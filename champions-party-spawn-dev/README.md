# Champions Party & Spawn (Fabric 1.21.1 / Cobblemon 1.8.1)

A development-alpha mod created for the user's Cobblemon Champions server.

**Functions implemented in source**
- `/파티`, `/프리셋`, or P key: named party presets with an interactive GUI; up to 10 UUID-only saves.
- Save/load/delete, Cobblemon party/PC ownership and battle checks, no Pokemon cloning, server-thread transfer with rollback attempt on failures.
- K key: bucket-based local spawn viewer. A server-authoritative handler collects the actual `/checkspawn <bucket>` command's feedback without granting players OP permission. The K GUI supports common/uncommon/rare/ultra-rare.
- Fabric S2C/C2S packet registration, server-side world+biome lookup, query cooldown, localization.

**Important: independent original GUI, not the HyperNetwork proprietary GUI.** The original HyperPartyPreset client screen methods, server packet IDs, and copyrights have not been verified, so this alpha supplies its own functional GUI instead. No proprietary code or art are redistributed.

## Build

- Java 21, Gradle 8.14.3, Minecraft 1.21.1, Fabric API, Cobblemon 1.8.1.
- Run `gradle build` in this directory.
- The GitHub Actions build workflow at `.github/workflows/champions-party-spawn-build.yml` also produces a downloadable JAR artifact after a successful run.
- Use only the remapped output JAR in server **and** client `mods`.

## Testing

1. Back up the world and Cobblemon storage files first.
2. Test on a separate Fabric 1.21.1 / Cobblemon 1.8.1 server.
3. Join as a non-operator and press K, compare with a privileged player's `/checkspawn common`.
4. Save a six-Pokemon preset, change party using PC, then load. Confirm the **same** UUIDs, IVs, items and correct original PC slots.
5. Test during a battle, while Pokemon are sent out, PC near full, invalid preset, disconnection/rejoin.
6. Don't put this alpha on a public production server before both build and gameplay verification.

## Source

- `PartyPresetRepository.java`: persistence
- `PartyPresetService.java`: validation
- `CobblemonStorageAdapter.java`: party ↔ PC transfer and rollback
- `ChampionsPackets.java`: Fabric protocol
- `ChampionsMod.java`: commands, request handlers and controlled Cobblemon checkspawn
- `ChampionsClient.java`: keyboard controls and UI
- `SpawnQueryService.java`: optional domain boundary for future direct-spawn-pool API integration

**Known limits:** Real spawn percentages are conditional *within* the selected rarity bucket, not an unconditional probability. Capturing diagnostic chat text is an interim method; directly using Cobblemon's spawn-pool API would be an improvement. HyperNetwork exact UI remains unimplemented. Build/in-game results must be checked separately.
