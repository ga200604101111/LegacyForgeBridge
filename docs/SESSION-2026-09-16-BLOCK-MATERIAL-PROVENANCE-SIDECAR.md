# Session 2026-09-16 — Block Material provenance sidecar

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

`LegacyBlockMaterialProvenanceAnalyzer` already proves the exact raw Minecraft 1.7.10
`Material` static field reaching a source-owned `Block.<init>` call. This slice makes that proof a
first-class conversion artifact so the later harvest-equivalence stage can consume it without
re-running or reinterpreting unrelated block-drop analysis.

This work remains evidence-only. It does not enable gameplay drops or infer mining/tool semantics
from a Material field name.

## New conversion artifact

A new conversion pass writes:

```text
legacyforgebridge/block-material-provenance.json
```

Schema version: `1`.

Root fields:

```text
schemaVersion
sourceSha256
blocks
 diagnostics
completeBlocks
incompleteBlocks
```

Each registered block records:

```text
legacyRegistryName
legacyNamespace            // when proven/present
sourceClass                // when proven
 directBlockSourceClass     // source class that directly invokes Block.<init>, when proven
complete
material                   // only when provenance is complete
reasons
```

The `material` object contains only raw bytecode provenance:

```text
owner
fieldName
descriptor
```

For example, a field such as `field_151575_d` is intentionally kept exactly as observed. This
sidecar does not label it as wood/rock/plant, does not claim whether a tool is required, and does
not assign a modern mining tag or harvest level.

## Fail-closed behavior

The pass preserves every analyzer failure reason. A block remains `complete=false` when Material
provenance cannot be reduced to one stable static `Material` field across the relevant direct Block
constructors.

No fallback inference is introduced by the materializer.

## Pipeline registration

`LegacyBlockMaterialProvenancePass` is registered in `LegacyConversionEngine` immediately after the
source-owned Block callback inventory and before block-drop analysis. This makes the sidecar part of
normal generic conversion rather than a dormant helper class.

The pass id is:

```text
legacy-block-material-provenance
```

## Regression coverage

`LegacyBlockMaterialProvenancePassTest` builds a synthetic legacy JAR containing:

- one direct Block whose constructor consumes a raw static Material field;
- one direct Block whose Material argument is unresolved/null;
- source-proven `GameRegistry.registerBlock` calls for both.

The test verifies exact source hash, counts, raw Material owner/name/descriptor, preserved incomplete
reasons, and explicitly verifies the sidecar does **not** synthesize fields such as `toolRequired`,
`harvestLevel`, or `modernTag`.

## Remaining harvest boundary

The broad block-drop runtime blocker remains:

```text
harvest-eligibility-proof-pending
```

Still unproven here:

- the exact Forge/Minecraft 1.7.10 `Material.isToolNotRequired()` truth table;
- raw Material field identity/semantic mapping where needed;
- held-item Forge tool classes and harvest levels;
- `EntityPlayer.canHarvestBlock` fallback equivalence;
- modern mining tags/tool-level mapping;
- final gameplay execution of the proven block-drop plan.

The independent schema-v5 silk-touch proof added by the preceding commit is preserved unchanged.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.42
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is still not preserved in the CI workspace. This slice therefore makes no new exact whole-JAR
Bamboo compatibility claim. Bamboo remains `PARTIAL` / not loader-safe until the remaining shared
runtime capabilities, source-class finalization, exact-corpus regression and real-machine gameplay
validation are complete.
