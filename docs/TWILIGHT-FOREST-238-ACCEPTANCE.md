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

## rev285: placed-block state observation vs server sync (2026-10-08)

The rev284 generic ordinary-Block → TileEntity → TESR source preflight now also inventories the source-rendered TileEntity field dependencies, update-tick field writes, metadata lookups, OpenGL rotations and model animation-pivot writes. NBT and legacy packet-hook **presence is not packet payload/dataflow proof**. Both tile networking and animation runtime readiness remain `false`. No placed Moonworm or other legacy block has been newly confirmed playable from this source-only change.

The historical upstream Moonworm placed block (14 source light) is different from the Queen-fired MoonwormShot projectile (source fullbright). Keep independent conversion/acceptance paths; the exact translated `-tw.jar` has not yet been tested. See `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV285.md`.

## rev286 — six-facing placed Block/TESR orientation source proof (2026-10-08)

The generic ordinary-Block + TileEntity + TESR preflight now derives bounded static X/Z rotation angles for all 16 legacy metadata values from source Java 7 bytecode (including explicit `&7` masking when present), and flags additional source GL rotation separately. This is independent of any mod name or Moonworm ID.

**Not yet accepted as playable**: the Moonworm TileEntity's dynamic `currentYaw`, the animation pivot writes, authoritative field/packet sync, facing-dependent collision/attachment/placement, full TESR GL transform sequence, real client renderer, the exact translated Twilight Forest `-tw.jar` source, and modern/FML multiplayer verification. Do not promote these new source proofs to an active modern block renderer or increment a gameplay completion tally.

Regression checkpoint: 24 rev286 analyzer + 6 manifest + 22 rev285 + 20 rev284 = **72 independent synthetic tests passed**, not a real Minecraft runtime test. See `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV286.md`.

## rev287 — static facing followed by a source TileEntity field Y angle (2026-10-08)

A new **generic**, exact-bytecode analyzer verifies that a TESR's third `GL11.glRotatef` is an unconditional rotation around Y and that its angle is read directly from one source-owned TileEntity instance field (integer-to-float or directly float). It revalidates rev286's static X/Z facing proof against the same source JAR and rejects other receivers, missing members, conditional rotations, additional transformations and forged evidence. The diagnostic sidecar can identify the source field and distinguish source tick-write observations from mere packet-hook presence.

**No new playable feature is admitted.** The yaw field is not known to be transmitted to the modern Fabric client, and the source model's per-part `setLivingAnimations` formula, 1.7.10 TESR transform closure, client BlockEntity renderer, exact translated `-tw.jar`, original Forge server comparison, and complete main JAR build remain unverified. No automatic legacy server tick replay is authorized.

Synthetic source-only regression: rev287 (19 analyzer + 9 manifest), rev286 (24 + 6), rev285 (16 + 6), rev284 (18 + 2): **100/100 independently executed** with Java 21/JDK ASM substitutions and compact dependency test doubles. These are not official Loom/JUnit or Minecraft tests. Details: `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV287.md`.

## rev288 — source-causal per-part TileEntity animation proof (2026-10-08)

The **generic**, non-executable Block+TileEntity+TESR preflight can now prove a ModelRenderer animation family in source-owned 1.7.10 bytecode: each constructed part has exactly one constant Y-pivot reset followed by one zero-guarded dynamic update, with the source expression `originalPivot + Math.min(0, MathHelper.sin(sourceTileInputsAndPartialTick))`. The original TileEntity integer guard and individual state-field dependencies are captured. Fake model assignments, missing operands, extra arithmetic, unrelated method calls, animation receiver mismatches and inverted guards fail closed.

**Gameplay acceptance remains unchanged:** no server packet/payload proof, no client replay of legacy updateEntity, no Fabric 1.21.11 animated BlockEntityRenderer, no exact translated `twilightforest-1.7.10-2.3.8-tw.jar` run, no original Forge server/live client comparison. The existing sidecar remains `runtimeWired=false`, `tileStateSyncProven=false`, per-candidate `runtimeReady=false`, with no executable rules. The analyzer has no Twilight Forest name/ID dispatch.

Rev288 independent Java21 synthetic tests 43/43, previous rev284–287 tests 100/100, **143/143 combined** (ASM import substitutions + JUnit/Gson test doubles, not real Loom/Gradle or in-game). See `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV288.md`.

## rev289 — renderer state NBT field-pair provenance is not packet synchronization (2026-10-08)

