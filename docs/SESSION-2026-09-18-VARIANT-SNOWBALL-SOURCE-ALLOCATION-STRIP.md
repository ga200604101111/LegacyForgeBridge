# 2026-09-18 — Variant snowball source allocation strip

## Scope

This slice follows converter revision 2026-09-18.120, where the generated behavior constructor can independently prove replacement of the legacy ItemSnowball constructor while the original source allocation remains staged.

Converter revision: 2026-09-18.121.

This revision removes only a narrowly proven inline source allocation residue after both constructor replacement and registerItem neutralization are complete.

## Bounded allocation residue

LegacyItemAllocationResidueStripper currently admits only the exact no-argument inline shape:

- NEW source item class;
- DUP;
- invokespecial source item <init>()V;
- LDC exact legacy registry name;
- POP;
- POP.

The two POP instructions are the already-proven residue produced by registerItem call neutralization.

The stripper rejects:

- constructor arguments;
- namespace registerItem overloads;
- fluent chains between construction and registration;
- static-field storage;
- helper-returned/shared instances;
- ambiguous multiple residues;
- any additional reference to the source item class in the same owner method after removal.

This intentionally keeps the first implementation narrower than the constructor-replacement analyzer.

## Proof ordering

LegacyClassDependencyAnalysisPass now performs the final stages in this order:

1. generated behavior constructor replacement readiness;
2. proof-gated source allocation strip;
3. fresh class dependency inventory;
4. retirement readiness.

This guarantees dependency/reference evidence is computed from the actual post-strip staged candidate.

## Retirement readiness

LegacyVariantSnowballRetirementReadiness now joins a dedicated source-allocation-strip sidecar.

A cohort can be retirement-candidate-ready only when:

- modern playable runtime is complete;
- projectile registerModEntity strip is complete;
- item registerItem neutralization is complete;
- generated constructor replacement is proven;
- source allocation strip is complete;
- fresh candidate class/resource references have no external blockers.

Source-class deletion remains unauthorized in this revision.

## Regression

The unrelated-namespace runtime fixture requires the exact inline allocation to be removed and verifies that Bootstrap no longer has an incoming reference to the source item class.

The synthetic retirement regression requires the fully replaced cohort to become retirement-candidate-ready while all three original source classes remain present.

The exact Bamboo test probes the same boundary without assuming Bamboo uses the inline shape. If the allocation is stored in a field, shared, helper-produced, or otherwise outside the bounded pattern, allocation retirement remains explicitly blocked.

## Next boundary

Once this revision is green, the next slice can add the actual three-class cohort retirement mutation with fresh pre-delete and post-delete reference checks plus automatic byte restoration on failure, matching the existing plain-entity retirement safety model.
