# 2026-09-17 — Grid-pot symbolic render identity insertion

Branch: `feature/generic-conversion-bamboo-corpus2`

Converter revision: `2026-09-17.92`.

## Purpose

Extend `.91` without resolving or emulating legacy custom render numeric IDs.

The admitted grid-pot source predicate already proves a direct comparison between `Block#getRenderType()` and a static integer field. `.92` preserves that comparison operand as a canonical symbolic key:

```text
<field owner internal name>#<field name>
```

A registry-proven source block is eligible when its effective `getRenderType/func_149645_b()I` directly returns the exact same static field.

No runtime integer value is read, executed or guessed.

## Materialization

`LegacyGridPotBlockAnalyzer.Rule` now carries `contentInsertionSymbolicRenderFields`.

`LegacyGridPotBlockPass` expands the `.91` positive eligibility table when either:

- source render type is constant `1`, `13` or `40`; or
- source render type is a static field whose canonical owner/name identity exactly equals one of the symbolic fields directly compared by the proven grid-pot predicate.

The resulting modern block IDs continue to flow through the same `.91` runtime gate, so no runtime branch broadening occurs outside the source-proven positive table.

## Bamboo exact anchor

The checksum-pinned exact-corpus regression requires one shared symbolic identity between:

- MultiPot's source insertion predicate; and
- `BlockBambooShoot#getRenderType()`.

The expected source identity is `ruby/bamboo/CustomRenderHandler#coordinateCrossUID`.

This Bamboo name is test-only. Production admission is generic owner/field equality.

## Still closed

- vanilla render-type membership;
- source blocks whose render type is stored in an instance field or computed dynamically;
- the negative/fallback branch for non-eligible held blocks;
- MultiPot content presentation/rendering.

Accordingly `contentInsertionRuntimeComplete` and overall grid-pot `runtimeComplete` remain false.
