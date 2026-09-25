# 2026-09-17 — Variant snowball teleport state skeleton proof

## Scope

This slice follows converter revision `2026-09-17.106`, where the Ender-style custom impact helper's random destination formula and delegate became source-proven.

Converter revision: `2026-09-17.107`.

The delegated teleport method is larger than the random wrapper. This slice proves only its target-state transaction skeleton: original-position capture, candidate-position assignment, guarded exact rollback with false return, and a separate guarded success path returning true. It deliberately does not claim that the success flag itself is computed from the correct ground/collision/liquid conditions yet.

## Bounded state proof

`LegacyVariantSnowballTeleportStateAnalyzer` starts only from random teleport wrappers already admitted by `LegacyVariantSnowballTeleportWrapperAnalyzer` and follows the exact delegated `(EntityLiving, double, double, double) -> boolean` method.

It requires:

1. the target's original X/Y/Z positions are each read and saved into three distinct double locals;
2. all three saves occur before the candidate writes admitted by this proof;
3. candidate X/Y/Z are written directly from the wrapper's three coordinate parameters;
4. a boolean/int success local guards the rollback path with `IFNE success`;
5. the rollback path calls target `setPosition(savedX, savedY, savedZ)` using the exact three saved locals, then returns false;
6. the guarded success target reaches an explicit true return.

The proof is intentionally transactional rather than semantic: it proves exact state capture/restoration and control-flow separation, but not why the success flag becomes true.

## Fail-closed regression

The unrelated-namespace synthetic fixture has a negative variant where rollback Y uses the candidate Y parameter instead of the saved Y local. That variant still contains `setPosition(...)` and `return false`, but the rollback proof is rejected because all three original coordinates are not restored exactly.

This prevents later runtime work from treating the mere presence of a rollback-looking call as proof of source-equivalent failure semantics.

## Bamboo exact-corpus target

The checksum-pinned Bamboo `EntityDirtySnowball` Ender teleport core must prove:

- original target X/Y/Z capture;
- candidate coordinate assignment from the random-wrapper delegate parameters;
- exact guarded rollback and false return;
- separate guarded true-return path.

Production analysis contains no Bamboo class-name or registry-name special case.

## Sidecar schema 7

`legacyforgebridge/variant-snowball-proof.json` now reports `schemaVersion=7`.

For a custom teleport variant whose wrapper and state skeleton are proven, the per-variant IR advances to:

`RANDOM_TELEPORT_STATE_SKELETON_PROVEN`

and records:

- `savedPositionProven=true`;
- `candidateAssignmentProven=true`;
- `guardedRollbackFalseProven=true`;
- `successTrueReturnProven=true`.

The family records `teleportStateSkeletonProven` / `teleportStateSkeletonCount`; the root reports `teleportStateSkeletonFamilies`.

`selectorSpecificImpactSemanticsComplete`, `impactSemanticsComplete`, `impactCompilerWired`, and `runtimeImplementationWired` remain false for Bamboo because the success predicate, ground search, collision/liquid safety and portal presentation are not yet compiled.

## Next boundary

The next gameplay-safety slice must bind the success flag to the source conditions instead of merely recognizing its transaction shell:

1. floor candidate coordinates;
2. require `world.blockExists`;
3. perform the downward Y search;
4. stop only on non-air movement-blocking ground or Y=0;
5. call target `setPosition` only after ground is found;
6. set success only when collision boxes are empty and the target bounding box is not liquid.

Only after that proof can the teleport gameplay core be considered complete. The 128 portal particles and portal sounds remain a separate presentation proof.
