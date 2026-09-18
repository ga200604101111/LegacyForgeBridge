# 2026-09-18 — Variant snowball playable runtime closure

## Scope

This slice follows converter revision 2026-09-18.115. Projectile registration, synchronized legacy metadata and the complete source-proven impact program are already wired.

Converter revision: 2026-09-18.116.

This revision closes the remaining item-launch and client-presentation boundaries for admitted metadata-indexed legacy ItemSnowball families.

## Specialized generated item

GeneratedModSupport now checks the already-loaded LegacyVariantSnowballRuntimeRegistry by exact modern item id before selecting an item runtime.

Only ids with a complete runtime rule receive ConvertedLegacyVariantSnowballItem. The generic content kind remains unchanged, so this is not a Bamboo/modid/class-name special case.

The specialized item extends ConvertedBehaviorItem so unrelated source-compiled hooks remain available, while its proven right-click launch callback is replaced by the dedicated source-equivalent implementation.

Inherited ItemSnowball stack size is fixed at 16 for the admitted family.

## Source-equivalent launch

ConvertedLegacyVariantSnowballItem reproduces the proven 1.7 shell:

- copies one carried item before source-equivalent consumption so the projectile retains the exact legacy_meta component even for the last stack item;
- bypasses consumption only when Player abilities instabuild is true, matching the old creative-mode guard;
- decrements exactly one item otherwise;
- gates both sound and projectile spawn to ServerLevel;
- reproduces the exact 0.4 / (rand * 0.4 + 0.8) pitch formula at volume 0.5;
- maps legacy random.bow snowball throwing presentation to SoundEvents.SNOWBALL_THROW;
- uses Projectile.spawnProjectileFromRotation with vanilla snowball power 1.5 and uncertainty 1.0;
- constructs ConvertedLegacyVariantSnowballProjectile with the one-count carried stack and exact normalized rule.

No modern ITEM_USED stat is added because the proved 1.7 source launch shell did not contain that side effect.

## Client renderer

ConvertedVariantSnowballPresentationRuntime registers the already-created projectile EntityType with the vanilla ThrownItemRenderer.

GeneratedModEntrypointPass invokes this runtime from the converted mod client initializer.

## Complete runtime admission

The runtime sidecar and registry now require all of the following:

- projectileEntityTypeRegistrationWired=true;
- projectileItemStackCarrierWired=true;
- legacyMetadataSyncWired=true;
- itemRuntimeWired=true;
- projectileRuntimeWired=true;
- projectileImpactRuntimeWired=true;
- rendererRuntimeWired=true;
- runtimeImplementationWired=true.

Any older partial sidecar therefore fails closed instead of silently creating a half-playable family.

## Regression

Normal CI covers:

- exact-id specialized item selection in GeneratedModSupport;
- the launch ItemStack copy and creative guard;
- one-count consumption;
- shared legacy-style pitch RNG;
- modern snowball sound;
- Projectile.spawnProjectileFromRotation and the specialized projectile constructor;
- complete runtime sidecar/registry flags;
- ThrownItemRenderer registration;
- generated client bootstrap of the presentation runtime.

## Next boundary

After this revision is green, the variant-snowball runtime itself is feature-complete for the currently admitted source family. The next work should be retirement/readiness:

1. prove no unsupported references from the legacy item/projectile/selector cohort are still needed;
2. strip or isolate source registration paths already replaced by the modern runtime;
3. add exact-corpus end-to-end assertions that the Bamboo snowball family reaches complete runtime admission;
4. only then include this cohort in source-class retirement and candidate loader-safety accounting.
