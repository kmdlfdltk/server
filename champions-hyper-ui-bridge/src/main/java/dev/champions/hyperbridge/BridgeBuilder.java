package dev.champions.hyperbridge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.server.level.ServerPlayer;

/**
 * Actual HyperPokeBuilderUI View/Apply/Result wire protocol adapter.
 * Non-OP users may preview, but cannot apply free edits.
 * Prices/balance are intentionally diagnostic placeholders; economy NOT connected.
 */
public final class BridgeBuilder {
    private static final Stats[] STATS={
        Stats.HP, Stats.ATTACK, Stats.DEFENCE,
        Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };
    private static final Set<String> NATURES=Set.of(
        "hardy","lonely","brave","adamant","naughty","bold","docile","relaxed",
        "impish","lax","timid","hasty","serious","jolly","naive",
        "modest","mild","quiet","bashful","rash","calm","gentle",
        "sassy","careful","quirky"
    );
    private static final List<String> MODULES=List.of(
        "level","ivs","evs","nature","ability","gender","friendship","ball","size"
    );
    private BridgeBuilder(){}

    private static Pokemon selected(ServerPlayer player,int slot) {
        if(slot<0||slot>5)throw new IllegalArgumentException("Invalid party slot");
        Pokemon p=Cobblemon.INSTANCE.getStorage().getParty(player).get(slot);
        if(p==null)throw new IllegalArgumentException("Empty party slot");
        return p;
    }

    private static int gender(Pokemon p) {
        String g=p.getGender().name();
        if("MALE".equals(g))return 1;
        if("FEMALE".equals(g))return 2;
        return 0;
    }

    private static Object asMon(Pokemon p,int slot) {
        List<Integer> ivs=new ArrayList<>(6);
        List<Integer> evs=new ArrayList<>(6);
        for(Stats stat:STATS){
            ivs.add(p.getIvs().getOrDefault(stat));
            evs.add(p.getEvs().getOrDefault(stat));
        }
        String ability=p.getAbility().getName();
        List<Object> abilityList=List.of(
            HyperWire.builder("AbilityOpt",ability,false)
        );
        String nature=p.getNature().getName().getPath();
        // The original UI reads all of these fields to render existing Pokémon.
        return HyperWire.builder("Mon",
            slot,p.getUuid(),p.getSpecies().getName(),
            p.getSpecies().getTranslatedName().getString(),
            p.getLevel(),p.getShiny(),gender(p),false,
            p.getFriendship(),"poke_ball",
            nature,nature,ability,abilityList,
            ivs,evs,"",2
        );
    }

    public static boolean sendView(ServerPlayer player,boolean open) {
        List<Object> party=new ArrayList<>(6);
        for(int i=0;i<6;i++) {
            Pokemon p=Cobblemon.INSTANCE.getStorage().getParty(player).get(i);
            if(p!=null)party.add(asMon(p,i));
        }
        // User-requested UI sample. Only operators can edit; zero-cost display
        // is NOT CobbleDollars support. Keep costs zero to avoid fake payments.
        Object limits=HyperWire.builder("Limits",100,31,252,510,255);
        Object prices=HyperWire.builder("Prices",
            0L,0L,0L,0L,0L,0L,0L,0L,0L,
            List.of(0L,0L,0L,0L,0L),List.of());
        return HyperWire.sendBuilder(player,"View",
            3,open,0L,MODULES,limits,prices,party);
    }

    private static int at(List<Integer> args,int i) {
        if(i>=args.size())throw new IllegalArgumentException("Missing integer field");
        return args.get(i);
    }

    private static void requireRange(int value,int min,int max) {
        if(value<min||value>max)
            throw new IllegalArgumentException("Requested value out of range "+min+"..."+max);
    }

    @SuppressWarnings("unchecked")
    public static void handleApply(ServerPlayer player,Object packet) {
        int protocol=HyperWire.integer(packet,"getProtocol");
        int seq=HyperWire.integer(packet,"getSeq");
        int slot=HyperWire.integer(packet,"getSlot");
        Object uuidField=HyperWire.field(packet,"getUuid");
        int op=HyperWire.integer(packet,"getOp");
        List<Integer> ints=(List<Integer>)HyperWire.field(packet,"getInts");
        String text=HyperWire.string(packet,"getText");
        boolean success=false;
        String reason="champions.hyperbridge.rejected";

        try {
            if(protocol!=3)throw new IllegalArgumentException("Protocol mismatch");
            if(!player.hasPermissions(2))
                throw new IllegalStateException("OP 권한이 필요합니다 (현재는 무료 편집 테스트용)");
            if(BattleRegistry.getBattleByParticipatingPlayer(player)!=null)
                throw new IllegalStateException("배틀 중에는 수정할 수 없습니다");
            Pokemon pokemon=selected(player,slot);
            if(!pokemon.getUuid().equals(uuidField))
                throw new IllegalStateException("선택한 포켓몬이 변경되었습니다. 다시 열어주세요");
            if(pokemon.getEntity()!=null)
                throw new IllegalStateException("밖에 꺼낸 포켓몬을 먼저 회수해 주세요");
            switch(op){
                case 1 -> {
                    int value=at(ints,0);
                    requireRange(value,1,100);
                    // LEVEL_ADD generally sends a positive increment; don't allow
                    // arbitrarily large or negative additions from edited packets.
                    pokemon.setLevel(Math.min(100,pokemon.getLevel()+value));
                }
                case 2 -> pokemon.setLevel(1);
                case 3 -> {
                    if(ints.size()!=6)throw new IllegalArgumentException("Expected six IVs");
                    for(int value:ints)requireRange(value,0,31);
                    for(int i=0;i<6;i++)pokemon.setIV(STATS[i],ints.get(i));
                }
                case 4 -> {
                    if(ints.size()!=6)throw new IllegalArgumentException("Expected six EVs");
                    int sum=0;
                    for(int value:ints) {requireRange(value,0,252);sum+=value;}
                    if(sum>510)throw new IllegalArgumentException("EV total exceeds 510");
                    for(int i=0;i<6;i++)pokemon.setEV(STATS[i],ints.get(i));
                }
                case 5 -> {
                    String key=text.trim().toLowerCase(Locale.ROOT);
                    if(!NATURES.contains(key))
                        throw new IllegalArgumentException("Unknown nature");
                    Nature nature=Natures.getNature(key);
                    if(nature==null)throw new IllegalArgumentException("Unknown nature");
                    pokemon.setNature(nature);
                }
                case 9 -> {
                    int value=at(ints,0);
                    requireRange(value,0,255);
                    pokemon.setFriendship(value,false);
                }
                default -> throw new UnsupportedOperationException(
                    "해당 항목은 화면 테스트만 지원합니다 (특성/볼/성별/크기)");
            }
            success=true;
            reason="champions.hyperbridge.applied";
        } catch (Exception failure) {
            reason=failure.getMessage()==null?"champions.hyperbridge.rejected":
                failure.getMessage();
            HyperBridgeMod.LOG.warn("PokeBuilder operation {} rejected: {}",op,reason);
        }
        HyperWire.sendBuilder(player,"Result",3,seq,success,reason,List.of());
        sendView(player,false);
    }

    public static void fail(ServerPlayer player,Object packet,String reason) {
        try {
            int seq=HyperWire.integer(packet,"getSeq");
            HyperWire.sendBuilder(player,"Result",3,seq,false,
                reason==null?"champions.hyperbridge.rejected":reason,List.of());
        }catch(Exception e){
            HyperBridgeMod.LOG.error("Unable to send builder failure response",e);
        }
    }
}
