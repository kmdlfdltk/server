package dev.champions.hyperbridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

/**
 * Original HyperPokeBuilderUI protocol v3 with real owned Cobblemon data.
 * Prices and withdrawals are server authoritative; CobbleDollars is optional
 * but fail-closed when not available.
 */
public final class BridgeBuilder {
    private static final Stats[] STATS={
        Stats.HP,Stats.ATTACK,Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED
    };
    private static final Set<String> NATURES=Set.of(
        "hardy","lonely","brave","adamant","naughty","bold","docile","relaxed",
        "impish","lax","timid","hasty","serious","jolly","naive",
        "modest","mild","quiet","bashful","rash","calm","gentle",
        "sassy","careful","quirky"
    );
    private static final List<String> MODULES=List.of(
        "ivs","evs","nature","ability"
    );
    // The source Hyper GUI fits five party rows. Mouse-wheel input switches
    // between first five and last five; slot IDs remain actual server slots.
    private static final Map<UUID, Integer> PARTY_SCROLL = new ConcurrentHashMap<>();
    private static final int VISIBLE_PARTY_ROWS = 5;
    private BridgeBuilder(){}

    public static void forget(ServerPlayer p) {
        PARTY_SCROLL.remove(p.getUUID());
    }

    public static void scroll(ServerPlayer p, int direction) {
        if (direction < -1 || direction > 1 || direction == 0) return;
        if (PARTY_SCROLL.get(p.getUUID()) == null) return;
        int count=0;
        for(int i=0; i<6; i++)
            if (Cobblemon.INSTANCE.getStorage().getParty(p).get(i)!=null) count++;
        if (count <= VISIBLE_PARTY_ROWS) return;
        int previous=PARTY_SCROLL.getOrDefault(p.getUUID(),0);
        int next=Math.max(0,Math.min(count-VISIBLE_PARTY_ROWS,previous+direction));
        if(previous!=next) {
            PARTY_SCROLL.put(p.getUUID(),next);
            sendView(p,false);
        }
    }

    private static Pokemon selected(ServerPlayer p,int slot) {
        if(slot<0||slot>5)throw new IllegalArgumentException("Invalid party slot");
        Pokemon pokemon=Cobblemon.INSTANCE.getStorage().getParty(p).get(slot);
        if(pokemon==null)throw new IllegalArgumentException("Empty party slot");
        return pokemon;
    }

    private static int gender(Pokemon p) {
        String g=p.getGender().name();
        if("MALE".equals(g))return 1;
        if("FEMALE".equals(g))return 2;
        return 0;
    }

    private static Object asMon(Pokemon p,int slot) {
        List<Integer> ivs=new ArrayList<>(6), evs=new ArrayList<>(6);
        for(Stats stat:STATS) {
            ivs.add(p.getIvs().getOrDefault(stat));
            evs.add(p.getEvs().getOrDefault(stat));
        }

        List<Object> abilities=new ArrayList<>();
        for(AbilityChoices.Choice available:AbilityChoices.forPokemon(p)) {
            abilities.add(HyperWire.builder("AbilityOpt",
                available.id(),available.hidden()));
        }
        String selectedAbility=p.getAbility().getName();
        if(abilities.isEmpty()) {
            // Exceptional event species can have a fixed, custom-only ability.
            abilities.add(HyperWire.builder("AbilityOpt",selectedAbility,false));
        }
        String nature=p.getNature().getName().getPath();
        return HyperWire.builder("Mon",
            slot,
            p.getUuid(),
            p.getSpecies().getResourceIdentifier().toString(),
            SpeciesNames.of(p),
            p.getLevel(),p.getShiny(),gender(p),false,
            p.getFriendship(),BallChoices.idOf(p.getCaughtBall()),
            nature,nature,selectedAbility,abilities,
            ivs,evs,"",2
        );
    }

