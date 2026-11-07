package dev.champions.partyspawn;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Validation logic only; the Cobblemon adapter must implement safe,
 * rollback-capable party/PC transfers on the server thread.
 */
public final class PartyPresetService {

    public interface CobblemonStoragePort {
        /** Returns the compact list of current party Pokemon UUIDs, up to six. */
        List<UUID> getCurrentParty(UUID player);

        /** Returns all Pokemon UUIDs currently owned in party or PC. */
        Set<UUID> getOwnedPokemon(UUID player);

        /** True if player is participating in a Cobblemon battle. */
        boolean isInBattle(UUID player);

        /**
         * Main-server-thread operation. Move existing Pokemon; never clone them.
         * Must commit all transfers or restore the original party/PC state.
         */
        void replacePartyAtomically(UUID player, List<UUID> desiredParty) throws Exception;
    }

    private final CobblemonStoragePort storage;

    public PartyPresetService(CobblemonStoragePort storage) {
        this.storage = Objects.requireNonNull(storage);
    }

    public List<UUID> captureCurrentParty(UUID player) {
        Objects.requireNonNull(player, "player");
        if (storage.isInBattle(player)) {
            throw new IllegalStateException("Cannot save party during a battle");
        }
        List<UUID> party = List.copyOf(storage.getCurrentParty(player));
        PartyPresetRepository.validateParty(party);
        return party;
    }

    public void applySavedPreset(UUID player, List<UUID> savedPokemon) throws Exception {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(savedPokemon, "savedPokemon");
        if (storage.isInBattle(player)) {
            throw new IllegalStateException("Cannot change party during a battle");
        }
        PartyPresetRepository.validateParty(savedPokemon);
        Set<UUID> owned = new HashSet<>(storage.getOwnedPokemon(player));
        if (!owned.containsAll(savedPokemon)) {
            throw new IllegalStateException(
                    "Preset refers to Pokemon the player no longer owns");
        }

        // Must run on the server thread. Adapter must re-check ownership,
        // battle/busy state and storage capacity under its own transaction lock.
        storage.replacePartyAtomically(player, List.copyOf(savedPokemon));
    }
}
