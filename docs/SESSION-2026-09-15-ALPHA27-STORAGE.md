# LegacyForgeBridge — Bamboo alpha.27 generic six-row storage slice

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

## Goal

Convert the first source-proven Minecraft Forge 1.7 `BlockContainer + IInventory` family into a modern 1.21.11 BlockEntity/Menu implementation without a Bamboo-specific production branch.

Bamboo `JPChest` is the exact-corpus acceptance anchor, not a hard-coded identity.

## Admitted source semantics

`LegacyStorageBlockAnalyzer` admits a block only when source bytecode proves all of the following:

- source block derives from `BlockContainer`;
- `createNewTileEntity` creates one lifecycle-registered TileEntity class;
- that TileEntity implements `IInventory`;
- inventory size is exactly 54 slots / 6 rows for this first bounded family;
- stack limit is a source constant in the valid legacy range;
- source has a literal inventory title and no custom inventory name;
- NBT uses the ordinary legacy `Items` list + per-entry `Slot` byte + ItemStack read/write path;
- usability checks the same TileEntity at its coordinates and a proven squared-distance threshold;
- activation preserves the source sneak-pass behavior and opens the TileEntity as `IInventory`;
- block removal proves inventory item drops;
- comparator behavior delegates to vanilla legacy `Container.calcRedstoneFromInventory` semantics.

If any required evidence is absent, the block remains skipped. The compiler does not infer storage behavior from names, textures, block shape, or Bamboo identities.

## Materialized rule

The converted mod owns `legacyforgebridge/storage-block-rules.json`.

A rule contains the modern block ID plus source provenance and bounded runtime semantics:

```text
slots
rows
stackLimit
title
interactionDistanceSq
sneakingPass
dropContents
comparator
presentationPending
```

`presentationPending=true` explicitly records that source-specific lid animation, special renderer and other presentation semantics are not claimed complete by this gameplay slice.

## Modern runtime

For admitted rules, generated block registration uses:

- `ConvertedLegacyStorageBlock` as the modern `EntityBlock` host;
- `ConvertedLegacyStorageBlockEntity` extending `BaseContainerBlockEntity`;
- Fabric `BlockEntityType` registration bound to the generated block;
- `NonNullList<ItemStack>` with the proven 54-slot size;
- modern `ContainerHelper` ValueInput/ValueOutput persistence;
- vanilla `ChestMenu.sixRows` for slot layout, shift-click and normal menu synchronization;
- same-block-entity + squared-distance validity;
- server-side menu opening;
- standard container content drops;
- standard comparator signal calculation.

No custom network packet or Bamboo-specific GUI class is introduced.

## Exact Bamboo acceptance

The checksum-pinned external Bamboo test requires the analyzer to identify exactly one admitted storage rule:

```text
legacy block registry = jpChest
source block = ruby/bamboo/block/BlockJpchest
source tile = ruby/bamboo/tileentity/TileEntityJPChest
legacy tile id = JP Chest
slots = 54
rows = 6
stack limit = 64
title = Chest
interaction distance squared = 64.0
sneaking pass = true
drop contents = true
comparator = true
```

The generated sidecar must map this source-proven rule to `bamboomod:jpchest`.

The exact Bamboo binary remains external and is never committed.

## Generic regression

A synthetic unrelated namespace provides the same source shape and must compile to one six-row storage rule. A second otherwise-similar block with comparator support disabled must remain skipped. This guards against both Bamboo-name special casing and permissive behavior guessing.

## Completion boundary

This slice does not make Bamboo loader-safe or complete. In particular, it does not claim completion of:

- JPChest lid/open-count presentation animation;
- source-specific renderer/model migration;
- other Bamboo BlockEntity families;
- custom inventories or menus outside the admitted six-row family;
- remaining entities, events, worldgen/dimensions or legacy executable classes.

The candidate therefore remains `PARTIAL` until the wider loader-safety and gameplay gates are satisfied.
