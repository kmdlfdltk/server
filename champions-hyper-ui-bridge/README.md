# Champions Hyper UI Bridge — 0.1.0 Alpha

Standalone server-side **bridge** to open the **real original** HyperPartyPreset 0.1.29 and HyperPokeBuilderUI 1.0.42 screens. It uses the original protocol payload classes from the user's local mod files, loaded reflectively. It **does not recreate or redistribute their UI, classes, sprites or models**.

### Install

**Dedicated server mods**:
- Cobblemon 1.8.1 (Fabric 1.21.1), Fabric API, Fabric Language Kotlin
- `champions-hyper-ui-bridge-0.1.0-alpha.jar`
- `HyperPartyPresetNet-0.1.29.jar` (from the extracted server_protocol folder)
- `HyperPokeBuilderNet-1.0.42.jar` (from the extracted server_protocol folder)

**Client mods**:
- `HyperLib-1.2.81.jar`
- `HyperPartyPreset-0.1.29.jar` (already embeds its Net JAR)
- `HyperPokeBuilderUI-1.0.42.jar` (already embeds its Net JAR)
- Fabric API, Fabric Language Kotlin, Cobblemon 1.8.1

**Do not install the separate Net JARs twice in the client**. Do not install the original huge HyperClient bundle alongside these extracted UI mods. HyperNet/HyperClientAssets are not required for the default screen-opening commands.

### Test commands (new unique names avoid collisions with previous mods)

- `/하이퍼파티` or `/hyperparty`: sends original HyperPartyPreset Open 1 packet and current State.
- `/하이퍼빌더` or `/hyperbuilder`: sends original HyperPokeBuilder View 3 packet with party data.
- `/하이퍼브리지` or `/hyperbridge`: prints active server protocol integration status.

### What is bridged

- **Party**: Original UI displays 10 slots and real party/member 3D-model metadata. Save, rename, clear and load use the new bridge's own UUID-only slot file (under world/champions_hyper_ui_presets). **Existing Champions Party & Spawn mod preset data is not imported automatically**. Live Pokémon, never clones, are transferred between PC and party on the server thread. Battle, ownership, PC capacity and released Pokémon checks apply.
- **Builder**: Original UI opens with real owned Pokémon information, IV/EV/level/nature/friendship. Editing requests for level, IV, EV, nature, friendship are processed **only for players with operator permission 2 or above**, with input validation. Unimplemented operations (ability, gender, ball, size) return refusal; they never silently mutate data.
- **Economy**: No CobbleDollars integration. This is a development sample. Server-reported prices are zero and non-operators can view but not modify Pokémon.
- Your older mods may continue to run because commands are distinct, but **P/K/B key conflicts** are handled from Controls.

### Safety

Backup the world/PC save before testing. Test on a private copy first. Build success means successful Java compile and JAR artifact, **not actual game runtime testing**. If the GUI cannot open, capture `latest.log` and check both sides' mod lists.
