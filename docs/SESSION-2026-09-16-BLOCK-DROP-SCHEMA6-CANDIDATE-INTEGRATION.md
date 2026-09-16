# Session 2026-09-16 — Block-drop schema v6 candidate integration lock

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

The preceding Material harvest fast-path slice already has focused unit/pass regressions. This slice
adds one full `LegacyConversionEngine` regression so the proof is locked at the final candidate JAR
boundary rather than only at an intermediate staging directory.

## Integration fixture

`BlockDropCandidateIntegrationTest` creates a synthetic Forge 1.7.10 JAR containing:

- `mcmod.info` for a normal generic mod;
- one source-owned Block registered through an `@Mod.EventHandler` lifecycle method;
- a direct `Block.<init>` call using MCP908 `Material.field_151575_d` (`wood`).

The test runs the real `LegacyConversionEngine` and opens the emitted candidate JAR.

## Assertions

The final candidate must contain:

```text
legacyforgebridge/block-material-provenance.json
legacyforgebridge/block-drop-plans.json
```

The block-drop sidecar must be the post-composition schema:

```text
schemaVersion = 6
materialHarvestRuleVersion = minecraft-1.7.10-mcp908-forge-1.7.10
harvestEligibilityProofCompletePlans = 1
gameplayDropRuntimeWired = false
```

The admitted wood plan must prove:

```text
legacyMaterial.namedMaterial = wood
legacyMaterial.toolNotRequired = true
harvestEligibilityProofComplete = true
```

and must remove the superseded broad blocker:

```text
harvest-eligibility-proof-pending
```

while retaining the explicit runtime gate:

```text
block-drop-gameplay-runtime-pending
```

`runtimeComplete` must remain false.

The test also verifies that the real conversion plan applied all three relevant stages:

```text
legacy-block-material-provenance
legacy-block-drop-analysis
legacy-block-harvest-material-proof
```

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.44
```

## Runtime boundary

This slice does not enable gameplay drops. It only ensures the already-proven schema-v6 harvest
fast-path survives the complete conversion pipeline and is embedded in the candidate artifact.
