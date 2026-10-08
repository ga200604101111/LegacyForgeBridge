# Twilight Forest 1.7.10 (2.3.8) — completeness and live acceptance ledger

Scope: a Fabric **1.21.11** client that interprets/mod-converts original Forge **1.7.10** client content and connects to the **unchanged** original Forge 1.7.10 server. The exact target corpus used in prior source investigation is `twilightforest-1.7.10-2.3.8-tw.jar`. The server, source mod JAR and other original mod JARs are never rewritten.

This is an acceptance ledger, **not** a fictitious global compatibility percentage. A source analyzer finding a registration or rendering pattern is not equivalent to working gameplay.

## Baseline evidence (as of rev281)

| Scope | Evidence reached | Runtime/gameplay acceptance |
| --- | --- | --- |
| Item registration | 110 source-registered items identified in Part 1 | Not verified for all items |
| Block registration | 61 source-registered blocks identified in Part 1 | Not verified for all blocks |
| Recipes / ore dictionary / smelting / creative tab | Source-specific patterns and generic completion passes audited in Part 1 | Not verified end-to-end with original corpus |
| Entity identities | 77 registrations source-proven | No complete living or boss family admitted |
| Entity watcher envelope | 77/77 schemas and access surfaces, 396 inherited vanilla + 37 source-owned entries, no collision; 94/94 source calls accounted | Does not prove movement, AI, animation or spawn correctness |
| Current plain-entity admissibility | Part 2F census: 0/77 | **Not playable**; 77/77 source methods have blockers under that strict family |
| Projectile item billboards | Seven exact vanilla-item RenderSnowball shapes audited in Part 2F-2 | No exact current Gradle/Loom + live-game confirmation |
| Fixed cuboid projectiles | rev279/280 source model candidate; rev281 additionally proves unique release/right-click direct launcher with ASM operand flow | Geometry/callback sidecar **not executable**; no current modern renderer |
| Other projectile renderers | ThrownIce block-backed, ChainBlock multipart, annihilation cube rotating/translucent, plus launcher-unproven cases classified | Not supported by their own verified runtime families |
| Living mobs and bosses | Registration + watcher data only | Combat, movement, render models, multipart bosses and AI remain open |
| Tile entities and screens | No complete Twilight-specific acceptance recorded | Unverified |
| Dimension / portal / structures / worldgen | No complete Twilight-specific acceptance recorded | Unverified |
| Advancement/progression/loot | No complete Twilight-specific acceptance recorded | Unverified |
| Particles/sounds/lighting | Local source evidence only for some paths | Unverified |
| Client/server interoperability | Generic LegacyForgeBridge transport/diagnostic work exists in earlier main builds | Complete Twilight/FML multiplayer acceptance **not** demonstrated |

Do not add these counts to produce a global completion percentage.

## Four-layer proof gate per feature

Every subsystem is tracked independently through:

1. **Identity and provenance:** exact registration, lifecycle, data and asset owners without names/IDs as production dispatch keys.
2. **Conversion semantics:** bytecode/source branch and operand closure, format/datafix, render state or gameplay intent, including explicit reject reasons.
3. **Modern executable implementation:** client-side Fabric runtime wiring and controlled/server-authoritative behavior; no unproved `runtimeComplete=true`.
4. **Real acceptance:** reproducible Gradle build + exact translated source JAR + client/game startup + native Forge1.7.10 comparison + unchanged multiplayer server checks.

Only layer 4 passing may be described as gameplay-compatible. Partial layers are engineering evidence.

## Next execution order

- Recover missing rev256–rev260 local overlay source/build wiring. The current root `src` cannot reproduce the latest shipped original main, and rev281 cannot be offered as a fresh main JAR.
- Validate rev281 fixed-model and launcher proofs against the **exact** `-tw.jar` (not merely upstream Java sources). Verify full-bright and texture/UV materialization before renderer admission.
- Build the first dedicated fixed-cuboid projectile renderer from existing source proof only; test camera-relative translation, arbitrary axis-angle rotation, texture atlas and proper light mode.
- Build independent, structural adapters for block-backed, multipart child-entity and rotating/translucent projectiles.
- Tackle mobs in bounded semantic families (constructor size, watcher state, spawn, transforms, animations, movement, hurt/interact), escalating to bosses/multipart and special behavior only when source/client and server contracts are proven.
- Then progress through BlockEntity+GUI, portals/dimension, terrain/structures, item interactions/recipes/loot, world effects, sounds/particles, and progression locks.
- Maintain an explicit acceptance corpus covering full pack startup, dimensional travel, ordinary mob interactions, every boss sequence, every important dungeon/structure, portal lifecycle, and multiplayer connect-play-disconnect loops. Compare with a native Forge 1.7.10 client connected to the same server under controlled test conditions.

## Current blockers and claim policy

- The exact `-tw.jar` bytes and native-client capture are not currently available in this work session.
- rev256–rev260 local overlay source and desktop helper are not fully recovered; see `docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md`.
- The newest delivered **complete** main JAR is recorded separately in the root README; newly committed source-only revisions do not supersede that deliverable until rebuilt, independently checked and installable.
- GitHub Actions, PR, release/tag creation, default/Bamboo branch mutations, force pushes, and original server/mod edits are prohibited under `AGENTS.md`.

Related checkpoints: `docs/SESSION-2026-10-07-TWILIGHTFOREST-PART1.md`, `docs/SESSION-2026-10-07-TWILIGHTFOREST-PART2E.md`, `docs/SESSION-2026-10-07-TWILIGHTFOREST-PART2F.md`, `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2F4.md`.

## rev283 latest addition: source geometry and light-map fidelity (2026-10-08)

The fixed-cuboid renderer candidate pipeline now requires an exact 1.7.10 ModelBox mesh/atlas UV proof and can optionally attach an exact source full-bright (1.0 / 0x00F000F0) getter proof. It is **still a source-only diagnostic**. The MoonwormShot upstream Java geometry can be exported as 24 correct UV quads for inspection, but the exact localized Twilight Forest JAR, runnable client renderer, entity spawn mapping, source texture packaging, FML networking and real Forge server/gameplay verification remain open.

See `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2F5.md`. This is not an increase in the count of confirmed playable mobs/projectiles, nor a whole-mod completion percentage.

## rev284: ordinary Block + TileEntity + TESR (2026-10-08)

The source-only generic adapter now recognizes an **ordinary Block subtype** with inherited `hasTileEntity(int)=true`, exact constructed TileEntity return, unique GameRegistry TileEntity identity, ClientRegistry TESR binding, and constructor-owned ModelBase rendering. This matters for `BlockTFMoonworm`, which is **not a BlockContainer**. The block's metadata-dependent bounds, constant light 14 and model animation invocation are upstream-source observations, **not playable conversion gates**.

This is intentionally distinct from `EntityTFMoonwormShot` projectile conversion. Both reuse a source cuboid model but differ in animation state, world placement/attachment, light and rendering pipeline.

The new evidence resource is `legacyforgebridge/block-tile-model-preflight.json` and explicitly declares no modern BlockEntity/animation/runtime or network adaptation. No additional Twilight Forest block is claimed playable based on this analysis. Exact corpus verification remains outstanding; see `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV284.md`.
