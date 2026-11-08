# Champions PokeBuilder Sample

Minecraft 1.21.1 / Fabric / Cobblemon 1.8.1 / Java 21.

**Standalone sample** with original tablet-style UI inspired by the provided HyperNetwork screenshot. No original HyperNetwork executable code or All-Rights-Reserved assets are distributed. It can coexist with the earlier Champions Party & Spawn mod.

Features:
- `/포켓빌더`, `/pokebuilder`, or `B`: open the editing screen.
- Displays the player's actual 6 Cobblemon party Pokémon.
- Genuine Cobblemon 3D model previews, localized name, level, nature, IV/EV totals.
- Five interactive tabs: Level (1-100), IVs (0-31), EVs (per-stat 0-252 and combined 0-510), Nature (25 choices), Friendship (0-255).
- Server-side validation, owner-only (party-slot), no editing while battling or while Pokemon is out.
- **Admin-only sample by default**: `requiresPermissionLevel: 2`. No currency integration or normal-player free stat editing. Config is `config/champions-pokebuilder.json`. Only disable the restriction in a private test world, never on a public server without economy integration.
- Changes apply to existing Pokemon objects, not clones.

Installation: build with Java 21 and Gradle 8.14.3 (`gradle build`). Install remapped `champions-pokebuilder-0.1.0-alpha.jar` on **both server and client** along with Fabric API, Fabric Language Kotlin, and Cobblemon 1.8.1. **Do not** install the `-dev.jar`.

Test with backup saves. Actual game launch and data persistence across sessions are not verified solely by a CI compile.
