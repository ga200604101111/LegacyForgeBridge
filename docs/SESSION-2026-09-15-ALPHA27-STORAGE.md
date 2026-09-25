# LegacyForgeBridge — Bamboo alpha.27 generic six-row storage slice

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

## Goal

Convert the first source-proven Minecraft Forge 1.7 `BlockContainer + IInventory` family into a modern 1.21.11 BlockEntity/Menu implementation without a Bamboo-specific production branch, then preserve presentation only when the source bytecode and bundled assets prove an ordinary full-cube two-texture facing model.

Bamboo `JPChest` is the exact-corpus acceptance anchor, not a hard-coded identity.

## Admitted gameplay semantics

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

## Admitted presentation semantics

Presentation is analyzed independently by `LegacyStoragePresentationAnalyzer`. The first admitted family requires source proof that:

- render type is ordinary block rendering (`0`);
- the block is opaque and renders as a normal full cube;
- explicit bounds, when present, remain the full `0..1` cube;
- placement writes raw metadata through `World.setBlockMetadataWithNotify(..., flag=3)`;
- that metadata comes from the standard legacy yaw-quadrant calculation `floor(rotationYaw * 4 / 360 + 0.5) & 3`;
- metadata `0/1/2/3` maps the front icon to legacy sides `NORTH/EAST/SOUTH/WEST` respectively;
- exactly two block icons are source-registered for the admitted face behavior;
- both referenced PNG assets exist in the copied source resources.

If any presentation proof or asset is missing, gameplay storage can still be materialized, but `presentationPending=true` remains and the source presentation is not guessed.

## Corrected exact-source finding

A reinspection of the checksum-pinned `BlockJpchest.class` established that JPChest does **not** use a custom TESR, chest-lid model, or open-count animation. It is an ordinary opaque full cube with four raw-metadata facings and two 16x16 block textures:

```text
front icon = bamboo:jpchest_f
other icon = bamboo:jpchest_o
assets = assets/bamboo/textures/blocks/jpchest_f.png
         assets/bamboo/textures/blocks/jpchest_o.png
```

The previous note treating lid animation/custom rendering as an outstanding JPChest requirement was incorrect and is superseded by this exact bytecode/resource evidence.

## Materialized rule

The converted mod owns `legacyforgebridge/storage-block-rules.json` schema 2.

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
presentationComplete
presentationPending
orientation
sourceFrontTexture
sourceOtherTexture
frontTexture
otherTexture
```

`orientation` is emitted only for the proven first family and is currently:

```text
player_yaw_opposite_quadrant_0_3
```

## Modern gameplay runtime

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

## Modern presentation runtime

For presentation-complete storage blocks:

- placement projects the proven legacy yaw quadrant back into `legacy_meta=0..3`;
- modern blockstate variants rotate one generated cube model for the four proven facings;
- `legacy_meta=4..15` fails closed visually to an all-other-faces cube instead of inventing a front;
- the block model uses the source-proven front/other texture resources;
- the item model preserves the source inventory icon convention (front on legacy side 3 / south);
- no custom block-entity renderer is installed because the source does not prove one for this family.

The legacy metadata carrier remains authoritative so later networking/state work does not silently change the old layout.

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
presentation orientation = player_yaw_opposite_quadrant_0_3
front icon = bamboo:jpchest_f
other icon = bamboo:jpchest_o
```

The generated sidecar maps this source-proven rule to `bamboomod:jpchest` and must mark its presentation complete. Exact-corpus assertions also check the four blockstate rotations, generated item/block model resources, and preservation of both source PNG files.

The exact Bamboo binary remains external and is never committed.

## Generic regression

Synthetic unrelated namespaces cover both halves independently:

- storage regression admits one six-row storage block while an otherwise-similar block without proven comparator semantics fails closed;
- presentation regression admits a normal full cube with the exact yaw/icon shape while an otherwise-similar custom-render-type block fails closed;
- orientation runtime regression pins `SOUTH/WEST/NORTH/EAST -> 0/1/2/3` and rejects vertical directions.

This guards against both Bamboo-name special casing and permissive behavior guessing.

## Completion boundary

This work completes the admitted JPChest storage gameplay and ordinary cube presentation semantics, but it does **not** make the whole Bamboo candidate loader-safe or complete. Remaining work includes, among other gaps:

- other Bamboo BlockEntity families and their persistence/menu/runtime semantics;
- item families not yet migrated end-to-end;
- entities, tracking/DataWatcher and renderers;
- Forge/FML event runtime adapters;
- worldgen/dimension semantics;
- remaining legacy executable classes and their reference closure;
- managed installation and real client/server smoke acceptance once the loader-facing candidate is proven safe.

The candidate therefore remains `PARTIAL` until the wider loader-safety and gameplay gates are satisfied.
