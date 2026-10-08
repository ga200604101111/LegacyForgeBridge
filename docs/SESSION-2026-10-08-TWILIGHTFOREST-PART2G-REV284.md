# 2026-10-08 — Twilight Forest 2.3.8 Part 2G / rev284: ordinary Block + TileEntity + TESR

Branch: `feature/generic-conversion-iyamato-corpus3`

## Why this slice is distinct from rev281–283

**Moonworm is a block.** Upstream `BlockTFMoonworm` inherits `BlockTFCritter` -> ordinary `net.minecraft.block.Block`. It does **not** extend `BlockContainer`; its inherited `hasTileEntity(int)` returns true, and its `createTileEntity(World,int)` creates a `TileEntityTFMoonworm`. That TileEntity is registered through `GameRegistry.registerTileEntity`, and the client binds a `TileEntityTFMoonwormRenderer` through `ClientRegistry.bindTileEntitySpecialRenderer`.

**MoonwormShot is separate.** The Moonworm Queen's release-use callback creates `EntityTFMoonwormShot`. That remote projectile uses `RenderTFMoonwormShot` and the same `ModelTFMoonworm` geometry without invoking the TileEntity animation method. The placed block's TESR calls the model's TileEntity animation entrypoint. Reusing the shared mesh does not make these two rendering/behavior lifecycles interchangeable.

Upstream source reference (not proof of the exact translated `-tw.jar` bytes): [Benimatic/twilightforest @ 98b88bde](https://github.com/Benimatic/twilightforest/tree/98b88bde74d6db0aa463dba304f5c13acb6140fb).

## New generic source recognition

`LegacyBlockTileModelPreflight` is a source-owned, non-executing ASM analyzer. Its production path consumes the existing generic `LegacyRegistryAnalyzer` and `LegacyLifecycleAnalyzer` results, then proves the client TESR class-literal/new-instance registration shape from exact source bytecode. It is **not** a mod-name or block-ID allow-list.

Candidate requirements:

1. Unique source-registered Block with source-owned ancestry to vanilla `Block`, **including normal Block subclasses**.
2. Nearest inherited source `hasTileEntity(int)` method returns an unconditional constant `true`.
3. Source `createTileEntity(World,int)` returns one directly constructed, source-owned TileEntity type with a declared matching no-arg constructor.
4. Unique, source-proven `GameRegistry.registerTileEntity` identity; reject missing or duplicate class/name mappings.
5. Exactly one proven `ClientRegistry.bindTileEntitySpecialRenderer(Class,new Renderer())` or `ClientRegistry.registerTileEntity(Class,String,new Renderer())` call; malformed or unresolved registration calls fail closed rather than guessed.
6. Renderer source ancestry to `TileEntitySpecialRenderer`, with one constructor-assigned source-owned `ModelBase` field, and reachable calls from the renderer's `renderTileEntityAt` entrypoint to the model draw method.
7. Record—not assume equivalent runtime support for—source tile ticking, a reachable tile-typed model method call, tile-field reads, metadata-dependent bounds access, direct constant light return and a zero return from the `quantityDropped` method. **A zero quantityDropped method does not establish the full legacy block-drop pipeline.**

It deliberately does **not** prove the six-facing mapping, mutable animation math, sprite atlas/UV, state persistence, networking, damage/drop callbacks, or the modern BlockEntityType/renderer implementation.

`LegacyBlockTileModelPreflightPass` emits independent `legacyforgebridge/block-tile-model-preflight.json` evidence. The source-only resource contains provenance, source-reachable facts, optional light/quantity evidence, and explicit skipped reasons. It has `sourceOnly=true`, `runtimeWired=false`, `blockEntityRuntimeWired=false`, `animationSemanticsProven=false`, `tileStateSyncProven=false`, `modernBlockGeometryProven=false`, and per-candidate `runtimeReady=false`. It **never** creates executable entity/block rules. Diagnostics use `SupportLevel.AUTO` so the optional census cannot downgrade a previously converted candidate to PARTIAL merely for reporting source evidence.

The pass is added to `GenericLegacyModProfile` after `GenericContentPass`; there is **no** special Twilight Forest selector.

## Exact upstream source behavior observed for Moonworm

- Placement and survival are inherited from `BlockTFCritter`: six attachment directions encoded by metadata, conditional face support and neighbor updates, non-opaque/non-normal shape.
- `BlockTFMoonworm`: metadata-dependent bounds, constant legacy block light value 14, and zero `quantityDropped` return. Another path may affect drops; complete behavior is not proven by this return value.
- Client renderer: tile metadata selects mounting orientation, tile `currentYaw` rotates the model around Y, GL scale (1, -1, -1), then it calls `ModelTFMoonworm.setLivingAnimations(tile,partialTime)` and draws at scale 0.0625.
- Tile `updateEntity`: source-local yawDelay/currentYaw/desiredYaw state, randomized intervals, and a sinusoidal vertical offset in the model's animation method. The source includes no obvious custom TileEntity packet/NBT persistence in these two classes; do not assume that source state can be synchronized or simulated correctly until the full 1.7.10 callback and network closure is proved.
- Legacy `RenderBlockTFCritters` reports false for world rendering and does not itself supply geometry, so the TESR is the visible candidate path in this upstream source—not proof of modern client behavior.

## Regressions and evidence boundaries

Executed on Java 21 against the **real uploaded rev260 LFB JAR** as classpath, with a temporary substitution to the JDK-internal ASM namespaces and synthetic 1.7.10 class bytecode:

- **18/18** renamed source block/TESR link tests: inherited Block marker, direct returned TileEntity, duplicate/missing block and tile identities, both official ClientRegistry binding forms, ambiguous/missing renderer, wrong renderer base, missing model, renderer draw-closure failure, optional animation, dynamic light, and unregistered source class.
- **2/2** source-manifest tests: source-only safety/readiness flags, and omission of unproven light state. For these tests the client Gson implementation is replaced with a minimal compatible API double.

No Gradle/Loom test suite, exact translated Twilight Forest `-tw.jar`, Minecraft Fabric/VFP startup, native 1.7.10 client comparison or original unchanged server gameplay test was executed. No runnable BlockEntity implementation, dimension or full original mod conversion is claimed.

The rev260 JAR remains byte-identical at SHA-256 `03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`. The rev256–rev260 source handoff remains incomplete, so **no new complete installable main JAR** is emitted by this source-only checkpoint.

## Next bounded generic slices

1. Prove from actual bytecode the six-direction attachment metadata `Block.onBlockPlaced`, neighbor-survival and bounds mapping, preserving original server state.
2. Prove the TileEntity local tick state machine, constructor defaults, NBT and network authority closure, including random interval bounds; do not invent client animation state.
3. Prove the model `setLivingAnimations(TileEntity, partialTicks)` pivot writes and renderer-facing transforms (tile metadata + yaw + negative scale), then compile a reusable data-driven TileEntity animation presentation family using the shared cuboid source mesh.
4. Verify block light, transparency, drops and placement support separately; only admit true BlockEntity runtime after all required source and modern pipeline gates pass.
5. Obtain/check SHA of the **exact** `twilightforest-1.7.10-2.3.8-tw.jar`, build a complete main from the pinned rev260 base only with verified compiled overlays, and run live 1.21.11 Fabric + unchanged Forge1.7.10 acceptance.

Repository policy honored: only this feature branch; `[skip ci] [skip actions]` on every commit; no Actions, PR, release/tag, force-push, main/Bamboo changes, or original mod/server mutation.
