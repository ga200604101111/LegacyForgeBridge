# Roadmap and Acceptance Targets

LegacyForgeBridge is developed in measurable compatibility stages. A version number does not mean universal 1.7.10 Forge compatibility; it means the acceptance criteria for that stage have been met.

## Definition of completion

Project completion is tracked in four independent dimensions:

1. **Transport / session compatibility** — can a 1.21.11 Fabric client communicate correctly with a 1.7.10 server?
2. **Forge/FML compatibility** — can the client complete Forge 1.7.10 discovery, handshake, channel, registry, and mod-list behavior?
3. **Legacy mod conversion coverage** — how much common 1.7.10 Forge API usage can be converted automatically?
4. **Behavior fidelity** — after conversion, how closely does gameplay match the original observable behavior?

A global percentage is only a summary. Each subsystem must also expose its own score.

## Milestones

### v0.1 — Foundation — target: 10%

Status target:

- [x] Fabric 1.21.11 / Java 21 project builds in CI.
- [x] `old-mods` discovery.
- [x] SHA-256 source cache foundation.
- [x] ASM class/reference analysis.
- [x] compatibility reports.
- [x] FML handshake codec/state-machine foundation.
- [ ] real legacy-session activation.
- [ ] real ViaFabricPlus/ViaVersion integration.

Acceptance rule: the mod must start safely and must never emit converted JARs that were not validated by the conversion pipeline.

### v0.2 — Clean Forge 1.7.10 Server — target: 20%

Goal: a Fabric 1.21.11 client can join and play on a Forge 1.7.10 server with no third-party mods installed.

Required:

- protocol target automatically switches to 1.7.10 for a legacy connection;
- Forge/FML server detection;
- FML marker/channel registration;
- ClientHello / ModList / HandshakeAck flow;
- clean connection reaches PLAY state;
- movement, chat, inventory, block interaction, death/respawn and world changes remain stable;
- disconnect restores normal 1.21.11 behavior completely;
- Legacy Session Profile is enabled only for the active legacy connection.

Acceptance target: **100 successful connect/play/disconnect cycles without state leaking into a normal 1.21.11 session.**

### v0.3 — Version Virtualization — target: 30%

Goal: the modern client behaves like a valid 1.7.10-era client while connected to a legacy server without globally downgrading Minecraft.

Required:

- modern-only blocks/items/entities are hidden from legacy-facing UI surfaces where they could be selected or sent;
- outbound packet guard rejects unsupported modern registry content;
- modern-only recipes, commands, suggestions and interaction affordances are filtered where relevant;
- modern registries remain intact internally so normal 1.21.11 worlds/servers still work after disconnect;
- unsupported received legacy/modded content has a defined placeholder/failure policy;
- per-session capability table is available to all bridge subsystems.

Acceptance target: **no known path can intentionally send a 1.21.11-only block/item/entity identifier to a 1.7.10 server.**

### v0.4 — Basic Legacy Mod Conversion — target: 45%

Goal: simple content mods can be placed in `old-mods`, converted at startup, written to `mods`, and loaded on the next launch.

Initial supported classes:

- simple Item;
- simple Block;
- food;
- tools;
- swords/weapons using ordinary item behavior;
- armor;
- basic recipes;
- language/resources;
- CreativeTab intent translated to a modern equivalent;
- basic GameRegistry calls;
- basic Forge event subscription.

Required pipeline:

`old-mods/*.jar -> analyze -> map -> transform -> validate -> output converted JAR -> cache result`

Acceptance target: **at least 10 representative small Forge 1.7.10 mods with >= 90% automatic conversion and no manual source edits.**

### v0.5 — Common Forge API Layer — target: 60%

Add:

- OreDictionary semantics;
- TileEntity/BlockEntity basics;
- inventories;
- containers/screens;
- simple packets;
- Entity registration;
- common world generation;
- common configuration patterns;
- common NBT transformations;
- common player/world Forge events.

Acceptance target: **at least 30 representative mods; >= 75% start successfully; >= 60% reach their primary gameplay loop without manual source edits.**

### v0.6 — Semantic Transformation Layer — target: 72%

Add transformations that cannot be solved by renaming alone:

- field -> getter/setter conversion;
- changed method descriptors;
- metadata -> modern state/property mapping where possible;
- DataWatcher/data-tracker adaptation;
- old inventory/NBT idioms;
- side/thread semantic changes;
- lifecycle changes;
- legacy packet intent -> modern networking adapter.

Acceptance target: unsupported patterns are classified deterministically instead of failing later with generic JVM linkage errors.

### v0.7 — Rendering and Client Behavior — target: 82%

Add:

- common legacy entity/block rendering intent;
- HUD/overlay conversion;
- common Tessellator usage;
- direct GL11 patterns translated to modern rendering abstractions where recognizable;
- keybind/input migration;
- particles and sounds;
- client-only Forge event equivalents.

Direct OpenGL compatibility is **not** a goal. Observable rendering intent is the goal.

### v0.8 — ASM/CoreMod Intent Migration — target: 90%

Goal: support common historical hacks without preserving the old transformer runtime.

Policy:

1. detect legacy ASM/CoreMod transformer;
2. identify known transformation intent;
3. map to a modern Fabric/vanilla event if available;
4. otherwise route through a shared Mixin hook;
5. refuse transformations whose intent cannot be proven safely.

Acceptance target: a catalog of known transformer patterns with explicit supported/unsupported status and regression tests.

### v0.9 — Large-Mod Stabilization — target: 95%

Focus on representative large content/technology/magic mods and dependency graphs.

Required:

- dependency ordering;
- namespace/registry collision handling;
- cross-mod API adapters;
- deterministic conversion manifests;
- conversion rollback;
- richer diagnostics;
- performance and memory profiling.

The 95% target refers to the **defined compatibility corpus**, not every 1.7.10 mod ever released.

### v1.0 — Stable Compatibility Platform

Release criteria:

- clean Forge 1.7.10 server interoperability is stable;
- Legacy Session Profile is complete and reversible;
- converted JAR cache/output lifecycle is stable;
- a documented compatibility corpus reaches the declared target thresholds;
- unsupported CoreMods fail explicitly before gameplay;
- normal 1.21.11 Fabric use is unaffected when no legacy connection/conversion is active;
- every compatibility rule has diagnostics and a stable rule ID;
- automated CI regression tests cover transport, conversion, and compatibility rules.

## What 100% does NOT mean

LegacyForgeBridge will not promise that every arbitrary 1.7.10 CoreMod, renderer, native library, reflection hack, or transformer can be converted automatically.

`100%` inside a subsystem means **100% of that subsystem's documented compatibility corpus and acceptance cases pass**.

## Compatibility corpus

The test corpus should eventually contain categories rather than cherry-picked mods:

- pure content mods;
- block/item mods;
- inventory/GUI mods;
- entity mods;
- world-generation mods;
- networking mods;
- rendering-heavy mods;
- API/dependency mods;
- ASM/CoreMods;
- large integrated mods.

Every release must record:

- number of tested mods;
- start success rate;
- primary-gameplay success rate;
- automatic-conversion rate;
- manual-rule requirement rate;
- unsupported reason distribution.
