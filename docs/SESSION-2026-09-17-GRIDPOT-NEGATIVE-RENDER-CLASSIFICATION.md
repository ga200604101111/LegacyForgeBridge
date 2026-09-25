# 2026-09-17 — GridPot source-proven negative render classification

## Scope

This slice follows converter revision `2026-09-17.95`, where registered legacy Block render identity proof gained a bounded constructor-field path and Bamboo `bamboosingle` / `bamboo2` can resolve to the same symbolic `coordinateCrossUID` used by the GridPot positive predicate.

Converter revision: `2026-09-17.96`.

## Why classification comes before runtime

It is unsafe to implement the legacy GridPot negative insertion branch as the complement of the positive id set. A converted `BlockItem` can be absent from the positive set simply because its source render identity has not been proven yet.

This checkpoint therefore creates a separate **known-negative** set without executing it.

## Classification

For every registry-proven converted mod Block whose render identity is present in `LegacyRegisteredBlockRenderTypeAnalyzer`:

- constant render types `1`, `13`, and `40` are positive;
- a symbolic static field directly equal to one of the GridPot source predicate symbolic fields is positive;
- every other proven constant or symbolic render identity is known-negative;
- a converted block with no proven render identity is neither positive nor negative.

The pass writes per-rule:

- `sourceProvenNegativeBlockIds`;
- `sourceProvenNegativeBlockCount`;
- `sourceProvenNegativeRenderClassificationWired=true` when the GridPot source predicate itself is proven;
- `negativeContentInsertionRuntimeWired=false`.

The root records `sourceProvenNegativeModRenderClassificationWired=true` and the total known-negative mod block identity count.

## Fail-closed boundary

This slice does **not** execute the legacy negative branch. It does not remove stored contents, remove a cell, or infer negative semantics for unknown BlockItems.

A later runtime slice must additionally prove the source negative control-flow and then consume only:

1. this known-negative BlockItem set; and
2. any separately proven non-BlockItem negative family.

Unknown BlockItems must remain untouched.

## Regression coverage

Normal CI proves positive and negative sets are disjoint, `1/13/40` never leak into the negative set, exact symbolic predicate matches remain positive, and a generated block without render proof remains absent from both sets.

The exact Bamboo corpus additionally pins `ricePlant`, `beanPlant`, and `tomatoPlant` to source render type `6`, proving real Bamboo identities that are known-negative relative to the MultiPot positive predicate.

Whole Bamboo conversion remains `PARTIAL`.