The generic Block + TileEntity + TESR preflight now matches source visual-state fields to **both** NBT writer and reader through verifier-frame operand provenance, literal tag keys and exact primitive types. Actual Java 7 source-inheritance super calls must be proven; overlapping NBT tag names and unverified reads/writes are rejected. An inherited source NBT override that only forwards to `TileEntity` does **not** prove the source animation fields are serialized.

There is **still no network/client runtime admission**: `sourceTilePacketPayloadProven=false`, `tileStateSyncProven=false`, `runtimeWired=false`, per-source `runtimeReady=false` and no executable rules. In the public upstream Moonworm TileEntity, `currentYaw`, `desiredYaw`, and `yawDelay` have no paired NBT persistence from the tile source. Actual translated `-tw.jar`, S35 packet payload, Fabric BlockEntityRenderer, Forge multiplayer parity and full main JAR build all remain unverified.

Latest Java21 independent synthetic source/manifest tests: rev289 30 analyzer + 11 manifest; rev284–288 143 regressions: **184/184 passed** with JDK-internal ASM substitutions and dependency test doubles, not official Loom/Gradle or in-game. Details: `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV289.md`.

## rev291 — exact-source generic static TESR geometry (2026-10-08)

**First actual client asset change** of rev279+ beyond source-only diagnostics: a newly compiled `LegacyStaticTileModelBakerPass` integrated into the complete rev260-preserving **rev291 preview main JAR** parses the user-provided exact `twilightforest-1.7.10-2.3.8-tw.jar` and outputs source-proven, six-direction static 3D cuboid models with original model PNG texture/UV and all 16 blockstate metadata mappings for **2** ordinary legacy blocks:

- **Moonworm**: old converted candidate was `cube_all`; new model has **4** parts and six static facing poses.
- **Cicada**: old converted candidate was `cube_all`; new model has **6** parts and six static facing poses.
- **Firefly**: old converted candidate was `cube_all`; remained **excluded** because its legacy TESR has an unproved multi-pass glow/blend renderer. No fake static equivalence admitted.

This does **not** reduce the independently measured **22 magenta placeholder block models**: Moonworm and Cicada previously had *textured cube_all*, not magenta placeholders. There are still approximately **8 barrier placeholder items**, **0/77 runtime entity adaptations**, and major portal/worldgen/interaction/boss gaps. This is a narrow but real first geometry improvement, not a complete mod conversion.

Local proof: real compiled rev291 Pass loaded from the packaged JAR (with temporary JDK-internal ASM test-package relocation, no shim distributed) configured a 32-pass generic profile and processed the **exact user source JAR** successfully; 2 admitted source models, Firefly skipped, all JSON element/UV/16-state constraints checked. **Actual Minecraft 1.21.11 Fabric/ViaFabricPlus rendered appearance and original Forge 1.7.10 multiplayer have not been tested.** The generated mod manifest still declares `status=partial` and `installable=false`; no gameplay readiness percentage can be inferred.

Full development evidence and binary provenance: `docs/SESSION-2026-10-08-REV291-STATIC-TESR-VISUAL-PREVIEW.md`, `diagnostics/rev291-twilight-exact-source-visual/verification.json`.

## rev292 — bulk *resource preview* repairs and Prism restart watchdog (2026-10-08)

Unlike source-only rev279–290, the **complete rev292 candidate JAR** contains an executable generic batch resource Pass. Against the user's *exact* original `twilightforest-1.7.10-2.3.8-tw.jar` and an isolated copy of the previously converted `-lfb.jar`, its source-icon inference recovered **22 previously magenta block models, 20 handheld weapon/tool presentation models, and 7 fallback item icons**. The static multipart Moonworm/Cicada preview from rev291 is preserved. The batch repairs do not claim the exact multi-texture/face/metadata mappings, weapon damage/spells, special block behavior, animations or networking.

The bundled Prism restart helper was minimally patched in both the complete main and its nested desktop-helper JAR. An actual fake-Prism `--dir / --launch` subprocess test on Linux/Xvfb found that old rev291 exited immediately after dispatch, while rev292 remained alive for follow-up readiness monitoring. This **does not certify Windows PrismLauncher restart** without an on-device test.

**Still not accepted as whole-mod gameplay**: original converted candidate was `PARTIAL` and lacked a complete executable runtime for its 77 registered entities; world/dimension/portals, Boss AI and special weapons remain blocked. Actual Fabric 1.21.11 client startup and unchanged Forge 1.7.10 server validation have not been run here. The source of this rev292 candidate and full QA boundaries are in `docs/SESSION-2026-10-08-REV292-BATCH-VISUAL-PRISM-WATCHDOG.md`.
