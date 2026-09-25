# Session 2026-09-16 — Block harvest eligibility source proof

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

This slice advances the generic Minecraft Forge 1.7.10 block-drop proof without enabling gameplay
drops. The previous slices already proved ordinary drop stack composition, the Forge
`HarvestDropsEvent` boundary and per-affected-block explosion drop semantics. The next unresolved
normal-harvest boundary is whether a player is eligible to harvest the block at all.

Minecraft Forge 1.7.10 routes the Block-side check through:

```text
Block.canHarvestBlock(player, metadata)
  -> ForgeHooks.canHarvestBlock(block, player, metadata)
```

The Forge decision can depend on the block material, per-metadata harvest tool/level, the held item
and its registered tool classes/levels, and the vanilla player fallback. Forge also exposes
`PlayerEvent.HarvestCheck`, whose result may be changed by source event handlers.

This slice therefore proves only the source-owned portion first. It does not guess the remaining
platform/tool mapping.

## Source hierarchy proof

`LegacyBlockHarvestEligibilityAnalyzer` walks the complete source-owned superclass chain for every
proven registered block. A clean source proof requires the first external superclass to be exactly:

```text
net/minecraft/block/Block
```

Specialized external bases remain fail-closed because their inherited harvest behavior may differ.

The analyzer rejects source-owned overrides of:

```text
canHarvestBlock(EntityPlayer, int)
getHarvestTool(int)
getHarvestLevel(int)
isToolEffective(String, int)
getMaterial()
```

It also detects source calls to either Forge harvest-level mutation form:

```text
setHarvestLevel(String, int)
setHarvestLevel(String, int, int)
```

A detected mutation is not interpreted yet; the block remains source-customized until the exact
per-metadata tool requirement can be materialized safely.

## HarvestCheck event boundary

The existing event analyzer is now queried for:

```text
net/minecraftforge/event/entity/player/PlayerEvent$HarvestCheck
```

A proven handler is recorded with the same registration provenance used for `HarvestDropsEvent`:
handler class/method, event bus, side, priority, receive-canceled flag, and registration site.

If a handler is present, source harvest eligibility remains incomplete with:

```text
harvest-check-event-runtime-pending
```

If event analysis itself is unresolved, absence is not guessed and the blocker is:

```text
harvest-check-event-absence-unproven
```

## Block-drop plan schema v4

`legacyforgebridge/block-drop-plans.json` now distinguishes source harvest proof from complete
platform harvest equivalence.

Root evidence adds:

```text
sourceHarvestCheckEventFree
harvestCheckEventHandlerCount
harvestCheckEventHandlers
harvestEligibilityAnalysisDiagnostics
sourceHarvestEligibilityProofCompletePlans
harvestEligibilityProofCompletePlans
```

Each admitted plan adds:

```text
sourceHarvestEligibilityCustomizationFree
sourceHarvestCheckEventFree
sourceHarvestEligibilityProofComplete
harvestEligibilityProofComplete
harvestEligibilityReasons
```

A plain direct Block with no source harvest customization and no proven `HarvestCheck` listener can
now complete `sourceHarvestEligibilityProofComplete` while `harvestEligibilityProofComplete` remains
false. The existing generic blocker:

```text
harvest-eligibility-proof-pending
```

therefore remains intentionally active.

## Proof separation

`HarvestCheck` affects whether the player is allowed to harvest; it does not redefine the already
proved `getDrops` stack calculation. Consequently a source `HarvestCheck` handler can gate harvest
eligibility while `normalDropProofComplete` and the per-block explosion drop proof remain true when
their independent evidence is complete.

Likewise, `HarvestDropsEvent` continues to gate final stack/chance proof without invalidating the
new source harvest-eligibility proof.

## Deliberate remaining boundary

This slice does **not** yet prove or migrate:

- `Material.isToolNotRequired()` equivalence for converted block materials;
- held-item Forge tool classes and harvest levels;
- the `EntityPlayer.canHarvestBlock` fallback path;
- modern Fabric/Minecraft tool-tag or mining-level mapping;
- silk-touch stacked-item semantics;
- gameplay execution of the block-drop plan.

No modern loot table or Block drop override is installed here.

## Regression coverage

Synthetic JAR tests cover:

- a direct Block hierarchy with no source harvest customization;
- a source-superclass `canHarvestBlock` override;
- a source `getHarvestTool` override;
- a source `setHarvestLevel` mutation;
- a specialized external block superclass;
- a registered `PlayerEvent.HarvestCheck` handler;
- independence between `HarvestCheck` eligibility proof and normal/explosion stack proof;
- independence between `HarvestDropsEvent` stack proof and source harvest-eligibility proof.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.39
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is still not preserved in the current CI workspace. This slice therefore makes no new exact
whole-JAR Bamboo compatibility claim. Bamboo remains `PARTIAL` / not loader-safe until the remaining
shared runtime capabilities, source-class finalization, exact-corpus regression and real-machine
gameplay validation are completed.
