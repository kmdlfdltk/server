# Champions Hyper UI Bridge v0.4.0-alpha

Minecraft 1.21.1 Fabric / Cobblemon 1.8.1.

## What's changed in v0.4.0

- **PokeBuilder editing tabs limited to IV, EV, Nature, Ability**. Level, gender, friendship, ball and size modules are removed from the server-provided UI module list.
- The bridge also **rejects all edits** outside operations 3 (IV), 4 (EV), 5 (Nature), 6 (Ability), including requests from old or modified clients.
- The actual Pokémon gender value (male/female/genderless) is retained for informational display; gender editing is disabled.
- The original source UI clips a six-Pokémon party on some resolutions. Install the new *client-only* `champions-hyper-scroll-client-0.1.0-alpha.jar`: while hovering over the original PokeBuilder's left party panel, scroll down to request a View with slots 2–6 and up to return to 1–5.
- **This is server-driven paging within the original UI, not a modification of HyperNetwork's proprietary screen class.** It preserves the design but is not yet proven in-game; switching pages may reset the selected Pokemon.
- Original HyperPartyPreset, its saves, CobbleDollars fees for retained modules, Korean text and Pokémon model packets remain unchanged.

## Installation

Server: replace the previous `champions-hyper-ui-bridge-0.3.0-alpha.jar` with `champions-hyper-ui-bridge-0.4.0-alpha.jar`. Keep original HyperPartyPresetNet and HyperPokeBuilderNet and all other dependencies.

Client: keep the original UI JARs from v0.2 unchanged, and **add** `champions-hyper-scroll-client-0.1.0-alpha.jar`. Do **not** put the scroll addon on the dedicated server. A client that lacks the addon can still open the original UI, but on a full party will see only the first five Pokémon in the builder.

### Testing

1. Back up the Cobblemon world and Pokémon storage.
2. Check `/하이퍼파티` still works unchanged.
3. Open `/하이퍼빌더`. Confirm the only editable modules are IV, EV, Nature, Ability.
4. With exactly six Pokémon, place the mouse over the **left party column** and wheel down; the party data should switch to slots 2–6, including the sixth Pokémon. Wheel up returns to slots 1–5. The refresh must preserve real party slot IDs, so a change to the sixth should edit the sixth Pokémon.
5. If the client screen does not refresh on a `View(open=false)` packet, or scroll input is intercepted by another mod, send both server/client `latest.log`; original client GUI binary-level patching would then be necessary.

---

Target: Minecraft 1.21.1 / Fabric / Cobblemon 1.8.1 / Java 21.

This standalone **server** bridge uses the actual HyperPartyPreset 0.1.29 and HyperPokeBuilderUI 1.0.42 client UIs. All proprietary client JARs/assets originate from the user's own supplied mod bundle; this GitHub repository contains only original bridge source.

## v0.2.0 changes

- Party preset: send full resource species identifier (e.g. `cobblemon:charmander`) for correct genuine Cobblemon 3D portrait rendering, and Pokémon's real aspects.
- Party names: send custom nickname only when present, allowing original client to translate Cobblemon species name into Korean; keep player-defined nicknames.
- Builder: send real species/form candidates including hidden abilities; safely apply only a form/species-valid selected ability.
- CobbleDollars: optional official wallet balance/withdraw/refund via discovered Kotlin property extensions; fail-closed if unavailable, price matching and preflight validation on the server thread.
- JSON pricing config auto-created at `config/champions-hyper-ui-bridge.json`; change costs while server is stopped, then restart.
- Separate client-only patched HyperLib header shows COBBLEMON CHAMPIONS; builder Korean locale says 코블달러 instead of 토큰. Those modified client assets are **not hosted in this repository**.

## Commands

`/하이퍼파티` / `/hyperparty`: open original preset.
`/하이퍼빌더` / `/hyperbuilder`: open original builder.
`/하이퍼경제`: wallet detection and balance diagnostic.
`/하이퍼브리지`: original packet integration diagnostic.

## Dependencies and installation

**Server**: Fabric API, Fabric Language Kotlin, Cobblemon 1.8.1, `HyperPartyPresetNet-0.1.29.jar`, `HyperPokeBuilderNet-1.0.42.jar`, `champions-hyper-ui-bridge-0.2.0-alpha.jar`. CobbleDollars 1.21.1 is needed for paid editing.

**Client**: Cobblemon, Fabric API, Fabric Language Kotlin, HyperPartyPreset 0.1.29, HyperPokeBuilderUI 1.0.42 (client wording patch), HyperLib 1.2.81 (branding patch). Do not install the complete HyperClient bundle alongside the separated modules or duplicate them.

## Price defaults

```json
{
  "enableCobbleDollars": true,
  "operatorBypassPayment": false,
  "allowFreeEditsIfProviderMissing": false,
  "levelPerPoint": 50,
  "levelReset": 2000,
  "ivPerPoint": 100,
  "evPerPoint": 20,
  "nature": 1500,
  "ability": 2000,
  "hiddenAbility": 5000,
  "friendshipPerPoint": 10,
  "allowAbilityEditing": true
}
```

Actual balance and fees are always checked server-side. No money is charged when an operation is refused; on a mutation failure after a charge, the original balance is restored if the wallet API allows it. If the displayed quote differs from the server calculation, the action is rejected. Unimplemented gender, ball, and size editing remain unavailable.

## Testing cautions

Build passes in CI but gameplay, real CobbleDollars 2.x API discovery, client 3D portraits, and charge/refund all require verification in a backed-up test server. Keep the existing `champions_hyper_ui_presets` world folder; updating the bridge does not wipe it. If it fails, collect client/server `latest.log`, the `/하이퍼경제` output and the on-screen error. Do not enable free editing on a public server before validating all economy operations.
