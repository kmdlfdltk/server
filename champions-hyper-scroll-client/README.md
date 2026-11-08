# Champions Hyper PokeBuilder Party Scroll (Fabric 1.21.1)

Client-side mouse-wheel addon paired with server Champions Hyper UI Bridge >=0.4.0.

**How:** Hyper PokeBuilder's original left party list visibly clips the sixth member. Without copying the proprietary UI classes, this addon intercepts mouse-wheel events while the pointer is over the party panel. The server bridge sends the original Hyper UI View packet containing slots 1–5 or 2–6 while keeping actual server party slot numbers unchanged. On screen open, the first group is shown.

This is a **five-row paging workaround** rather than modifications to original screen render internals: it preserves its exact design and may reset selected Pokémon when the client refreshes its View. Runtime gameplay behavior requires testing.

Install only on client with original HyperPokeBuilderUI/HyperLib, Minecraft 1.21.1 Fabric API. Do not add this client mod to the dedicated server. Install the matching new server bridge on the server.

Wheel down over the left party list to see the sixth Pokémon. Wheel up to return to the first. If the client mod is not connected, no custom network packet is sent.
