# 2026-09-17 — Variant snowball metadata-map boundary

## Scope

This slice follows converter revision `2026-09-17.101` and tightens the proof contract for metadata-indexed custom snowball families.

Converter revision: `2026-09-17.102`.

## Concrete gap found in `.101`

`LegacyVariantSnowballAnalyzer` proves that the projectile selector argument comes from:

`selectorMap.get(Integer.valueOf(stack.getItemDamage()))`

It separately proves the selector enum constants, selector ids and base-damage values.

Those two facts do **not** by themselves prove that selector id equals legacy item metadata. The static map may be reordered, sparse, cross-mapped or otherwise populated differently. Treating the enum id as `legacyMeta` would therefore exceed the bytecode evidence.

The unrelated synthetic fixture made this boundary especially visible: its lookup field could be referenced without proving any map-population program at all, while the `.101` sidecar still called each selector id `legacyMeta`.

## Bamboo source evidence

The historical Bamboo source corroborates the intended relation for `ItemDirtySnowball`:

- `dmgMap` is created as a `HashMap`;
- the class iterates `EnumDirtySnowball.values()`;
- each entry is inserted as `dmgMap.put(eds.getId(), eds)`;
- item damage is later used as the key for `dmgMap.get(...)`.

That source evidence is useful for defining the next bounded bytecode matcher, but production conversion must still prove the relation from the admitted source JAR rather than hard-code Bamboo names or trust an external source tree.

## `.102` contract

The proof sidecar schema is bumped to `2`.

For each admitted family it now records:

- `metadataSelectorLookupProven=true` — item metadata is proven to feed the source map lookup;
- `metadataSelectorBindingProven=false` — map population/key-to-enum binding is not yet admitted;
- `projectileSelectorStorageProven=true`;
- `selectorEnumConstantsProven=true`;
- `impactSemanticsComplete=false`;
- `runtimeImplementationWired=false`.

Variant entries now use `selectorId` instead of `legacyMeta`. This prevents downstream work from silently treating an enum-internal id as item metadata before the source map population is proven.

## Fail-closed consequence

No modern projectile runtime may route item metadata to a selector from this sidecar yet. The enum id and base-damage table are descriptive selector facts only.

The next safe gate is a source-bytecode proof for the selector map population. For the Bamboo-shaped family that proof should require a bounded relation equivalent to:

1. initialize the admitted static map;
2. iterate the selector enum values;
3. compute each key from the proven selector-id getter;
4. store that same selector value as the map value;
5. reject alternate/unproven writes or cross-mappings.

Only after that succeeds may the sidecar set `metadataSelectorBindingProven=true` and expose a proven legacy-metadata mapping. Impact-branch classification remains subsequent work.
