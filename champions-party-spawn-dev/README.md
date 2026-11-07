# Champions Party & Spawn — development source (not a playable mod)

Target: Minecraft 1.21.1 / Fabric / Cobblemon 1.8.1 / Java 21

## Status, 2026-11-08

This repository folder contains **original Java domain-logic source code only**. It is NOT a compiled or installable Fabric mod.

- UUID-only per-player preset persistence: written
- Preset ownership, battle-state, duplicate validation: written
- Cobblemon 1.8.1 transactional Party–PC adapter: NOT implemented
- HyperPartyPreset original UI and network protocol integration: NOT implemented
- K-key Hyper spawn viewer and spawn-pool adapter: NOT implemented
- Fabric entrypoints, build, in-game testing: NOT implemented

The model's local runtime returned repeated ClientError, so source JAR internals could not be re-verified. No third-party HyperNetwork code or images are redistributed here.

## Source files

- src/main/java/dev/champions/partyspawn/PartyPresetRepository.java
- src/main/java/dev/champions/partyspawn/PartyPresetService.java
- src/main/java/dev/champions/partyspawn/SpawnQueryService.java

These are Java 21 source components intended to be integrated later into a Fabric project. They are not command registrations, standalone Minecraft features, or graphical screens.

## Integration to-do

1. Verify exact Cobblemon 1.8.1 storage API and implement a rollback-safe party/PC transfer adapter on the server main thread.
2. Verify HyperPartyPreset screen opening method and packet IDs; obtain permission before reusing proprietary assets or code.
3. Verify the Cobblemon spawn pool API in the supplied JAR and create a server-side adapter equivalent in scope to /checkspawn.
4. Implement C2S/S2C Fabric networking and a client K-key handler.
5. Add Fabric mod metadata, Gradle build, run in a separate test profile and validate against save backups.

## Safety invariants

- Never spawn or duplicate Pokémon while loading a preset. Only move UUID-matched, owned instances.
- Prevent battle-time party swaps, duplicate UUIDs, missing Pokémon, and partial moves.
- Always derive current position/biome/dimension from the actual server player for spawn queries.
- Do not grant normal players operator permission just to support a screen.
- No build or runtime validation is claimed.
