# Session 2026-09-17 — Plain Entity source retirement readiness

Checkpoint target: converter revision `2026-09-17.81`.

## Scope

The first `PLAIN_ENTITY_SYNCHED_DATA_ONLY` family now has modern class generation, `EntityType` registration, no-op renderer registration, FML remote spawn, initial typed DataWatcher mapping, and the `.80` post-init mutation gate.

This slice does **not** delete old 1.7.10 Entity or renderer classes. It adds the evidence layer required before source-class retirement can be authorized safely.

## Current-candidate reference scan

`LegacyCandidateReferenceAnalyzer` scans the actual staging candidate after generated semantic/entrypoint bytecode exists. This is intentionally different from the conservative source-JAR dependency graph: future bytecode rewrites must be able to remove a blocker when the candidate no longer references a legacy class.

For caller-supplied retirement targets it inventories:

- incoming class references from the current staged bytecode, including descriptors, owners, type constants, annotations and invokedynamic arguments;
- dotted/internal class-name literals;
- raw class-name occurrences in candidate resources;
- class/resource scan completeness.

Internal `legacyforgebridge/*` analysis sidecars are excluded from resource blocking because their `sourceClass` strings are diagnostic metadata, not runtime classloading references. A resource larger than the bounded 32 MiB scan limit makes resource-reference closure incomplete instead of being silently ignored.

## Retirement cohort readiness

`LegacyPlainEntityRetirementReadiness`, materialized from `LegacyClassDependencyAnalysisPass`, joins:

- executable plain-entity runtime rules;
- entity instantiation/world-spawn inventory;
- exact no-op renderer proof;
- conservative source-JAR root/dynamic evidence;
- the new current-candidate bytecode/resource reference scan.

The output is:

`legacyforgebridge/plain-entity-retirement-readiness.json`

An Entity retirement candidate is blocked by unresolved world-spawn arguments, unwired direct-construction/spawn migration, source root/dynamic/generated-reference evidence, incomplete reference scans, or current-candidate incoming/resource references.

The one deliberate cohort exception is the proven single no-op renderer referencing its own Entity type. That descriptor edge may be retired with the same Entity/renderer pair. Any bootstrap, proxy, handler, generated class, shared renderer, or runtime resource reference remains a blocker.

## Safety boundary

A positive `retirementCohortCandidateReady=true` is only modeled readiness. This slice keeps:

- `retirementAuthorizationWired=false`
- `sourceClassDeletionWired=false`
- `sourceClassDeletionAuthorized=false`
- `rendererClassDeletionAuthorized=false`

Actual removal must be a later mutation pass with another post-rewrite reference check. This preserves the dependency analyzer's rule that symbolic reachability alone never authorizes source-class exclusion.
