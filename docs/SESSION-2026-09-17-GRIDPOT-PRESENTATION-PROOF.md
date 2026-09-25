# 2026-09-17 — GridPot stored-content presentation proof

## Scope

This slice follows converter revision `2026-09-17.98`. GridPot gameplay already has source-proven positive insertion, known-negative classification/fallback, vanilla metadata demultiplexing, persistence and network synchronization, but inserted content is not yet rendered by the modern client.

Converter revision: `2026-09-17.99`.

## Proof boundary

`LegacyGridPotPresentationAnalyzer` links the presentation surface from source bytecode rather than from Bamboo names:

1. the registered GridPot block must have a source-proven static-field `getRenderType()` identity;
2. that exact static field must be used as the key of a unique legacy custom-renderer map insertion;
3. the renderer constructor must prove the three cell offsets `-0.333 / 0 / 0.333`;
4. the world renderer must iterate enabled GridPot cells and dispatch to a source helper;
5. the helper must read both stored item identity and stored metadata from the proven GridPot TileEntity;
6. the helper must convert the item back to a Block and inspect its legacy render type;
7. the admitted render branches must contain constants `1`, `13`, `40` plus the same symbolic custom-render identity used by the source insertion predicate;
8. the helper must contain the bounded source transforms used for inserted contents (`4/16` vertical translation, `0.75` crossed-square scale, `0.125` cactus half width) and the expected RenderBlocks/Tessellator draw calls.

Any missing or ambiguous step leaves presentation closed.

## Materialized IR

The generic profile now writes:

`legacyforgebridge/grid-pot-presentation-proof.json`

with, for every admitted source renderer:

- source block / TileEntity / renderer classes;
- `storedContentPresentationProven=true`;
- the three proven grid offsets;
- content vertical translation;
- crossed-square scale;
- cactus half width;
- `storedContentPresentationRuntimeWired=false`.

The last flag deliberately remains false. This checkpoint proves source intent and transforms only; it does not yet register a 1.21.11 BlockEntityRenderer.

## Exact Bamboo coverage

The checksum-pinned Bamboo corpus must prove that `ruby/bamboo/block/BlockMultiPot` is presented by `ruby/bamboo/render/block/RenderMultiPot` and that the source renderer has the admitted stored-content surface.

This prevents a future renderer refactor, mapping change or weak call-inventory match from silently enabling a presentation adapter that no longer corresponds to the source JAR.

## Next runtime slice

Minecraft 1.21.11 uses the extract/submit rendering pipeline. The next safe slice will:

- copy each enabled GridPot cell's synchronized `ItemStack` into a client render state;
- resolve it through `ItemModelResolver` into `ItemStackRenderState`;
- submit those item states through `SubmitNodeCollector` at the source-proven 3x3 cell transforms;
- register the renderer only for rules whose `.99` proof is present.

That modern item-model rendering is an explicit adaptation of the legacy crossed-square/cactus RenderBlocks presentation; it will be marked separately from exact source proof.

## Remaining boundary

`presentationRuntimeComplete` remains false at this checkpoint. Unknown BlockItems remain fail-closed in gameplay, and whole Bamboo conversion remains `PARTIAL`.