    public static boolean sendView(ServerPlayer player,boolean open) {
        if(open) PARTY_SCROLL.put(player.getUUID(),0);
        List<Object> allParty=new ArrayList<>(6);
        for(int i=0;i<6;i++) {
            Pokemon p=Cobblemon.INSTANCE.getStorage().getParty(player).get(i);
            if(p!=null)allParty.add(asMon(p,i));
        }
        int maxOffset=Math.max(0,allParty.size()-VISIBLE_PARTY_ROWS);
        int offset=Math.min(maxOffset,PARTY_SCROLL.getOrDefault(player.getUUID(),0));
        PARTY_SCROLL.put(player.getUUID(),offset);
        List<Object> party=allParty.size()>VISIBLE_PARTY_ROWS
            ?new ArrayList<>(allParty.subList(offset,offset+VISIBLE_PARTY_ROWS))
            :allParty;
        BridgeEconomyConfig c=BridgeEconomyConfig.INSTANCE;
        Object limits=HyperWire.builder("Limits",100,31,252,510,255);
        Object prices=HyperWire.builder("Prices",
            c.levelPerPoint,c.levelReset,c.ivPerPoint,c.evPerPoint,c.nature,
            c.ability,c.hiddenAbility,0L,c.friendshipPerPoint,
            List.of(0L,0L,0L,0L,0L),List.of()
        );
        // If no real wallet provider exists, -1 signals unavailable balance.
        long balance=CobbleDollarsWallet.balanceForView(player);
        return HyperWire.sendBuilder(player,"View",
            3,open,balance,MODULES,limits,prices,party);
    }

    private static void requireRange(int value,int min,int max) {
        if(value<min||value>max)
            throw new IllegalArgumentException("Requested value outside "+min+"..."+max);
    }

    private static int at(List<Integer> args,int idx) {
        if(args==null||idx>=args.size())
            throw new IllegalArgumentException("Missing numeric argument");
        return args.get(idx);
    }

    private static void mutate(Pokemon pokemon,int op,List<Integer> ints,
                               String text) {
        switch(op) {
            case 1 -> {
                int increase=at(ints,0);
                requireRange(increase,1,100-pokemon.getLevel());
                pokemon.setLevel(pokemon.getLevel()+increase);
            }
            case 2 -> pokemon.setLevel(1);
            case 3 -> {
                if(ints==null||ints.size()!=6)
                    throw new IllegalArgumentException("Expected six IVs");
                for(int value:ints)requireRange(value,0,31);
                for(int i=0;i<6;i++)pokemon.setIV(STATS[i],ints.get(i));
            }
            case 4 -> {
                if(ints==null||ints.size()!=6)
                    throw new IllegalArgumentException("Expected six EVs");
                int sum=0;
                for(int value:ints) { requireRange(value,0,252); sum+=value; }
                if(sum>510)throw new IllegalArgumentException("EV sum exceeds 510");
                for(int i=0;i<6;i++)pokemon.setEV(STATS[i],ints.get(i));
            }
            case 5 -> {
                String key=NatureKeys.require(text);
                Nature nature=Natures.getNature(key);
                if(nature==null)throw new IllegalArgumentException("Unknown nature");
                pokemon.setNature(nature);
                // Clear Mint modifiers so the displayed effective nature really changes.
                pokemon.setMintedNature(null);
                if (!pokemon.getEffectiveNature().getName().getPath().equals(key))
                    throw new IllegalStateException("성격 변경이 적용되지 않았습니다");
            }
            case 6 -> {
                // Includes hidden abilities only when the species/form actually permits them.
                var target=AbilityChoices.require(pokemon,text);
                AbilityChoices.set(pokemon,target);
            }
            case 8 -> {
                var nextBall=BallChoices.require(text);
                pokemon.setCaughtBall(nextBall);
                if(!pokemon.getCaughtBall().equals(nextBall))
                    throw new IllegalStateException("볼 변경이 적용되지 않았습니다");
            }
            case 9 -> {
                int value=at(ints,0);
                requireRange(value,0,255);
                pokemon.setFriendship(value,false);
            }
            default -> throw new UnsupportedOperationException("This module is not enabled");
        }
    }

