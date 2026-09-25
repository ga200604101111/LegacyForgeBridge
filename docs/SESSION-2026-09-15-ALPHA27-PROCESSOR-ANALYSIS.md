# LegacyForgeBridge — Bamboo alpha.27 MillStone analysis slice

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

## Baseline carried forward

JPChest gameplay + source-proven four-direction presentation is complete on commit `0ff426da9b87b34a29aa92f56387c6e11c3c1001`.

GitHub Actions run `#219` passed source hygiene, full build/tests, remap and artifact upload. The produced test JAR was:

- `legacyforgebridge-0.2.0-alpha.27.jar`
- SHA-256 `76ee5a127952e32feaadcc38791ee308017311666f9fd02ce2b7bb4fa0434640`

## Goal of this slice

Establish a generic, non-executing conversion contract for the next Bamboo BlockEntity family before implementing its modern ticking/menu runtime.

The exact-corpus anchor is Bamboo MillStone, but production admission is structural. No Bamboo class or registry name is used to decide whether a processor is supported.

## Source-proven MillStone topology

The exact Bamboo 2.6.8.5 corpus proves:

- registry name: `bambooMillStone`;
- block class: `ruby/bamboo/block/BlockMillStone`;
- TileEntity class: `ruby/bamboo/tileentity/TileEntityMillStone`;
- legacy TileEntity id: `MillStone`;
- inventory size: 3;
- stack limit: 64;
- slot 0: input;
- slots 1 and 2: output-only;
- top slots: `[0]`;
- bottom slots: `[2, 1]`;
- side slots: `[0]`;
- sided insertion delegates to the ordinary slot-validity rule;
- base processing threshold: 400 progress units;
- interaction distance squared: 64.0;
- legacy GUI id: 1;
- server GUI case 1 creates `ContainerMillStone` for the same TileEntity;
- inventory contents are dropped on block removal;
- comparator output uses vanilla 1.7 `Container.calcRedstoneFromInventory` semantics;
- source recipe lookup is `GrindManager.getOutput(ItemStack)`;
- the TileEntity inherits the CoFH `IEnergyHandler` API.

The generated machine rule is deliberately marked `runtimeComplete=false`. This slice does not authorize removal of the legacy machine classes.

## Energy correction

An earlier working note incorrectly concluded that normal energy input could not enter MillStone's accelerated path. Exact bytecode disproves that conclusion.

`TileEntityEnergyUser` defaults `getMaxUseEnergy()` to `getMinUseEnergy()`, but `TileEntityMillStone` overrides:

- minimum use energy = 100;
- maximum accepted/stored energy boundary = 500.

`updateEntity()` can therefore enter its accelerated branch. Its effective progress multiplier is derived from stored energy and the 100-energy minimum, and the same step consumes energy. This behavior must be modeled explicitly in a later runtime slice; it is not replaced by a fixed 400-tick machine here.

The legacy superclass also persists `innerEnergy`, so old-world NBT migration cannot simply discard that field.

## Reachability-safe recipe extraction

`LegacyReachableCallAnalyzer` starts only at actual FML lifecycle roots and follows source-owned call edges. It does **not** globally treat every `<clinit>` as active.

For an admitted processor it locates the source-owned static registration API and reconstructs call arguments conservatively using ASM source frames. Ambiguous arguments stay unresolved.

For exact Bamboo, `CommonProxy.init()` constructs `BambooRecipe`; its constructor reaches `addGrindRecipe()`, which makes exactly 13 calls to public static `GrindManager.addRecipe(...)` overloads.

A synthetic regression includes a dormant class whose static initializer calls the same registration API. That call must not be counted because no FML lifecycle root reaches the class.

## Recipe materialization

The processor recipe sidecar converts source-proven ItemStack identities through the existing 1.7 registry and data-fix pipeline.

Legacy metadata wildcard `32767` remains fail-closed in general. The only expansion admitted here is a Minecraft 1.7 **Block** wildcard: metadata 0..15 is upgraded independently through DFU, invalid variants are discarded, and duplicate modern identities are collapsed. This preserves pre-flattening block families without silently choosing meta 0.

Ore-key processor inputs remain unresolved until a dedicated modern tag/member expansion stage exists.

## Output boundary

This slice adds `legacyforgebridge/single-input-processor-rules.json` containing topology, source provenance and materialized processor recipes.

It does **not** yet implement:

- modern machine BlockEntity ticking;
- modern menu/client progress synchronization;
- sided hopper transfer runtime;
- migration of `grindTime`, `grindItemName`, `grindItemDmg` and `innerEnergy` NBT;
- the source energy acceleration/consumption formula;
- MillStone custom renderer/entity presentation.

Bamboo therefore remains `PARTIAL` after this analysis/materialization slice.
