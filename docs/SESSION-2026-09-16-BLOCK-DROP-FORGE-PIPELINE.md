# Session 2026-09-16 — Forge block-drop pipeline proof

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

This slice tightens the generic Minecraft Forge 1.7.10 block-drop proof against the actual Forge
patch pipeline before any generic Block drop runtime is enabled.

The previous normal-drop plan gate already rejected source-owned overrides of `getDrops`,
`dropBlockAsItemWithChance`, `harvestBlock`, silk-touch callbacks and stacked-block creation.
Cross-checking the Forge 1.7.10 `Block.java.patch` exposed one additional dispatch point:

```text
getDrops(world, x, y, z, metadata, fortune)
  -> quantityDropped(metadata, fortune, random)
  -> getItemDropped(metadata, random, fortune)
  -> damageDropped(metadata)
```

Forge adds `quantityDropped(int metadata, int fortune, Random random)` above the older vanilla
quantity callbacks. A source mod can override that overload without overriding the older
`quantityDropped(Random)` or `quantityDroppedWithBonus(int, Random)`. Such a block must therefore
not inherit a fabricated vanilla quantity result.

`LegacyBlockDropPlanCompiler` now rejects source-owned overrides of the exact descriptor:

```text
quantityDropped(IILjava/util/Random;)I
```

The check continues to walk the complete source-owned superclass chain.

## HarvestDropsEvent boundary

Forge 1.7.10 also patches `dropBlockAsItemWithChance` so that it:

1. obtains the complete stack list through `getDrops(...)`;
2. calls `ForgeEventFactory.fireBlockHarvesting(...)`;
3. permits `HarvestDropsEvent` handlers to mutate the stack list and drop chance;
4. applies the resulting chance to each returned stack.

The silk-touch branch of `harvestBlock` also invokes the same Forge harvesting event.

For that reason `legacyforgebridge/block-drop-plans.json` is now schema version 2 and distinguishes
the pre-event stack plan from the final normal-drop result.

For each admitted block it records:

- `preHarvestEventDropProofComplete`;
- `forgeHarvestEventProofComplete`;
- `normalDropProofComplete`;
- `sourceDropPathOverrideFree`;
- `runtimeComplete`.

The sidecar also records proven source registrations for:

```text
net/minecraftforge/event/world/BlockEvent$HarvestDropsEvent
```

including handler class/method, bus, side, priority, receive-canceled flag and registration
provenance.

If a source `HarvestDropsEvent` handler is proven, the pre-event stack plan remains available, but
the final normal-drop proof is false and the runtime blocker is:

```text
harvest-drops-event-runtime-pending
```

If event analysis itself is unresolved, absence is not guessed; the blocker is:

```text
harvest-drops-event-absence-unproven
```

Only a clean analysis with no source HarvestDropsEvent handler is currently marked
`sourceHarvestDropsEventFree=true`.

## Exact Forge explosion observation

The Forge 1.7.10 `Explosion.java.patch` preserves the legacy explosion drop call with:

```text
chance = 1.0F / explosionSize
fortune = 0
```

and changes destruction to `Block.onBlockExploded(...)`.

That is useful evidence, but this slice intentionally keeps
`explosion-drop-chance-proof-pending`: final explosion compatibility still needs the source
`canDropFromExplosion` / explosion callback surface and the harvesting-event interaction to be
closed before generic drop runtime can claim equivalent behavior.

## Regression coverage

Synthetic JAR tests now cover:

- a source override of Forge's metadata/fortune-sensitive quantity overload, which must fail closed;
- the previous direct/full-drop/harvest/silk callback gates;
- an event-free plain Block, which may retain a final normal-drop proof;
- a proven source `HarvestDropsEvent` listener, where the pre-event plan remains but final
  normal-drop proof is gated.

No loot-table or modern Block drop override is installed by this slice.

## Converter identity

This behavior changes conversion evidence and therefore bumps:

```text
CONVERTER_REVISION = 2026-09-16.37
```

The public mod version remains `0.2.0-alpha.27`.

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is not preserved in the current CI workspace. This slice therefore does **not** claim new exact
whole-JAR Bamboo coverage.

Bamboo candidate status remains `PARTIAL` / not loader-safe until the remaining necessary source
semantics, class finalization and exact-corpus/runtime validation are completed.
