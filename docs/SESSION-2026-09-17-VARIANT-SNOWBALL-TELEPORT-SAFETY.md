# 2026-09-17 — Variant snowball teleport gameplay-safety proof

## Scope

This slice follows converter revision `2026-09-17.107`, where the source-mapped random teleport core had a proven target-state transaction skeleton: original coordinates are saved, candidate coordinates are assigned, failure rolls back exactly, and success/failure return paths are distinct.

Converter revision: `2026-09-17.108`.

This slice proves how the teleport success flag is derived from the legacy ground-search and collision/liquid safety program. It does not yet prove the success-only portal particle/sound presentation and it does not generate a modern projectile runtime.

## Bounded gameplay-safety proof

`LegacyVariantSnowballTeleportSafetyAnalyzer` starts only from teleport cores already admitted by `LegacyVariantSnowballTeleportStateAnalyzer`.

For an admitted core it requires all of the following to be tied to the exact rollback failure join proved by the state skeleton:

1. candidate X/Y/Z are floored through the 1.7.10 `MathHelper.floor_double` family;
2. `World.blockExists(x,y,z)` guards entry to the search and otherwise reaches the rollback failure join;
3. a zero-initialized ground flag drives a loop while Y is above zero;
4. each iteration reads the block immediately below the candidate position;
5. ground is accepted only when the block is not `Blocks.air` and its material `blocksMovement()`;
6. otherwise target Y and the floored Y local both decrement by exactly one and the loop repeats;
7. only a proven ground flag permits `setPosition(currentX,currentY,currentZ)`;
8. a non-empty collision list reaches the same rollback failure join;
9. any liquid in the target bounding box reaches the same rollback failure join;
10. the already-proven final success flag is set to true only after the collision and non-liquid gates and before the rollback guard.

MCP and SRG names are both admitted for the bounded 1.7.10 calls. Production logic contains no Bamboo class or registry-name special case.

## Regression

Normal CI includes an unrelated-namespace synthetic teleport core with the exact safety program. It also includes an inverted liquid condition; that fixture preserves the independent floor, block, ground, reposition, collision and flag-write facts but must fail the non-liquid proof and aggregate gameplay-safety proof.

The checksum-pinned exact-corpus regression requires Bamboo's `snowball` / `ender` selector (id 6) to prove the complete gameplay-safety core from the supplied Bamboo JAR.

Historical Bamboo source matches this bounded family: it checks `blockExists`, searches downward for a non-air movement-blocking block, repositions only after ground is found, requires `getCollidingBoundingBoxes(...).size() == 0`, rejects `isAnyLiquid(...)`, and otherwise rolls back to the saved coordinates.

## Sidecar schema 8

`legacyforgebridge/variant-snowball-proof.json` now reports:

- family-level `teleportGameplaySafetyProven` and `teleportGameplaySafetyCount`;
- root `teleportGameplaySafetyFamilies`;
- per-variant `RANDOM_TELEPORT_GAMEPLAY_SAFETY_PROVEN` when the bounded core is complete;
- explicit proof facts for floor conversion, block-existence gating, downward ground search, guarded reposition, collision emptiness, non-liquid gating, and final success-flag binding;
- `teleportPresentationComplete=false` for this slice.

`selectorSpecificImpactSemanticsComplete`, `impactSemanticsComplete`, `impactCompilerWired`, and `runtimeImplementationWired` remain closed for Bamboo's Ender selector because success-only portal particles/sounds and the modern runtime are not yet compiled.

## Next boundary

The remaining source behavior in Bamboo's teleport helper after the gameplay-safety core is the success presentation:

- exactly 128 `portal` particles interpolated from the saved position to the accepted target position with source-randomized offsets and velocities;
- a portal sound at the original coordinates;
- a portal sound associated with the projectile/entity path;
- true return after those success-only effects.

That presentation should be proved separately before the Ender selector can be considered source-semantics complete and before runtime code generation opens.
