# Session 2026-09-16 — Forge default silk-touch proof

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

This slice separates Minecraft/Forge 1.7.10 silk-touch behavior from the ordinary non-silk block-drop plan so a silk-only customization no longer destroys an otherwise valid normal-drop proof.

The preceding harvest-eligibility and Block Material constructor-provenance work is preserved. Source-owned harvest callbacks, `setHarvestLevel` mutations, `PlayerEvent.HarvestCheck`, and raw constructor Material provenance remain independent inputs to the later platform harvest-equivalence stage.

No modern loot table or Block drop runtime is enabled here.

## Exact 1.7 / Forge behavior used

Forge's context-aware `canSilkHarvest(world, player, x, y, z, metadata)` stores metadata and delegates to the old no-argument method. The patched base Block no-argument result is effectively:

```text
renderAsNormalBlock() && !hasTileEntity(metadata)
```

Forge's base `hasTileEntity(metadata)` returns whether the Block implements `ITileEntityProvider`.

Vanilla base `createStackedBlock(metadata)` obtains the registered BlockItem, emits count 1, and preserves metadata only when that Item reports `getHasSubtypes() == true`. A default `ItemBlock` constructor does not set that subtype flag, so its inherited Item default is false unless source code later mutates the Item.

## New analyzer

`LegacyBlockSilkTouchAnalyzer` walks the complete source-owned Block hierarchy and proves two independent surfaces.

### Eligibility

It rejects source-owned overrides of:

```text
canSilkHarvest()
canSilkHarvest(World, EntityPlayer, x, y, z, metadata)
renderAsNormalBlock()
hasTileEntity(metadata)
```

The default eligibility is admitted only when the first external superclass is exactly `net.minecraft.block.Block`.

A direct source implementation of `ITileEntityProvider` is not treated as unknown: the base Forge rule is proven to disable silk harvest for all metadata values. Source interfaces are traversed recursively; an unresolved non-Java external interface remains fail-closed because it could itself extend `ITileEntityProvider`.

### Stacked item

It rejects source-owned `createStackedBlock(metadata)` overrides and custom ItemBlock classes.

For the default GameRegistry ItemBlock path, the base stacked item is proven as:

```text
item = self BlockItem
count = 1
legacy damage = 0
```

only when no source-wide unscoped `Item.setHasSubtypes(boolean)` mutation can affect a BlockItem. The mutation scan uses ASM source frames. Receivers proven to be `this` of a source Item subclass, a newly-created ordinary source Item, or a source-proven ordinary registered Item field are scoped away; unresolved receivers remain fail-closed. In particular:

```text
Item.getItemFromBlock(block).setHasSubtypes(true)
```

keeps default BlockItem stacked metadata unproven.

## Normal-drop plan correction

`LegacyBlockDropPlanCompiler` is now explicitly the ordinary non-silk route compiler. Silk-only source callbacks such as `canSilkHarvest(...)` and `createStackedBlock(...)` no longer make a normal-drop plan incomplete; they are evaluated only by the silk proof surface.

Callbacks that can replace the ordinary route itself still fail closed:

```text
getDrops(...)
quantityDropped(metadata, fortune, Random)
dropBlockAsItem(...)
dropBlockAsItemWithChance(...)
harvestBlock(...)
```

## Block-drop sidecar schema v5

`legacyforgebridge/block-drop-plans.json` preserves all schema-v4 harvest-eligibility and explosion evidence and adds:

```text
silkTouchEligibilityProofComplete
silkTouchEligible
silkTouchStackProofComplete
silkTouchProofComplete
silkTouchEligibilityReasons
silkTouchStackReasons
silkTouchStack
silkTouchAnalysisDiagnostics
silkTouchProofCompletePlans
```

A fully proven event-free direct Block now carries a silk stack of self BlockItem, quantity 1, legacy damage 0. The previous broad blocker `silk-touch-stacked-item-proof-pending` is removed.

Silk-specific fail-closed blockers are:

```text
silk-touch-source-proof-missing
silk-touch-eligibility-runtime-pending
silk-touch-stacked-item-runtime-pending
```

`HarvestDropsEvent` remains a separate final-result gate because Forge fires it on the silk branch as well. `PlayerEvent.HarvestCheck` remains independent: it gates whether the player may harvest, but does not erase the proven normal/silk/explosion stack calculation.

## Remaining broad blocker

For a plain event-free direct Block with source-clean harvest callbacks and default explosion/silk semantics, the broad generic block-drop plan is reduced to:

```text
harvest-eligibility-proof-pending
```

The repository now also has exact raw Block Material constructor provenance, but material/tool/player equivalence still needs the platform-side mapping before gameplay drop runtime can be enabled.

## Regression coverage

Synthetic tests cover direct Block silk defaults, `ITileEntityProvider`, source render/tile/silk/stack overrides, unscoped BlockItem subtype mutation, retention of normal plans for silk-only customizations, preservation of source harvest proof, HarvestDropsEvent and HarvestCheck event boundaries, and coexistence with explosion proof.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.41
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar` (`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`) is not preserved in the current CI workspace. This slice therefore does **not** claim new exact whole-JAR Bamboo coverage. Bamboo remains `PARTIAL` / not loader-safe pending the remaining shared runtime capabilities, source-class finalization, exact-corpus regression and real-machine validation.
