# 2026-09-17 — Grid-pot source-proven mod content insertion subset

Branch: `feature/generic-conversion-bamboo-corpus2`

Converter revision: `2026-09-17.91`.

## Goal

Extend the executable generic nine-cell grid-pot core with a bounded positive subset of the source content-insertion branch, without pretending the full legacy render predicate has been ported.

## Hardened source proof

The grid-pot family now requires its TileEntity to prove a setter with the canonical shape:

```text
enabled[slot] gate
items[slot] = new ItemStack(item, 1, meta)
markDirty()
```

The `ItemStack(Item,int,int)` constructor proof pins the count argument itself to constant `1`; an unrelated constant elsewhere in the method is insufficient.

The block activation proof additionally requires the source TileEntity setter to be called directly from one held `ItemStack` local's:

```text
getItem()
getItemDamage()
```

and retains the existing source remove-item path.

The render predicate is no longer proven merely by seeing constants near `getRenderType()`. It requires direct integer comparisons whose operands are:

```text
block.getRenderType() == 1
block.getRenderType() == 13
block.getRenderType() == 40
block.getRenderType() == <symbolic static int render id>
```

Equivalent branch polarity is accepted through the JVM integer comparison opcodes; dynamic/mixed identities remain closed.

## Portable eligibility table

`LegacyRegisteredBlockRenderTypeAnalyzer` inventories registry-proven source mod blocks and admits only an effective source `getRenderType/func_149645_b()I` whose direct returns all resolve to the same integer constant or symbolic static int field.

For `.91`, only constant render types `1`, `13`, and `40` are materialized into modern block IDs. Symbolic custom render IDs are recorded by the analyzer but are not executable eligibility yet.

`legacyforgebridge/grid-pot-block-rules.json` remains schema 1 and adds:

- `sourceProvenModInsertionEligibilityWired`
- `sourceProvenModContentInsertionWired`
- `sourceProvenInsertionBlockIds`
- `sourceProvenInsertionBlockCount`

`contentInsertionRuntimeComplete` stays `false`.

## Runtime semantics

For an enabled target cell and a held modern `BlockItem` whose block ID is in that source-proven table:

1. use the legacy hit-slot formula with the opposite face direction;
2. remove existing stored content;
3. drop the previous content only outside infinite-material mode;
4. store exactly one copy of the held stack;
5. shrink the held stack by one outside infinite-material mode;
6. use the existing BlockEntity changed/update synchronization path.

The separate self-grid-pot add-cell path is unchanged.

## Deliberately still closed

- vanilla blocks that happened to use legacy render types 1/13/40;
- the symbolic `coordinateCrossUID` family;
- the legacy negative/fallback branch for blocks outside the positive predicate;
- MultiPot custom presentation/content rendering.

An item outside the positive source-proven table continues to consume the interaction without inventing state changes.

## Regression boundary

Normal CI covers direct constant/static render identity proof, dynamic/mixed rejection, portable eligibility materialization, runtime sidecar validation, and runtime bytecode wiring for eligibility → previous-content removal/drop → store → stack shrink.

The checksum-pinned exact-corpus regression additionally requires Bamboo MultiPot's hardened insertion branch proof and the registered `BlockCrossLamp` source render type `1`. These Bamboo identities are test anchors only and never appear in production admission logic.
