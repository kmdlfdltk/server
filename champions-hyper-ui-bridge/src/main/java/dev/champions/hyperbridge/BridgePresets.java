package dev.champions.hyperbridge;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Separate, fixed 10-slot UUID-based preset store. No Pokémon clones.
 * Deliberately does not overwrite prior Champions Party & Spawn presets.
 */
public final class BridgePresets {
    public static final int SLOTS=10;
    private static final int SYNC=0, SAVE=1, LOAD=2, RENAME=3, CLEAR=4;
    private static final int OK=0, BAD_NAME=10, EMPTY_PARTY=9,
        EMPTY_SLOT=12, NOT_FOUND=8, IN_BATTLE=4, PC_FULL=7, FAILED=13;

    private BridgePresets() {}

    private static Path path(ServerPlayer p) {
        return p.getServer().getWorldPath(LevelResource.ROOT)
            .resolve("champions_hyper_ui_presets")
            .resolve(p.getUUID()+".properties");
    }

    private static Properties read(ServerPlayer p) throws Exception {
        Properties props=new Properties();
        Path file=path(p);
        if (Files.isRegularFile(file))
            try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
                props.load(in);
            }
        return props;
    }

    private static void write(ServerPlayer p,Properties props) throws Exception {
        Path target=path(p), directory=target.getParent();
        Files.createDirectories(directory);
        Path tmp=Files.createTempFile(directory,"hyper-preset-",".tmp");
        try {
            try(Writer out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){
                props.store(out,"Champions Hyper Party Presets");
            }
            try {
                Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,
                           StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException ignored){
                Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    private static void checkSlot(int i) {
        if(i<0 || i>=SLOTS)throw new IllegalArgumentException("프리셋 슬롯은 0~9입니다");
    }

    private static String nameKey(int i){ return "slot."+i+".name"; }
    private static String idsKey(int i){ return "slot."+i+".ids"; }

    private static List<UUID> ids(Properties props,int i) {
        String encoded=props.getProperty(idsKey(i),"").trim();
        if(encoded.isBlank()) return List.of();
        List<UUID> out=new ArrayList<>();
        for(String value:encoded.split(","))out.add(UUID.fromString(value));
        if(out.size()>6 || new HashSet<>(out).size()!=out.size())
            throw new IllegalStateException("손상된 프리셋 UUID 목록");
        return List.copyOf(out);
    }

    private static String checkedName(String value) {
        String name=value==null?"":value.strip();
        if(name.isBlank() || name.length()>16
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("프리셋 이름은 1~16글자여야 합니다");
        return name;
    }

    private static void save(ServerPlayer p,int slot,String name) throws Exception {
        checkSlot(slot);
        if(BattleRegistry.getBattleByParticipatingPlayer(p)!=null)
            throw new IllegalStateException("배틀 중에는 저장할 수 없습니다");
        List<UUID> current=new ArrayList<>();
        for(Pokemon mon:Cobblemon.INSTANCE.getStorage().getParty(p)){
            if(mon.getEntity()!=null)
                throw new IllegalStateException("밖에 꺼낸 포켓몬을 회수해 주세요");
            current.add(mon.getUuid());
        }
        if(current.isEmpty())throw new IllegalStateException("저장할 파티가 없습니다");
        Properties props=read(p);
        String label=name==null || name.isBlank()
            ? props.getProperty(nameKey(slot),"프리셋 "+(slot+1))
            : checkedName(name);
        props.setProperty(nameKey(slot),checkedName(label));
        props.setProperty(idsKey(slot),
            String.join(",",current.stream().map(UUID::toString).toList()));
        write(p,props);
    }

    private static void rename(ServerPlayer p,int slot,String label) throws Exception {
        checkSlot(slot);
        Properties props=read(p);
        if(ids(props,slot).isEmpty())
            throw new IllegalStateException("저장된 프리셋이 없습니다");
        props.setProperty(nameKey(slot),checkedName(label));
        write(p,props);
    }

    private static void clear(ServerPlayer p,int slot) throws Exception {
        checkSlot(slot);
        Properties props=read(p);
        props.remove(nameKey(slot));
        props.remove(idsKey(slot));
        write(p,props);
    }

    private static void load(ServerPlayer p,int slot) throws Exception {
        checkSlot(slot);
        List<UUID> selected=ids(read(p),slot);
        if(selected.isEmpty())throw new IllegalStateException("빈 프리셋 슬롯입니다");
        new PartyTransfer(p.getServer()).replacePartyAtomically(p.getUUID(),selected);
    }

    /** Constructs exactly ten nullable Preset objects as original State expects. */
    private static List<Object> snapshots(ServerPlayer p) throws Exception {
        Properties props=read(p);
        Map<UUID,Pokemon> owned=new HashMap<>();
        for(Pokemon pokemon:Cobblemon.INSTANCE.getStorage().getParty(p))
            owned.put(pokemon.getUuid(),pokemon);
        for(Pokemon pokemon:Cobblemon.INSTANCE.getStorage().getPC(p))
            owned.put(pokemon.getUuid(),pokemon);

        List<Object> out=new ArrayList<>(SLOTS);
        for(int i=0;i<SLOTS;i++){
            List<UUID> saved=ids(props,i);
            if(saved.isEmpty()){out.add(null);continue;}
            List<Object> members=new ArrayList<>();
            for(UUID uuid:saved) {
                Pokemon pokemon=owned.get(uuid);
                if(pokemon==null) {
                    members.add(HyperWire.party("Member","missing",List.of(),
                        0,"",false));
                } else {
                    List<String> aspects=new ArrayList<>(pokemon.getForm().getAspects());
                    if(pokemon.getShiny())aspects.add("shiny");
                    members.add(HyperWire.party("Member",
                        pokemon.getSpecies().getName(),aspects,
                        pokemon.getLevel(),pokemon.getSpecies().getTranslatedName().getString(),true));
                }
            }
            out.add(HyperWire.party("Preset",
                props.getProperty(nameKey(i),"프리셋 "+(i+1)),members));
        }
        return out;
    }

    public static void sendState(ServerPlayer p,int code,int action,int slot) throws Exception {
        HyperWire.sendParty(p,"State",1,code,action,Math.max(0,Math.min(9,slot)),
            snapshots(p));
    }

    public static void fail(ServerPlayer player,Object payload,int errorCode,String diagnostic) {
        try {
            int action=HyperWire.integer(payload,"getAction");
            int slot=HyperWire.integer(payload,"getSlot");
            sendState(player,errorCode,action,slot);
            if(diagnostic!=null && !diagnostic.isBlank())
                HyperBridgeMod.LOG.debug("Preset action failure: {}",diagnostic);
        } catch(Exception e) {
            HyperBridgeMod.LOG.error("Could not send HyperPartyPreset failure response",e);
        }
    }

    public static void handle(ServerPlayer p,Object packet) {
        int protocol=HyperWire.integer(packet,"getProtocol");
        int action=HyperWire.integer(packet,"getAction");
        int slot=HyperWire.integer(packet,"getSlot");
        String name=HyperWire.string(packet,"getText");
        if(protocol!=1) {
            fail(p,packet,2,"Protocol mismatch");return;
        }
        int result=OK;
        try {
            switch(action){
                case SYNC -> {}
                case SAVE -> save(p,slot,name);
                case LOAD -> load(p,slot);
                case RENAME -> rename(p,slot,name);
                case CLEAR -> clear(p,slot);
                default -> throw new IllegalArgumentException("Invalid action "+action);
            }
        }catch(IllegalArgumentException e) {
            HyperBridgeMod.LOG.warn("Preset request invalid: {}",e.getMessage());
            result=BAD_NAME;
        }catch(Exception e) {
            HyperBridgeMod.LOG.warn("Preset request refused",e);
            String reason=e.getMessage()==null?"":e.getMessage();
            if(reason.contains("배틀"))result=IN_BATTLE;
            else if(reason.contains("공간 부족"))result=PC_FULL;
            else if(reason.contains("없습니다")||reason.contains("빈 프리셋"))result=NOT_FOUND;
            else if(reason.contains("파티가 없습니다"))result=EMPTY_PARTY;
            else result=FAILED;
        }
        try {sendState(p,result,action,slot);}
        catch(Exception error){throw new IllegalStateException("State send failed",error);}
    }
}