    @SuppressWarnings("unchecked")
    public static void handleApply(ServerPlayer player,Object packet) {
        int protocol=HyperWire.integer(packet,"getProtocol");
        int seq=HyperWire.integer(packet,"getSeq");
        int slot=HyperWire.integer(packet,"getSlot");
        Object uuidField=HyperWire.field(packet,"getUuid");
        int op=HyperWire.integer(packet,"getOp");
        List<Integer> ints=(List<Integer>)HyperWire.field(packet,"getInts");
        String input=HyperWire.string(packet,"getText");
        HyperBridgeMod.LOG.debug("PokeBuilder Apply: player={}, slot={}, op={}, text={}, ints={}",
            player.getUUID(),slot,op,input,ints);
        long uiQuoted=HyperWire.number(packet,"getQuoted");
        boolean success=false;
        String reason="champions.bridge.rejected";

        try {
            if(protocol!=3)throw new IllegalArgumentException("Protocol mismatch");
            // Disabled tabs are blocked server-side too, even if an outdated
            // client sends a forged/old level, gender, friendship, ball, or size edit.
            if (op != 3 && op != 4 && op != 5 && op != 6)
                throw new UnsupportedOperationException(
                    "이 서버에서는 개체값·노력치·성격·특성만 변경할 수 있습니다.");
            if(BattleRegistry.getBattleByParticipatingPlayer(player)!=null)
                throw new IllegalStateException("배틀 중 수정할 수 없습니다");
            Pokemon pokemon=selected(player,slot);
            if(!pokemon.getUuid().equals(uuidField))
                throw new IllegalStateException("파티가 변경되었습니다. 다시 열어 주세요");
            if(pokemon.getEntity()!=null)
                throw new IllegalStateException("꺼낸 포켓몬을 먼저 회수해 주세요");

            // Prevalidate the complete operation before charging.
            long charge=BridgePrices.quote(pokemon,op,ints,input);
            var config=BridgeEconomyConfig.INSTANCE;
            boolean bypass=config.operatorBypassPayment && player.hasPermissions(2);
            if(!bypass && !CobbleDollarsWallet.available()) {
                if(!config.allowFreeEditsIfProviderMissing)
                    throw new IllegalStateException(
                        "CobbleDollars 연결 없음: "+CobbleDollarsWallet.status());
                charge=0;
            }

            if(!bypass && charge>0 && uiQuoted!=charge) {
                throw new IllegalStateException(
                    "가격 정보가 갱신되었습니다. 화면을 다시 열어 주세요 (표시 "
                    +uiQuoted+" / 서버 "+charge+")");
            }

            final int finalOp=op;
            final Pokemon finalPokemon=pokemon;
            Runnable mutation=()->mutate(finalPokemon,finalOp,ints,input);
            if(bypass || charge==0){
                mutation.run();
            } else {
                CobbleDollarsWallet.payAndApply(player,charge,mutation);
            }
            success=true;
            reason="champions.bridge.applied";
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[포켓빌더] 변경 성공 (항목 "+op+")"));
        }catch(Exception problem) {
            reason=problem.getMessage()==null?"champions.bridge.rejected":
                problem.getMessage();
            HyperBridgeMod.LOG.warn("PokeBuilder operation {} rejected: {}",op,reason);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[포켓빌더] 변경 실패 ("+op+"): "+reason));
        }

        HyperWire.sendBuilder(player,"Result",3,seq,success,reason,List.of());
        sendView(player,false);
    }

    public static void fail(ServerPlayer player,Object packet,String reason) {
        try {
            int seq=HyperWire.integer(packet,"getSeq");
            HyperWire.sendBuilder(player,"Result",3,seq,false,
                reason==null?"champions.bridge.rejected":reason,List.of());
        }catch(Exception e){
            HyperBridgeMod.LOG.error("Unable to send builder failure",e);
        }
    }
}
