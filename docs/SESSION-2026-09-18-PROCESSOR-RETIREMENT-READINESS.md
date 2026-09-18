# 2026-09-18 — Processor source retirement readiness

## Scope

This slice follows converter revision 2026-09-18.125. Runtime-complete single-input processors can now neutralize their exact legacy TileEntity registration and conservatively neutralize a uniquely owned GameRegistry.registerBlock call while preserving source Block constructor evaluation.

Converter revision: 2026-09-18.126.

This revision does not delete any processor source class. It materializes the complete remaining retirement evidence for the source Block/TileEntity cohort so subsequent work can close one independent gate at a time.

## Readiness inputs

LegacySingleInputProcessorRetirementReadiness consumes:

- single-input-processor-rules.json schema 4;
- single-input-processor-tile-registration-strip.json;
- single-input-processor-block-registration-strip.json;
- final staged candidate bytecode;
- LegacyClassDependencyAnalyzer evidence.

Only machines with baseRuntimeComplete=true, sourcePresentationComplete=true and runtimeComplete=true are evaluated.

## Source cohort

The initial processor retirement cohort contains:

- sourceBlockClass;
- sourceTileClass.

Any staged nested classes named below either primary class using the JVM nested-class prefix base + "$" are added automatically to the same cohort. Naming only expands the candidate set; every companion still needs dependency and reference closure.

## Registration evidence

Readiness requires exactly one successful source registration retirement for each primary runtime identity:

- tileRegistrationStripComplete=true with one stripped registerTileEntity site;
- blockRegistrationStripComplete=true with one stripped registerBlock site.

If the real source block registration uses a shared helper, an unsupported overload or a used return value, the block strip remains incomplete and readiness reports block-registration-strip-incomplete.

## Independent mandatory blockers

A complete modern processor runtime is intentionally not treated as proof that source constructors or GUI registration are removable.

Revision .126 keeps four explicit gates closed:

- processor-block-constructor-replacement-not-wired;
- processor-block-source-allocation-retirement-not-wired;
- processor-tile-constructor-replacement-not-wired;
- processor-gui-handler-retirement-not-wired.

These are separate because a source constructor may contain initialization/global side effects not represented by runtimeComplete, and the legacy GUI handler may serve unrelated GUI ids.

## Candidate reference closure

For Block, TileEntity and every nested companion, readiness records:

- current candidate incoming class references;
- current candidate resource references;
- original source incoming/outgoing references;
- source root evidence;
- generated symbolic references;
- dynamic bytecode evidence;
- candidate-state evidence.

External incoming references remain blockers. Expected examples before later gates are closed include:

- bootstrap/source holder -> Block because Block construction is deliberately preserved;
- GUI handler -> TileEntity through the old GUI branch.

References inside the expanded Block/Tile/nested cohort are allowed.

## Deletion boundary

The sidecar always keeps:

- retirementAuthorizationWired=false;
- sourceClassDeletionWired=false;
- sourceClassDeletionAuthorized=false;
- deletedSourceClassCount=0.

No source byte is removed by this revision.

## Regression

The unrelated-namespace processor fixture first completes both TileEntity and Block registration strips, then proves that retirement still remains blocked by constructor/allocation/GUI gates and by actual surviving external references.

The exact Bamboo MillStone regression now reads the same readiness sidecar and resolves its rule from sourceBlockClass. It requires modern runtime replacement and TileEntity registration retirement to be visible, mirrors the real block-registration strip result, and requires all remaining constructor/allocation/GUI blockers to stay explicit.

## Next boundary

The readiness report now determines the next processor work directly:

1. prove source Block constructor/property replacement;
2. only then remove the preserved source Block allocation;
3. prove source TileEntity constructor replacement;
4. isolate or retire the processor GUI handler branch without harming unrelated GUI ids;
5. recompute readiness and only when all actual candidate references close, add an atomic pre-delete/post-delete retirement pass with full byte restoration.
