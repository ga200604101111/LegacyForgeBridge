# Session 2026-09-17 — Plain Entity source cohort retirement

Checkpoint target: converter revision `2026-09-17.84`.

## Goal

The `.81` retirement sidecar deliberately stopped at modeled readiness. `.82` and `.83` then removed exact common/client registration references from the current staging candidate when those registration expressions were proven replaceable.

This slice wires actual source-class retirement for the subset of plain Entity/no-op-renderer cohorts that remain fully clean after those rewrites.

## Authorization is stricter than readiness

`LegacyPlainEntityRetirementPass` reads only `.81` cohorts with `retirementCohortCandidateReady=true`, but does not trust that sidecar as the final deletion decision.

For each cohort it performs a fresh `LegacyCandidateReferenceAnalyzer` scan immediately before deletion and requires:

- class-reference scan closure complete;
- resource-reference scan closure complete;
- both source class files still present at safe staging paths;
- no incoming Entity references except its paired renderer;
- no incoming renderer references except its paired Entity;
- no runtime resource reference to either class.

The paired renderer-to-Entity callback descriptor remains the only allowed pre-delete cohort edge.

## Transactional delete + post-check

Before mutation, both class files are read into memory. The pass then deletes both and performs another fresh reference scan against the remaining staging candidate.

Retirement is authorized only if the post-delete scan is complete and finds zero remaining incoming/resource references to either retired class name.

If deletion or post-delete validation fails, both original class byte arrays are restored to their original staging paths and the cohort remains blocked.

Output:

`legacyforgebridge/plain-entity-retirement.json`

It records:

- fresh pre-delete incoming references;
- post-delete scan diagnostics;
- rollback status;
- per-class deletion authorization;
- cohort retirement completion;
- deleted source-class count and blockers.

## Pipeline position

Retirement is invoked at the end of `LegacyClassDependencyAnalysisPass`, after generated semantic/entrypoint code and profile-level Entity/renderer registration stripping, but before `LegacyBytecodeAuditPass`. The audit therefore sees the actual retired candidate state.

## Remaining boundary

This slice does not bypass `.77/.78` instantiation proof. Any direct legacy Entity construction, proven local `World.spawnEntityInWorld` use requiring rewrite, or unresolved spawn argument keeps `.81` readiness false, so `.84` cannot delete that cohort.

Those local spawn/constructor sites remain the next runtime-migration boundary.
