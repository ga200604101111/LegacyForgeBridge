# 2026-09-18 — Variant snowball impact runtime

## Scope

This slice follows converter revision 2026-09-18.114. The projectile EntityType and synchronized legacy metadata carrier are already present, while generated items still cannot launch the projectile.

Converter revision: 2026-09-18.115.

This revision implements the complete source-proven impact program without opening item launch.

## Common impact shell

ConvertedLegacyVariantSnowballProjectile now reads the synchronized legacy metadata from its carried ItemStack and resolves the normalized source variant.

For a non-null variant, the server-side hit path:

- applies the source-proven base damage through the modern thrown-projectile DamageSource;
- applies selector-specific effects only when the target is a Mob, preserving the old EntityLiving guard and intentionally excluding players;
- emits exactly eight ITEM_SNOWBALL particles at the projectile position;
- discards the projectile on the authoritative server path.

Unknown metadata remains fail closed: the projectile is discarded without inventing a selector or effect.

## Potion variants

The normalized legacy names map only to the three source-proven modern effects:

- poison -> MobEffects.POISON;
- confusion -> MobEffects.NAUSEA;
- regeneration -> MobEffects.REGENERATION.

Duration and amplifier are copied from the normalized source proof.

## Random teleport variant

The Ender selector reproduces the proven wrapper/core semantics:

- X/Z candidate range: target position plus (randomDouble - 0.5) * 32;
- Y candidate: target position plus nextInt(8) - 4;
- legacy World#blockExists Y domain [0,256) plus loaded-chunk gate;
- downward solid-ground search using non-air blocks that block motion;
- candidate reposition only after a ground is found;
- block and entity collision rejection against the target bounding box while excluding the projectile;
- liquid rejection;
- exact rollback to the saved position on failure.

The successful presentation path emits 128 portal particles using the proven saved-to-target interpolation and per-particle random position/velocity formulas, then plays the Enderman portal sound at both the saved origin and final entity position.

## Runtime flags

The runtime sidecar and registry now require:

- projectileEntityTypeRegistrationWired=true;
- projectileItemStackCarrierWired=true;
- legacyMetadataSyncWired=true;
- projectileRuntimeWired=true;
- projectileImpactRuntimeWired=true;
- itemRuntimeWired=false;
- rendererRuntimeWired=false;
- runtimeImplementationWired=false.

The last three remain closed because the converted item still cannot launch the projectile and the projectile renderer is not registered yet.

## Regression

Normal CI covers the runtime sidecar/registry admission flags plus bytecode evidence for:

- thrown DamageSource creation;
- Entity.hurtServer;
- Mob-only potion application;
- all three modern potion holders;
- block/entity collision and liquid gates;
- particle emission;
- portal sound emission;
- projectile discard.

## Next boundary

Once this slice is green, the next slice can safely make the generated item playable:

1. specialize only exact item ids that have a fully installed variant-snowball rule;
2. preserve the carried ItemStack and legacy metadata;
3. spawn the registered projectile from player rotation;
4. reproduce the source-proven creative consumption guard and random.bow pitch formula;
5. register the vanilla ThrownItemRenderer;
6. then open retirement of the old item/projectile cohort.
