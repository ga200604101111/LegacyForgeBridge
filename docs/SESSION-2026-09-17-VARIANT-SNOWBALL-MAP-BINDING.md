# 2026-09-17 — Variant snowball metadata-map binding proof

## Scope

This slice follows converter revision `2026-09-17.102`, which separated metadata lookup evidence from the still-unproven selector-map population relation.

Converter revision: `2026-09-17.103`.

## Source target

The historical Bamboo `ItemDirtySnowball` source initializes its private static selector map as follows:

1. allocate a fresh `HashMap`;
2. iterate `EnumDirtySnowball.values()` with the compiler-generated array/index foreach loop;
3. insert each selector with `dmgMap.put(eds.getId(), eds)`;
4. later use `dmgMap.get(stack.getItemDamage())` when constructing `EntityDirtySnowball`.

Production analysis remains name-agnostic. Bamboo package, class and field names appear only in the checksum-pinned exact-corpus regression.

## Bounded bytecode proof

`LegacyVariantSnowballMapBindingAnalyzer` starts only from families already admitted by `LegacyVariantSnowballAnalyzer`. For each selector map it requires:

- the map field is private static and has the admitted `Map`/`HashMap` descriptor;
- the field is initialized exactly once from a fresh `HashMap` in `<clinit>`;
- `<clinit>` contains exactly one canonical selector population loop shaped as the Java enhanced-for lowering of `Selector.values()`;
- the loop key is produced by the previously proven selector-id getter;
- the value inserted into the map is the exact same selector local used to produce the key;
- the loop index starts at zero, increments by one and exits at the enum-values array length;
- additional selector-map accesses in `<clinit>` are restricted to the admitted population use and an optional read-only `size()` call;
- every same-class selector-map access outside `<clinit>` is read-only (`get`/`size`) and the field is not reassigned or returned/escaped through the bounded shape.

Anything outside that surface remains fail-closed.

## Regression

Normal CI now covers two unrelated-namespace fixtures:

- canonical `Enum.values() -> map.put(selector.getId(), selector)` population is admitted;
- a cross-mapped variant that uses a constant key instead of the selector id is rejected.

The existing lookup-only fixture intentionally has no proven population program and therefore remains `metadataSelectorBindingProven=false`.

A checksum-pinned exact-corpus test requires Bamboo's `snowball` family to prove the `dmgMap` binding when the exact corpus is supplied.

## Sidecar schema 3

`legacyforgebridge/variant-snowball-proof.json` now reports `schemaVersion=3`.

For each family:

- `metadataSelectorLookupProven` remains the item-metadata lookup proof;
- `metadataSelectorBindingProven` is true only when the new map-population proof succeeds;
- an unproven family records `metadataSelectorBindingBlocker`;
- every variant always exposes `selectorId`;
- `legacyMeta` is emitted only when source map binding is proven;
- `impactSemanticsComplete=false` and `runtimeImplementationWired=false` remain unchanged.

The root also reports `metadataBindingFamilies`.

## Remaining boundary

This slice proves selector routing only. It does not compile any custom projectile impact semantics, item consumption/sound behavior, dispenser entry point, networking/persistence surface or modern EntityType runtime.

The next safe projectile gate is impact-branch classification, now that metadata-to-selector routing can be source-proven without assuming enum ids are metadata ids.
