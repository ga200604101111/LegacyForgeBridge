# 2026-09-17 — Variant snowball common impact proof

## Scope

This slice follows converter revision `2026-09-17.103`, where metadata-to-selector routing became source-proven through the selector-map population program.

Converter revision: `2026-09-17.104`.

The next boundary is projectile impact behavior. Bamboo's `EntityDirtySnowball` has a selector-independent shell around a selector-specific switch. This slice proves only that common shell. It does not compile the `ender`, `poison`, `confusion` or `heal` branches.

## Source shape

The historical Bamboo impact callback has four common behaviors:

1. a null selector kills the projectile and returns;
2. a non-null entity hit receives thrown-projectile damage using the selector's proven base-damage getter;
3. every impact emits exactly eight `snowballpoof` particles at the projectile position with zero motion;
4. the projectile is removed only on the server side after the particle path.

After the base damage call, living targets enter a selector switch. That switch remains outside this slice.

## Bounded proof

`LegacyVariantSnowballImpactAnalyzer` starts only from families already admitted by `LegacyVariantSnowballAnalyzer` and requires a unique stored selector field on the projectile.

It proves the four common behaviors independently:

### Selector-null guard

The callback must contain the direct shape:

- load `this.selector`;
- branch when non-null;
- call the legacy `kill` method;
- return before the normal impact body.

### Selector base damage

The callback must:

- load `this.selector`;
- call the already-proven selector damage getter and store that byte value;
- guard `MovingObjectPosition.entityHit` against null;
- call the legacy thrown-damage factory with `this` and the projectile thrower;
- pass the same stored selector damage value, converted to float, to `attackEntityFrom` on that entity hit.

This does not infer any selector-specific effect from the later switch.

### Particle loop

The callback must contain a canonical zero-based loop with an exclusive upper bound of exactly `8`, incrementing by one and jumping back to the loop comparison. Inside that loop it must call the legacy world particle API with:

- particle id `snowballpoof`;
- `this.posX`, `this.posY`, `this.posZ`;
- three zero motion components.

A seven-particle synthetic variant is rejected while the independently proven damage and termination facts remain available.

### Server termination

The callback must read the projectile world, check the legacy `isRemote` field, skip removal on the client, and call the legacy `setDead` method only on the server path.

An otherwise-identical synthetic projectile without that gate is rejected.

## Sidecar schema 4

`legacyforgebridge/variant-snowball-proof.json` now reports `schemaVersion=4` and, per admitted family:

- `selectorNullImpactGuardProven`;
- `selectorBaseDamageAttackProven`;
- `snowballPoofLoopProven`;
- `serverTerminationProven`;
- `commonImpactSemanticsProven`;
- `commonImpactBlockers` when the full common shell is not proven;
- `selectorSpecificImpactSemanticsComplete=false`;
- `impactSemanticsComplete=false`;
- `runtimeImplementationWired=false`.

The root reports `commonImpactFamilies`.

`impactCompilerWired` remains false. No generated 1.21.11 projectile behavior is admitted by this slice.

## Regression

Normal CI covers:

- a generic unrelated-namespace family with the complete common impact shell;
- the same family with a seven-particle loop, which fails only the particle proof and therefore fails the aggregate common-impact proof;
- the same family with an ungated `setDead`, which fails server termination and the aggregate common-impact proof;
- sidecar materialization that exposes common-impact facts without opening selector-specific impact semantics or runtime wiring.

A checksum-pinned exact-corpus test requires Bamboo's `snowball` family to prove all four common impact facts when the exact Bamboo corpus is supplied.

## Remaining boundary

Bamboo's selector switch still contains materially different semantics:

- `ender` invokes the custom random-teleport path;
- `poison` applies poison;
- `confusion` applies confusion;
- `heal` applies regeneration;
- other selectors have no additional target effect beyond base damage.

Those selector-to-effect edges must be proven from the compiled enum-switch dispatch before any variant `impactEffect` may move away from `UNCOMPILED`. Teleport internals are a separate, larger behavior proof even after the switch mapping is known.
