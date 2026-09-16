# Session 2026-09-16 — generic grid-pot core runtime

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus:

```text
Bamboo-2.6.8.5.jar
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

This slice continues the same generic-conversion branch. It does not add a Bamboo modid, registry-id,
class-name or checksum production profile. The exact corpus is a regression target only.

## Source family proven

`LegacyGridPotBlockAnalyzer` admits a deliberately narrow legacy family:

- a source `BlockContainer` registered with a custom `ItemBlock`;
- one lifecycle-registered TileEntity;
- exactly nine enabled booleans and nine optional ItemStack cells arranged as a 3x3 grid;
- a non-ticking TileEntity;
- source hit-to-slot math using `x/z + ForgeDirection offset / 6` and the exact 3x3 index formula;
- custom ItemBlock placement that enables the hit-selected first cell only after successful block placement;
- empty-hand item/cell removal and same-BlockItem cell addition;
- source-disabled ordinary block drops and TileEntity-owned per-enabled-cell break drops;
- per-cell boolean + optional ItemStack NBT persistence;
- dynamic collision and ray-trace geometry consisting of a 0.01-high base plus enabled 1/3 by 0.375 by 1/3 cells;
- explicit non-opaque / non-normal source rendering boundary.

An unrelated-namespace synthetic JUnit fixture carries the same structural proof without using Bamboo
names.

Against the checksum-pinned Bamboo JAR the analyzer produces exactly one admitted rule:

```text
registryName = bambooMultiPot
source block = ruby/bamboo/block/BlockMultiPot
source item  = ruby/bamboo/item/ItemMultiPot
source tile  = ruby/bamboo/tileentity/TileEntityMultiPot
legacy tile id = BambooMultiPot
cells = 9
gridWidth = 3
baseHeight = 0.01
cellHeight = 0.375
```

The exact name is asserted only in the tagged corpus regression, never used for production admission.

## Runtime implemented

`LegacyGridPotBlockPass` writes schema-1 `legacyforgebridge/grid-pot-block-rules.json` and separates
three readiness layers:

1. `coreRuntimeComplete`
2. `contentInsertionRuntimeComplete`
3. `presentationRuntimeComplete`

Only the first is enabled in this slice.

`LegacyGridPotBlockRegistry`, `ConvertedLegacyGridPotBlock`,
`ConvertedLegacyGridPotBlockEntity` and `ConvertedLegacyGridPotBlockItem` replay the proven core:

- first placement creates exactly the source-selected cell;
- placing another copy on an existing block enables the source-selected cell and consumes one item
  outside infinite-material mode;
- empty-hand use removes stored content first, then the cell itself, preserving creative/no-drop
  behavior and deleting the block when the final cell is removed;
- break/removal emits one grid-pot BlockItem for every enabled cell plus any future stored contents;
- ordinary Block loot is empty because the source explicitly returns null from `getItemDropped`;
- enabled-cell state and contents persist through modern BlockEntity storage;
- modern update tags/BlockEntity packets mirror the source TileEntity update path so client collision/selection state stays synchronized;
- the modern shape is dynamic and mirrors the proven 3x3 source geometry;
- the generated block is registered with a custom BlockItem because normal modern `BlockItem`
  placement would incorrectly create an all-disabled grid.

## Deliberately still closed

The original content-insertion branch accepts another BlockItem only when its **legacy 1.7 render
ID** is one of:

```text
1, 13, 40, CustomRenderHandler.coordinateCrossUID
```

Modern `BlockItem` identity is not equivalent to that predicate. The analyzer inventories and proves
that source branch, but schema 1 keeps `contentInsertionRuntimeComplete=false`. A held non-self item
is consumed as an interaction without mutating the grid so modern block placement cannot invent a
replacement behavior.

Presentation also stays closed. Bamboo's MultiPot uses an `ISimpleBlockRenderingHandler`; this slice
does not claim that legacy plant/block content rendering has been ported.

Therefore the family-level `runtimeComplete` marker remains false even though the gameplay/persistence
core is executable.

## Regression coverage

Normal CI adds:

- unrelated-namespace nine-cell TileEntity and legacy render-predicate structural proof;
- runtime sidecar parser fail-closed checks;
- exact slot-mapping direction math;
- generated-mod bytecode wiring for rule load, specialized Block, BlockEntity type and custom BlockItem.

The dedicated `exact-corpus` task adds the checksum-pinned Bamboo family assertion.

Whole Bamboo conversion remains `PARTIAL`. Campfire, Huton, VillagerBlock, Spa/MultiBlock,
entity/worldgen/dimension families and final legacy-class dependency closure remain separate work.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.55
```
