# 2026-09-17 — GridPot safe vanilla 1.7.10 insertion identities

## Scope

This slice follows converter revision `2026-09-17.92`, where the GridPot/MultiPot positive insertion runtime already accepted source-proven converted mod blocks with either portable constant legacy render types (`1`, `13`, `40`) or an exact symbolic static render identity used by the source predicate.

Converter revision: `2026-09-17.93`.

## Why a bounded vanilla table is required

The legacy positive insertion branch is keyed by 1.7.10 block render type. Render type alone is not enough to safely produce a modern identity because several admitted legacy render families multiplex multiple modern block identities through item damage/metadata.

Examples intentionally excluded in this checkpoint include `sapling`, `tallgrass`, `red_flower`, and `double_plant`.

## Admitted vanilla subset

`LegacyGridPotVanillaInsertion1710` therefore contains only the first metadata-independent, one-to-one platform identities:

- `brown_mushroom`, legacy render type `1` -> `minecraft:brown_mushroom`;
- `red_mushroom`, legacy render type `1` -> `minecraft:red_mushroom`;
- `cactus`, legacy render type `13` -> `minecraft:cactus`.

This is a Minecraft 1.7.10 platform compatibility table, not a Bamboo-specific production table.

## Materialization

For a GridPot rule whose source insertion predicate has already passed the exact `.91/.92` proof, `LegacyGridPotBlockPass` now combines source-proven converted mod ids, exact symbolic render-identity matches, and the metadata-independent vanilla subset above.

The combined ids continue to use the schema-1 `sourceProvenInsertionBlockIds` runtime set for backward compatibility. Additional audit fields distinguish mod-backed and vanilla-backed identities and counters.

## Runtime behavior

No new mutation path is introduced. The `.91` path remains authoritative: eligible held `BlockItem`, remove/drop old content, store exactly one new stack, and consume one held item outside infinite-material mode.

## Regression coverage

Normal CI pins the exact three vanilla identities, verifies render type 40 contributes no vanilla identity in this checkpoint, explicitly excludes metadata-ambiguous legacy families, and proves the runtime parser accepts mixed converted-mod and `minecraft:*` eligibility ids.

## Boundary

This checkpoint does not claim full GridPot content insertion closure. Metadata-demultiplexing, the negative insertion branch, and inserted-content presentation remain separate proof/runtime work. The overall Bamboo candidate remains `PARTIAL`.
