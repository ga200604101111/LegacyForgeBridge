# 2026-09-17 — Variant snowball random-teleport wrapper proof

## Scope

This slice follows converter revision `2026-09-17.105`, where selector-specific DirtySnowball switch edges became source-proven. The Ender selector still points at a custom helper whose internals were intentionally uncompiled.

Converter revision: `2026-09-17.106`.

This slice proves only the outer random-destination wrapper. It does not yet prove the collision/ground-search/rollback implementation of the delegated teleport routine.

## Bounded wrapper

`LegacyVariantSnowballTeleportWrapperAnalyzer` starts only from selector-effect edges already classified as `CUSTOM_HELPER_UNCOMPILED`.

For the custom `(EntityLiving) -> boolean` helper it requires the exact source family:

- X destination = projectile `posX + (rand.nextDouble() - 0.5) * 32.0`;
- Y destination = projectile `posY + (rand.nextInt(8) - 4)`;
- Z destination = projectile `posZ + (rand.nextDouble() - 0.5) * 32.0`;
- all random calls use the projectile's inherited `Random` field;
- the three computed double locals plus the same target are passed to a projectile-owned `(EntityLiving, double, double, double) -> boolean` helper;
- that delegate result is returned directly.

The proven horizontal expression therefore corresponds to a ±16 block offset from the projectile position; the vertical expression corresponds to integer offsets -4 through +3, matching the source `nextInt(8)-4` behavior.

A synthetic variant that changes the horizontal multiplier from `32.0` to `16.0` fails the X/Z proof and cannot be promoted merely because the delegate call still exists.

## Bamboo target

The checksum-pinned Bamboo family maps selector `ender` (selector id / legacy metadata 6) to the custom random teleport helper. The exact-corpus regression requires the wrapper constants, RNG provenance and exact lower-level teleport delegate to be source-proven.

No Bamboo names are used by production analysis.

## Sidecar schema 6

For a selector whose custom impact helper passes this proof, the per-variant IR changes from:

`CUSTOM_HELPER_UNCOMPILED`

to:

`RANDOM_TELEPORT_WRAPPER_PROVEN`

and records:

- `sourceImpactHelper`;
- `sourceTeleportHelper`;
- `horizontalRandomRadius=16.0`;
- `verticalRandomRadius=4`.

The family records `randomTeleportWrapperProven` / `randomTeleportWrapperCount`; the root reports `randomTeleportWrapperFamilies`.

This does **not** make `selectorSpecificImpactSemanticsComplete` true. The lower-level teleport core remains uncompiled, so `impactSemanticsComplete=false`, `impactCompilerWired=false`, and `runtimeImplementationWired=false` remain correct.

## Next boundary

The delegated teleport helper still has to prove:

1. temporary target-position assignment and exact saved-position rollback;
2. integer floor conversion of target coordinates;
3. downward ground search until a non-air movement-blocking block or Y=0;
4. target repositioning only after ground is found;
5. collision-box emptiness and non-liquid checks;
6. false return with exact rollback on failure;
7. successful portal particle/sound presentation and true return.

Those are independent gameplay-safety and presentation facts and should remain fail-closed until source bytecode proves them.
