# 2026-09-17 — GridPot stored-content presentation runtime

## Scope

This slice follows converter revision `2026-09-17.99`, which proves the legacy GridPot renderer consumes the synchronized nine-cell Item/meta state at the source 3x3 transforms.

Converter revision: `2026-09-17.100`.

## Runtime admission

`LegacyGridPotPresentationRuntimePass` combines two independent sidecars:

- the GridPot core runtime rule (`legacyforgebridge/grid-pot-block-rules.json`);
- the `.99` presentation source proof (`legacyforgebridge/grid-pot-presentation-proof.json`).

A client presentation rule is emitted only when both are present and prove the bounded 9-cell / 3x3 family.

The resulting sidecar is:

`legacyforgebridge/grid-pot-presentation-runtime.json`

It records:

- modern block identity;
- source block and renderer provenance;
- source-proven `-0.333 / 0 / 0.333` grid offsets;
- source-proven `0.25` content vertical anchor;
- source `0.75` content scale;
- `storedContentPresentationProven=true`;
- `storedContentPresentationRuntimeWired=true`;
- `adaptation=MODERN_ITEM_MODEL_RENDER_STATE`;
- `exactLegacyGeometry=false`.

The last field is deliberate. The runtime does not claim byte-for-byte recreation of legacy `RenderBlocks` crossed-square/cactus geometry.

## Minecraft 1.21.11 rendering path

`ConvertedLegacyGridPotRenderer` uses the native 1.21.11 extract/submit pipeline:

1. `extractRenderState` copies only enabled, non-empty synchronized GridPot cell contents;
2. each `ItemStack` is resolved through `ItemModelResolver.updateForTopItem` into `ItemStackRenderState` using `ItemDisplayContext.NONE`;
3. `submit` places each resolved model at the source-proven 3x3 X/Z offset and vertical anchor;
4. the model bounding box is used to center X/Z and align the model bottom to the source anchor;
5. `ItemStackRenderState.submit` sends the resolved modern item model through `SubmitNodeCollector` with the BlockEntity light value.

This preserves content identity and GridPot cell layout while intentionally adapting old fixed-function Block rendering to modern model semantics.

## Client wiring

`ConvertedGridPotPresentationRuntime` reads only the admitted runtime sidecar. It rejects malformed rules, wrong adaptation modes, and any rule claiming exact legacy geometry.

For each admitted rule it resolves the already-registered converted GridPot `BlockEntityType` and registers `ConvertedLegacyGridPotRenderer` through the same `BlockEntityRenderers` path used by the existing processor presentation runtime.

The generated converted mod client initializer now calls `ConvertedGridPotPresentationRuntime.initializeMod(modId)`.

## Regression coverage

Normal CI now covers:

- source proof to runtime-sidecar promotion;
- blocked/non-core rules staying out of runtime admission;
- runtime-sidecar parsing and exact-geometry rejection;
- renderer bytecode calls to `ItemModelResolver`, model bounding-box placement and `ItemStackRenderState.submit`;
- generated client entrypoint wiring.

The checksum-pinned Bamboo corpus additionally runs the exact source proof and runtime admission pipeline and requires `ruby/bamboo/block/BlockMultiPot` to receive an adapted modern presentation rule.

## Remaining boundary

This slice completes the visible stored-content presentation adapter, but it still does **not** set the older GridPot core sidecar's broad `presentationRuntimeComplete` flag to true because:

- legacy crossed-square/cactus geometry is intentionally adapted, not exact;
- exact legacy biome tint/anaglyph behavior is not recreated;
- unresolved gameplay BlockItems remain fail-closed until their render identity can be proven.

Whole Bamboo conversion therefore remains `PARTIAL`.
