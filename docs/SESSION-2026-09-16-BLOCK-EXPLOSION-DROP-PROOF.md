# Session 2026-09-16 — Per-block explosion drop proof

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

This slice closes the **legacy per-affected-block explosion drop proof** for ordinary source-proven
Block plans without claiming that the whole explosion system is migrated.

The exact Minecraft/Forge 1.7.10 path is:

```text
if (block.canDropFromExplosion(explosion)) {
    block.dropBlockAsItemWithChance(world, x, y, z, metadata,
                                    1.0F / explosionSize, 0);
}
block.onBlockExploded(world, x, y, z, explosion);
```

Forge's patched `dropBlockAsItemWithChance` then routes through `getDrops(...)` and
`HarvestDropsEvent`, so the final explosion drop proof additionally depends on the harvest-event
boundary completed in the previous slice.

## New source proof

`LegacyBlockExplosionAnalyzer` walks the complete source-owned superclass chain for every proven
registered block and keeps two surfaces independent.

### Explosion drop eligibility

It detects source overrides of:

```text
canDropFromExplosion(Explosion)
func_149659_a(Explosion)
```

The direct Minecraft 1.7.10 `Block` default is true. That default is admitted only when the first
external superclass is exactly `net/minecraft/block/Block`. Specialized external bases such as
`BlockTNT` remain fail-closed because their inherited semantics may differ.

### Explosion destruction callbacks

It independently detects source overrides of:

```text
onBlockExploded(World, x, y, z, Explosion)
onBlockDestroyedByExplosion(World, x, y, z, Explosion)
func_149723_a(World, x, y, z, Explosion)
```

A custom destruction callback does not invalidate an already-proven per-block drop chance because
the legacy explosion evaluates the drop path first. It does, however, remain an explicit runtime
blocker for full explosion destruction equivalence.

## Block drop plan schema v3

`legacyforgebridge/block-drop-plans.json` now records:

```text
legacyExplosionChanceMode = inverse_explosion_size_1_7_10
legacyExplosionFortune = 0
sourceExplosionDropEligibilityProofComplete
sourceExplosionDestructionOverrideFree
explosionDropProofComplete
explosionDropEligibilityReasons
explosionDestructionReasons
```

Root counters include:

```text
explosionDropProofCompletePlans
sourceExplosionDestructionOverrideFreePlans
```

For an event-free direct Block with no source explosion override, the old generic blocker
`explosion-drop-chance-proof-pending` is removed. The remaining generic normal-drop blockers are:

```text
harvest-eligibility-proof-pending
silk-touch-stacked-item-proof-pending
```

If source overrides `canDropFromExplosion`, the plan remains valid for ordinary harvesting but gains:

```text
explosion-can-drop-callback-runtime-pending
```

If source overrides an explosion destruction callback, explosion drop proof may still complete, but
the plan gains:

```text
explosion-destruction-callback-runtime-pending
```

If a source `HarvestDropsEvent` handler exists, the exact `1/explosionSize` base chance is known but
the **final** explosion drop proof remains false because the event may mutate the chance/list.

## Deliberate boundary

This is not a claim that all explosion semantics are converted. In particular, this slice does not
yet migrate or prove:

- Forge `ExplosionEvent` effects on the affected-block set;
- arbitrary source `onBlockExploded` / `onBlockDestroyedByExplosion` behavior;
- modern runtime execution of the legacy drop plan;
- complete harvest/silk runtime.

No modern loot table or Block drop override is installed here.

## Regression coverage

Synthetic tests now verify:

- direct Block defaults prove explosion drop eligibility;
- `canDropFromExplosion` inherited from a source-owned superclass fails closed;
- custom `onBlockExploded` and `onBlockDestroyedByExplosion` are tracked separately from drop eligibility;
- a specialized external superclass such as `BlockTNT` is not guessed;
- sidecar schema v3 closes explosion drop proof for event-free plain Blocks;
- a custom `canDropFromExplosion` blocks only explosion drops, not the ordinary normal-drop plan;
- a custom destruction callback leaves the per-block explosion drop proof intact but remains a destruction blocker;
- `HarvestDropsEvent` still gates the final explosion drop result.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.38
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is not preserved in the current CI workspace. This slice therefore does **not** claim new exact
whole-JAR Bamboo coverage. Bamboo remains `PARTIAL` / not loader-safe pending the remaining shared
runtime capabilities, source-class finalization, exact-corpus regression and real-machine gameplay
validation.
