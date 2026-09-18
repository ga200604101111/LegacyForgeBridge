# 2026-09-18 — Processor TileEntity constructor replacement proof

## Scope

This slice follows converter revision 2026-09-18.126. Runtime-complete single-input processors already expose exact TileEntity/block registration retirement and a fail-closed source-retirement readiness report.

Converter revision: 2026-09-18.127.

This revision closes only the TileEntity constructor-replacement gate. It does not remove source allocation or source classes.

## Bounded source constructor proof

LegacySingleInputProcessorTileConstructionAnalyzer examines the no-arg constructor chain for each already-proven processor TileEntity.

The accepted source effect surface is intentionally narrow:

- simple constructor control flow with no branches/switches/try-catch;
- source-owned no-arg superclass delegation ending exactly at vanilla TileEntity();
- exactly one ItemStack[] allocation whose constant length equals the source-proven processor slot count;
- optional explicit writes of JVM default values to source instance fields.

The following fail closed:

- missing/overloaded/unresolved constructor chains;
- external superclass constructors other than vanilla TileEntity();
- extra non-constructor method calls;
- static/global field writes;
- non-default source field initializers;
- unsupported allocations/opcodes;
- zero or multiple admitted inventory-array initializations;
- inventory arrays whose size differs from the already-proven processor topology.

## Modern replacement boundary

LegacySingleInputProcessorTileConstructionPass consumes only runtime-complete processor rules.

A proof is promoted to tileConstructorReplacementProven=true only when the bounded source constructor is complete and the modern processor BlockEntity constructor is already wired to initialize its inventory from rule.slots() using NonNullList.withSize. Ordinary Java default field initialization covers only source fields proved to remain at JVM defaults.

The sidecar is:

legacyforgebridge/single-input-processor-tile-construction-replacement.json

It records constructor chain identities, admitted field initializations, blockers and aggregate proven/blocked counts.

## Readiness integration

LegacySingleInputProcessorRetirementReadiness now consumes this sidecar when present and source-hash/schema-valid.

The root exposes tileConstructorReplacementAnalysisWired.

Per machine:

- tileConstructorReplacementProven=true removes only processor-tile-constructor-replacement-not-wired;
- failed or missing proof preserves that blocker.

The following independent gates remain unchanged:

- processor-block-constructor-replacement-not-wired;
- processor-block-source-allocation-retirement-not-wired;
- processor-gui-handler-retirement-not-wired;
- all real candidate incoming/resource/generated/dynamic reference blockers.

## Regression

Normal CI proves:

- a source constructor chain with one 3-slot ItemStack[] and only default scalar writes is admitted;
- a non-default source field initializer fails closed.

The exact Bamboo MillStone regression reads the same construction sidecar by sourceTileClass. If the real constructor passes the bounded proof, readiness must remove only the Tile constructor blocker. If it does not pass, both the construction sidecar and readiness must keep explicit blockers.

## Next boundary

No source class deletion is authorized by revision .127.

After CI is green, the readiness evidence determines which remaining gate to attack next. Likely candidates are:

1. source Block constructor/property replacement;
2. source Block allocation retirement after that constructor proof;
3. processor GUI handler branch retirement.

These remain independent so a successful Tile constructor proof cannot accidentally authorize the Block or GUI cohort.
