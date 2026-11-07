package dev.champions.partyspawn;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Safe server-authoritative spawn query boundary.
 * Cobblemon 1.8.1 adapter and HyperNetwork client protocol are intentionally
 * NOT implemented until the supplied JAR APIs can be inspected.
 */
public final class SpawnQueryService {
    public record SpawnEntry(String speciesId, String formId,
                             String bucket, double relativeWeight,
                             int minLevel, int maxLevel) {
        public SpawnEntry {
            Objects.requireNonNull(speciesId, "speciesId");
            Objects.requireNonNull(bucket, "bucket");
            if (!Double.isFinite(relativeWeight) || relativeWeight < 0) {
                throw new IllegalArgumentException("Invalid relative weight");
            }
            if (minLevel < 1 || maxLevel < minLevel) {
                throw new IllegalArgumentException("Invalid level range");
            }
        }
    }

    public record SpawnResult(String dimensionId, String biomeId,
                              long serverGameTime, List<SpawnEntry> entries) {
        public SpawnResult {
            Objects.requireNonNull(dimensionId);
            Objects.requireNonNull(biomeId);
            entries = List.copyOf(entries);
        }
    }

    public interface ServerSpawnPort {
        /** Must derive location/world/biome from the *server* player. */
        SpawnResult queryAtPlayer(UUID playerId);
    }

    private final ServerSpawnPort spawnPort;

    public SpawnQueryService(ServerSpawnPort spawnPort) {
        this.spawnPort = Objects.requireNonNull(spawnPort);
    }

    public SpawnResult query(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        // Network handler must rate-limit requests, verify session ownership,
        // and schedule API access onto the Minecraft server thread.
        return Objects.requireNonNull(spawnPort.queryAtPlayer(playerId));
    }
}
