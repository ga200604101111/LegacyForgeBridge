# 2026-09-18 — Variant snowball retirement readiness

## Scope

This slice follows converter revision 2026-09-18.117. The playable modern runtime is complete and the exact legacy projectile registerModEntity call is stripped when its source stack slice is proven safe.

Converter revision: 2026-09-18.118.

This revision does not delete any source class. It materializes the evidence required before a later deletion pass can be authorized.

## Cohort

Each complete runtime rule defines one source retirement cohort:

- source item class;
- source projectile class;
- selector enum class.

The modern runtime must already be fully complete, including item launch, projectile EntityType registration, synchronized metadata, impact gameplay and renderer registration.

## Candidate reference closure

LegacyVariantSnowballRetirementReadiness scans the final staged candidate for the complete three-class cohort and records:

- incoming class references for each source class;
- resource references;
- original source-JAR incoming/outgoing references;
- generated symbolic references;
- dynamic bytecode evidence;
- candidate class state.

References between the three cohort classes are allowed because they would be retired together. Any incoming reference from outside the cohort remains a blocker.

## Registration gates

Projectile retirement additionally requires the exact registerModEntity strip from revision .117.

Item retirement remains intentionally blocked in this revision:

- itemRegistrationRetirementRequired=true;
- itemRegistrationStripComplete=false;
- item-registration-strip-not-wired is emitted as a mandatory blocker.

This prevents a complete modern runtime from being mistaken for permission to remove the old Item class while GameRegistry.registerItem or a shared helper can still reference it.

## Deletion policy

The readiness sidecar always keeps:

- retirementAuthorizationWired=false;
- sourceClassDeletionWired=false;
- sourceClassDeletionAuthorized=false;
- deletedSourceClassCount=0.

No mutation is performed.

## Regression

The unrelated-namespace fixture verifies that:

- modern runtime replacement is complete;
- projectile registration strip is complete;
- item registration retirement remains incomplete;
- the source cohort is not retirement-ready;
- the source class files remain present.

The exact Bamboo runtime test also verifies this fail-closed boundary after proving the real snowball runtime and projectile registration strip.

## Next boundary

The next work item is a conservative GameRegistry.registerItem retirement proof. It must distinguish direct single-use registration sites from shared helper methods. Only after that proof removes the final external item-registration reference should a deletion pass perform fresh pre-delete/post-delete reference scans with byte restoration on failure.
