# 2026-09-18 — Variant snowball synchronized metadata carrier

## Scope

This slice follows converter revision 2026-09-18.113. A dormant modern projectile EntityType now exists from source-proven registerModEntity metadata, but no generated item can launch it.

Converter revision: 2026-09-18.114.

The purpose of this slice is to establish the exact metadata transport boundary needed before any impact behavior is opened.

## Reuse the modern projectile ItemStack channel

Minecraft 1.21.11 ThrowableItemProjectile already synchronizes and persists its carried ItemStack. LegacyForgeBridge already stores pre-flattening subtype metadata in the network-synchronized legacyforgebridge:legacy_meta data component.

ConvertedLegacyVariantSnowballProjectile therefore adds a launch-form constructor that passes the complete source ItemStack into the modern ThrowableItemProjectile constructor rather than creating a parallel metadata watcher.

The projectile exposes:

- legacyMetadata(): reads LegacyStackComponents.get(getItem());
- variant(): resolves the normalized runtime variant by that metadata value.

Unknown metadata remains a null variant, matching the source map lookup's ability to produce a null selector rather than inventing a fallback effect.

## Runtime readiness flags

The runtime sidecar root and admitted rule now state:

- projectileEntityTypeRegistrationWired=true;
- projectileItemStackCarrierWired=true;
- legacyMetadataSyncWired=true;
- itemRuntimeWired=false;
- projectileRuntimeWired=false;
- projectileImpactRuntimeWired=false;
- rendererRuntimeWired=false;
- runtimeImplementationWired=false.

The registry refuses sidecars missing the new carrier/synchronization guarantees.

## Regression

Normal CI covers:

- the launch-form projectile constructor accepting Level, LivingEntity, ItemStack and the source-proven rule;
- direct delegation to the modern ThrowableItemProjectile ItemStack constructor;
- legacyMetadata() reading the synchronized carried ItemStack through LegacyStackComponents;
- runtime sidecar and registry gating for the new readiness flags.

## Next boundary

With metadata transport closed, impact behavior can now be implemented against projectile.variant() without guessing or adding a second synchronization protocol:

1. exact source base damage;
2. three potion branches;
3. null/ordinary no-op selector behavior;
4. Ender random teleport safety core and portal presentation;
5. snowball poof and server termination.

Only after impact is complete should the generated item launch path be enabled.
