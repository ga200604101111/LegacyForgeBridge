# 2026-09-17 — GridPot source-proven negative insertion runtime

## Scope

This slice follows converter revision `2026-09-17.96`, where converted mod Block identities with a proven render identity were split into positive, known-negative, and unknown sets, while the negative interaction remained unwired.

Converter revision: `2026-09-17.97`.

## Source proof

`LegacyGridPotNegativeBranchAnalyzer` runs only on the already-admitted GridPot family. It requires a source fallback shaped around the original activation plus its source `removeSlot` helper.

The bounded proof requires:

- the existing exact positive render predicate proof;
- activation reaches the source remove-slot helper at least twice;
- activation contains the repeated stored-item presence/removal paths and a source ItemStack drop path;
- the helper checks that the target cell is enabled;
- the helper calls the proven TileEntity remove-cell and empty-grid methods;
- the helper preserves server-side and creative-mode gates;
- the helper contains the source ItemStack/block-item drop path;
- the helper removes the final block through the legacy world removal call.

Missing evidence leaves `sourceNegativeInsertionBranchProven=false` and the runtime stays closed.

## Runtime admission

When the source fallback proof succeeds, the GridPot sidecar writes:

- `sourceNegativeInsertionBranchProven=true`;
- `negativeContentInsertionRuntimeWired=true`;
- `negativeNonBlockItemRuntimeWired=true`;
- the `.96` `sourceProvenNegativeBlockIds` set.

The runtime rule rejects positive/negative id overlap and rejects negative runtime wiring without source fallback proof.

## Executable behavior

For an enabled cell and a non-self held item:

1. a BlockItem in the positive set keeps the `.91` replacement path;
2. a BlockItem in the known-negative set executes the proven legacy fallback;
3. a BlockItem whose source render identity is unresolved remains untouched/fail-closed;
4. a non-BlockItem executes the proven negative fallback.

The fallback preserves the source behavior:

- if the cell contains an item, remove it and drop it outside infinite-material mode while retaining the cell;
- otherwise remove the cell, drop one GridPot item outside infinite-material mode, and remove the block when the final cell disappears.

The empty-hand path now reuses the same modern removal helper, keeping the two source-equivalent behaviors aligned.

## Regression

Normal CI covers:

- unrelated-namespace structural source proof and a missing-creative-gate failure;
- registry parsing of disjoint positive/negative sets;
- rejection of negative runtime without source fallback proof;
- rejection of positive/negative id overlap;
- bytecode wiring for negative BlockItem membership, non-BlockItem admission, and the shared remove/drop/remove-cell/final-block-removal path.

The checksum-pinned Bamboo exact-corpus test requires the MultiPot negative fallback source proof to succeed.

## Remaining boundary

`contentInsertionRuntimeComplete` remains false. Unknown BlockItems are deliberately not assigned negative semantics, and inserted-content presentation is still separate work. Whole Bamboo conversion remains `PARTIAL`.
