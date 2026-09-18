# 2026-09-18 — Variant snowball source cohort retirement

## Scope

This slice follows converter revision 2026-09-18.121. The modern variant-snowball runtime is complete, both legacy registrations are retired, the source item constructor has a generated behavior replacement, and an exact inline source allocation can be removed when its bounded residue is proven.

Converter revision: 2026-09-18.122.

This revision adds the actual proof-gated retirement mutation for the legacy item/projectile/selector class cohort.

## Atomic three-class retirement

LegacyVariantSnowballRetirementPass consumes only retirement rules whose retirementCohortCandidateReady flag is true.

For each admitted cohort it requires three distinct source class identities:

- source item class;
- source projectile class;
- selector enum class.

All three staged class files must exist at safe normalized paths.

## Fresh pre-delete verification

Immediately before deletion the pass reruns LegacyCandidateReferenceAnalyzer against the three source classes.

The fresh scan must prove:

- class-reference scan closure;
- resource-reference scan closure;
- every incoming class reference is internal to the same three-class cohort;
- no external candidate resource references remain.

A stale readiness result therefore cannot authorize deletion after later staging mutations.

## Delete / verify / restore transaction

When the fresh pre-delete scan is clean:

1. all three original class byte arrays are saved;
2. all three class files are deleted;
3. candidate references are rescanned for all three targets;
4. any remaining incoming/resource reference or scan incompleteness rejects retirement;
5. on any rejection or exception, all three saved class files are restored.

Only a clean post-delete scan sets retirementComplete=true and sourceClassDeletionAuthorized=true.

## Pipeline ordering

LegacyClassDependencyAnalysisPass now runs the variant-snowball retirement transaction after:

- generated behavior code exists;
- constructor replacement readiness;
- source allocation strip;
- fresh final class dependency analysis;
- retirement readiness.

LegacyBytecodeAudit therefore sees the post-retirement candidate state.

## Regression

The unrelated-namespace fixture requires a retirement-ready cohort to delete exactly three original source classes while leaving the unrelated Bootstrap class intact.

The exact Bamboo regression is conditional and atomic:

- if the real Bamboo cohort reaches retirement candidate readiness, ItemDirtySnowball, EntityDirtySnowball and EnumDirtySnowball must all be removed;
- otherwise all three must remain present.

Partial deletion is never accepted.

## Next boundary

After this revision is green, variant-snowball work should move from the local cohort to source-wide reference cleanup and candidate accounting: verify any remaining Bamboo classes that referenced the retired family no longer require those symbols, update progress evidence, and continue with the next unsupported gameplay/content family rather than broadening retirement rules without source proof.
