# 2026-09-17 — Variant snowball teleport presentation proof

## Scope

This slice follows converter revision `2026-09-17.108`, where Bamboo's Ender snowball teleport gameplay core became source-proven through exact floor conversion, ground search, guarded reposition, collision emptiness, non-liquid rejection, success binding and rollback.

Converter revision: `2026-09-17.109`.

This slice proves the remaining success-only portal presentation. When combined with the selector switch, common impact shell and gameplay-safety proof, the source semantics of the admitted random-teleport selector are complete. Modern runtime generation remains closed.

## Bounded presentation proof

`LegacyVariantSnowballTeleportPresentationAnalyzer` starts only from teleport cores already admitted by `LegacyVariantSnowballTeleportSafetyAnalyzer`.

It first re-identifies the exact `.107` rollback guard and enters only the guarded success branch. It then requires:

1. a loop count local initialized to exactly `128` and an index initialized to zero;
2. an exclusive `index < count` loop, `+1` increment and back-edge;
3. interpolation fraction `index / (count - 1.0)`;
4. three velocity locals, each exactly `(projectile.rand.nextFloat() - 0.5F) * 0.2F`;
5. X/Z particle coordinates interpolated from the saved origin to accepted target coordinates plus `(nextDouble()-0.5) * target.width * 2.0`;
6. Y particle coordinate interpolated from saved origin to accepted target Y plus `nextDouble() * target.height`;
7. `spawnParticle("portal", ...)` using those exact coordinate and velocity locals;
8. a portal sound at the saved origin coordinates with volume/pitch `1.0F`;
9. a portal sound on the entity path using the projectile as the source entity, also at volume/pitch `1.0F`;
10. true return after the presentation path.

The admitted 1.7.10 method families accept both MCP and SRG names. Production analysis contains no Bamboo class-name or registry-name special case.

## Regression

Normal CI covers an unrelated-namespace synthetic teleport with the complete presentation. A 127-particle variant fails closed. A variant with the wrong entity-path sound keeps independent particle/interpolation/randomization/origin-sound facts but cannot become presentation-complete.

The checksum-pinned exact-corpus regression requires Bamboo's `snowball` / `ender` selector (id 6) to prove the full portal presentation from the supplied Bamboo JAR.

## Sidecar schema 9

`legacyforgebridge/variant-snowball-proof.json` now adds:

- family-level `teleportPresentationProven` / `teleportPresentationCount`;
- root `teleportPresentationFamilies`;
- per-variant `RANDOM_TELEPORT_SEMANTICS_PROVEN` after the gameplay and presentation chains are both complete;
- `portalParticleCount=128`;
- explicit interpolation, randomization, origin-sound and entity-sound proof facts;
- `teleportPresentationComplete=true` only after the whole bounded success presentation is admitted.

Selector completion is now computed from the actual per-selector effect set. Bounded `NONE` and `POTION` effects are complete directly; a `CUSTOM_HELPER_UNCOMPILED` selector is complete only when its corresponding teleport presentation proof is complete. Therefore Bamboo's DirtySnowball family can now become `selectorSpecificImpactSemanticsComplete=true` and `impactSemanticsComplete=true` at the source-semantics layer.

`impactCompilerWired=false` and `runtimeImplementationWired=false` remain unchanged. Source proof is not runtime code generation.

## Next boundary

The next step is no longer reverse-engineering DirtySnowball semantics. It is runtime compilation:

- generate/register the modern variant snowball item/projectile family;
- preserve metadata-to-selector routing through the already-proven mapping;
- compile base damage, potion effects and no-op variants;
- compile the bounded random teleport and portal presentation into the modern runtime;
- add runtime bytecode/behavior regressions and retire the corresponding legacy projectile classes only after the generated path is admitted.
