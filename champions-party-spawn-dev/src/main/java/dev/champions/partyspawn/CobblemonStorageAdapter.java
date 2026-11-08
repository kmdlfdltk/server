package dev.champions.partyspawn;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Only call on the logical Minecraft server thread. No Pokemon objects are cloned. */
public final class CobblemonStorageAdapter implements PartyPresetService.CobblemonStoragePort {
    private final MinecraftServer server;

    public CobblemonStorageAdapter(MinecraftServer server) { this.server = server; }

    private ServerPlayer player(UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null) throw new IllegalStateException("Player offline");
        return p;
    }

    @Override public List<UUID> getCurrentParty(UUID id) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player(id));
        List<UUID> out = new ArrayList<>();
        for (Pokemon p : party) out.add(p.getUuid());
        return out;
    }

    @Override public Set<UUID> getOwnedPokemon(UUID id) {
        ServerPlayer p = player(id);
        Set<UUID> ids = new HashSet<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(p)) ids.add(pokemon.getUuid());
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(p)) ids.add(pokemon.getUuid());
        return ids;
    }

    @Override public boolean isInBattle(UUID id) {
        return BattleRegistry.getBattleByParticipatingPlayer(player(id)) != null;
    }

    @Override public void replacePartyAtomically(UUID id, List<UUID> wanted) throws Exception {
        if (!server.isSameThread()) throw new IllegalStateException("Must run on server thread");
        ServerPlayer owner = player(id);
        if (isInBattle(id)) throw new IllegalStateException("배틀 중 파티 교체 불가");
        if (wanted.isEmpty() || wanted.size() > 6 || new HashSet<>(wanted).size() != wanted.size())
            throw new IllegalArgumentException("Invalid preset");

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(owner);
        Pokemon[] originalParty = new Pokemon[6];
        Map<UUID, Pokemon> all = new HashMap<>();
        for (int slot=0;slot<6;slot++) {
            Pokemon poke = party.get(slot);
            originalParty[slot] = poke;
            if (poke != null) all.put(poke.getUuid(), poke);
        }

        Map<UUID, PCPosition> wantedFromPc = new HashMap<>();
        int freePcSlots = 0;
        for (int box=0;box<pc.getBoxes().size();box++) {
            for (int slot=0;slot<30;slot++) {
                PCPosition pos = new PCPosition(box,slot);
                if (!pc.isValidPosition(pos)) continue;
                Pokemon poke = pc.get(pos);
                if (poke == null) {freePcSlots++;continue;}
                if (all.putIfAbsent(poke.getUuid(),poke) != null) throw new IllegalStateException("Duplicate Pokemon UUID in stores");
                if (wanted.contains(poke.getUuid())) wantedFromPc.put(poke.getUuid(),pos);
            }
        }
        for (UUID uuid : wanted) if (!all.containsKey(uuid))
            throw new IllegalStateException("프리셋에 없는 포켓몬이 포함되어 있음");
        // Deny operations while a party Pokemon is out in the world, for safe state preservation.
        for (Pokemon pokemon : originalParty) if (pokemon != null && pokemon.getEntity() != null)
            throw new IllegalStateException("밖에 꺼낸 포켓몬을 먼저 회수해주세요");

        Set<UUID> selected = new HashSet<>(wanted);
        List<Pokemon> displaced = new ArrayList<>();
        for (Pokemon poke : originalParty) if (poke != null && !selected.contains(poke.getUuid())) displaced.add(poke);
        if (freePcSlots + wantedFromPc.size() < displaced.size())
            throw new IllegalStateException("PC 보관함 공간 부족");

        // Validate all targets and positions before altering any slot.
        for (UUID uuid : wantedFromPc.keySet())
            if (pc.get(wantedFromPc.get(uuid)) != all.get(uuid))
                throw new IllegalStateException("PC changed during load");

        try {
            for (Pokemon poke : originalParty) if (poke != null && !party.remove(poke))
                throw new IllegalStateException("Could not remove Pokemon from party");
            for (UUID uuid : wantedFromPc.keySet()) if (!pc.remove(all.get(uuid)))
                throw new IllegalStateException("Could not remove Pokemon from PC");
            for (int i=0; i<wanted.size(); i++) party.set(i, all.get(wanted.get(i)));
            for (Pokemon poke : displaced) if (!pc.add(poke))
                throw new IllegalStateException("Could not transfer Pokemon to PC");
        } catch (Exception failure) {
            // Restore original slots and exact PC positions; no copies of Pokemon are made.
            try {
                List<Pokemon> current = new ArrayList<>();
                for (Pokemon poke : party) current.add(poke);
                for (Pokemon poke : current) party.remove(poke);
                for (Pokemon poke : displaced) pc.remove(poke);
                for (var item : wantedFromPc.entrySet()) pc.remove(all.get(item.getKey()));
                for (int i=0;i<originalParty.length;i++) if (originalParty[i]!=null) party.set(i,originalParty[i]);
                for (var entry : wantedFromPc.entrySet()) pc.set(entry.getValue(), all.get(entry.getKey()));
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                ChampionsMod.LOGGER.error("CRITICAL: party rollback failure for {}", id, rollbackFailure);
            }
            throw failure;
        }
    }
}
