# 2026-09-18 — Variant snowball projectile EntityType registration

## Scope

This slice follows converter revision 2026-09-18.112. The runtime-owned variant-snowball rule sidecar already exists and loads before generated item registration. This revision joins those source-complete rules with source-proven legacy EntityRegistry registration metadata and materializes only the dormant modern projectile EntityType.

Converter revision: 2026-09-18.113.

## Registration proof join

LegacyVariantSnowballRuntimePass now runs after LegacyEntityDataWatcherPass and requires a unique entity registration whose sourceClass exactly matches the sourceProjectileClass already proven by the variant-snowball analyzers.

The join admits only registrations with:

- sourceDataWatcherDefinitionComplete=true;
- a non-negative legacy numeric entity id;
- positive tracking range and update frequency;
- velocityUpdates=true;
- no source-owned DataWatcher entries requiring an unwritten synchronization runtime;
- no premature runtimeImplementationWired claim.

The legacy tracking range is converted from blocks to modern client tracking chunks using the same ceiling rule as the existing plain-entity runtime.

The runtime sidecar schema is now 2 and preserves:

- legacyProjectileRegistryName;
- legacyProjectileNumericId;
- legacyTrackingRangeBlocks;
- modernClientTrackingRangeChunks;
- updateFrequency;
- velocityUpdates;
- the inherited vanilla EntitySnowball dimensions 0.25 x 0.25;
- the existing normalized item-use and impact candidate IR.

## Dormant modern projectile type

LegacyVariantSnowballRuntimeRegistry now registers an EntityType for each admitted projectile rule before generated item registration.

ConvertedLegacyVariantSnowballProjectile extends the current 1.21.11 ThrowableItemProjectile base and intentionally contains no custom impact behavior yet. The generated item path cannot create or launch it in this revision, so registering the type does not open partially implemented gameplay.

The rule root and each rule therefore state:

- projectileEntityTypeRegistrationWired=true;
- itemRuntimeWired=false;
- projectileRuntimeWired=false;
- projectileImpactRuntimeWired=false;
- rendererRuntimeWired=false;
- runtimeImplementationWired=false.

## Regression

A new VariantSnowballRuntimeFixture adds a source-level registerModEntity call without changing the older proof fixtures.

Normal CI covers:

- item/projectile semantic candidate + unique source registration joining;
- legacy entity id/tracking/update/velocity metadata preservation;
- 64 legacy tracking blocks becoming 4 modern tracking chunks;
- inherited 0.25 x 0.25 snowball dimensions;
- missing entity registration failing closed;
- unexpected candidate runtime claims failing closed;
- malformed tracking/dimension/teleport runtime rules failing closed;
- runtime bytecode containing EntityType.Builder registration and the dormant ThrowableItemProjectile constructor.

## Next boundary

The next slice can specialize only item ids that have a fully installed variant-snowball rule and projectile EntityType, then reproduce the proven server-authoritative launch path:

1. preserve the LFB legacy metadata component on the thrown ItemStack;
2. consume one item only outside creative mode;
3. play the source-proven random.bow sound formula;
4. spawn the registered projectile from the player rotation using the current projectile API;
5. keep impact callbacks closed until the metadata variant has been carried into the projectile and synchronized safely.
