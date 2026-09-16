# Session 2026-09-16 — Forge 1.7.10 Material harvest fast-path proof

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

The previous slices proved:

- ordinary non-silk block drop stack composition;
- `HarvestDropsEvent` boundaries;
- explosion drop semantics;
- default silk-touch eligibility/stack construction;
- source-owned harvest callback customization boundaries;
- exact raw `Block.<init>(Material)` constructor provenance.

The remaining broad harvest blocker was still intentionally open because Forge 1.7.10 can depend on
held-tool classes/levels and `EntityPlayer.canHarvestBlock`.

This slice proves one exact platform branch without implementing those harder routes:

```text
block.getMaterial().isToolNotRequired() == true
```

When that branch is true, Forge 1.7.10 returns `true` before consulting the held item, harvest tool,
harvest level, player fallback, or `PlayerEvent.HarvestCheck`.

Gameplay block-drop execution remains disabled.

## Exact 1.7.10 evidence

The version-locked source used for the platform facts is MCP 908, whose repository documents that it
is the Minecraft 1.7.10 coder pack.

In the MCP 908 `Material` source:

```text
field_76241_J = true
func_76221_f() -> field_76241_J = false
func_76229_l() -> return field_76241_J
```

Legacy mappings identify:

```text
func_76221_f = setRequiresTool
func_76229_l = isToolNotRequired
```

Forge 1.7.10 `ForgeHooks.canHarvestBlock` executes:

```text
if (block.getMaterial().isToolNotRequired()) {
    return true;
}

// only after that:
held item
getHarvestTool(metadata)
item harvest level
player.canHarvestBlock(block)
```

Forge's 1.7.10 `Block.canHarvestBlock(player, metadata)` delegates directly to that ForgeHooks method.

The patched `EntityPlayer.canHarvestBlock` is the location that fires
`ForgeEventFactory.doPlayerHarvestCheck`, so a successful Material fast-path never reaches the
`HarvestCheck` event.

## Version-locked Material table

`LegacyMaterialHarvestRules1710` records all 34 vanilla static Material fields from MCP 908 together
with their mapped material name and exact `isToolNotRequired()` result.

Only these six call `setRequiresTool()` in the 1.7.10 static initializer:

```text
rock
iron
anvil
snow
craftedSnow
web
```

Their `toolNotRequired` value is therefore `false`.

Every other vanilla static Material in the table preserves the default `true` value.

Unknown owners, descriptors, or field names are not inferred.

## Source fast-path safety

`LegacyBlockHarvestEligibilityAnalyzer` now exposes two independent source proofs.

The existing full tool-route proof still rejects:

```text
canHarvestBlock
getHarvestTool
getHarvestLevel
isToolEffective
getMaterial
setHarvestLevel(...)
```

The new `materialFastPathSourceSafe` proof is intentionally narrower. Because the Material branch
returns before tool lookup, only source behavior that can replace the branch itself is relevant:

```text
canHarvestBlock
getMaterial
```

For example, a source block may override `getHarvestTool` or call `setHarvestLevel` and still be fully
provable through a no-tool-required Material. Those customizations are unreachable on that branch.

A specialized external Block superclass remains fail-closed.

## New composition pass

`LegacyBlockHarvestMaterialProofPass` runs immediately after `LegacyBlockDropAnalysisPass` in the
normal conversion pipeline.

The drop analyzer continues to emit schema v5 as its local intermediate artifact. The new pass
consumes that exact version and writes final schema v6.

This preserves the existing drop-analyzer regression surface while keeping platform harvest proof as
a separate stage.

Pass id:

```text
legacy-block-harvest-material-proof
```

Rule version:

```text
minecraft-1.7.10-mcp908-forge-1.7.10
```

## Block-drop sidecar schema v6

Each admitted plan now adds:

```text
materialFastPathSourceSafe
materialFastPathSourceReasons
legacyMaterialProvenanceComplete
legacyMaterialReasons
legacyMaterialHarvestRuleKnown
legacyMaterial
materialFastPathHarvestEligibilityProofComplete
harvestEligibilityProofComplete
harvestEligibilityMode   // only when complete
```

A proven `legacyMaterial` object contains:

```text
owner
fieldName
descriptor
namedMaterial            // only for a version-locked known field
toolNotRequired          // only for a version-locked known field
```

Root evidence adds:

```text
schemaVersion = 6
materialHarvestRuleVersion
materialProvenanceAnalysisDiagnostics
harvestEligibilityProofCompletePlans
gameplayDropRuntimeWired = false
```

## HarvestCheck behavior

If all of the following are proven:

```text
source canHarvestBlock/getMaterial fast-path safe
raw Block Material provenance complete
Material field known in the 1.7.10 table
toolNotRequired == true
```

then harvest eligibility is complete even when the source JAR has a registered
`PlayerEvent.HarvestCheck` handler, because Forge does not call the player fallback on this branch.

The pass therefore removes these blockers only for that proven fast-path:

```text
harvest-eligibility-proof-pending
harvest-eligibility-source-proof-missing
harvest-eligibility-source-customization-runtime-pending
harvest-check-event-runtime-pending
harvest-check-event-absence-unproven
```

This does not suppress `HarvestDropsEvent`; that event belongs to the later drop-result path and
remains independent.

## Remaining fail-closed routes

For Materials whose 1.7.10 rule says `toolNotRequired == false`, the pass adds:

```text
harvest-tool-player-route-pending
```

Unresolved provenance adds:

```text
harvest-material-provenance-pending
```

Unknown Material fields add:

```text
harvest-material-rule-unproven
```

Source overrides of `canHarvestBlock` or `getMaterial` add:

```text
harvest-material-fast-path-source-runtime-pending
```

The following are still not migrated:

- held-item Forge tool classes;
- per-stack harvest levels;
- per-metadata Block harvest tool/level requirements on the tool route;
- `EntityPlayer.canHarvestBlock` vanilla fallback equivalence;
- modern mining tags/tool tiers;
- final gameplay drop execution.

## Explicit gameplay gate

Once the broad harvest proof begins completing, an empty blocker list would incorrectly imply that
runtime execution is ready. Schema v6 therefore always carries:

```text
block-drop-gameplay-runtime-pending
```

until a later slice actually wires and validates the modern block-drop gameplay runtime.

`runtimeComplete` remains false.

## Regression coverage

New tests verify:

- all 34 MCP 908 vanilla Material fields are present;
- exactly `rock`, `iron`, `anvil`, `snow`, `craftedSnow`, and `web` require tools;
- wrong Material owner/descriptor/field remains unknown;
- `getHarvestTool` and `setHarvestLevel` do not poison Material fast-path source safety;
- `canHarvestBlock` and `getMaterial` do poison it;
- a proven wood Material completes harvest eligibility despite a registered `HarvestCheck` handler;
- a rock Material remains on the tool/player route;
- unresolved Material provenance remains fail-closed;
- a source `getMaterial` override remains fail-closed;
- gameplay runtime remains explicitly blocked after harvest proof completes.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.43
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is still not preserved in the current CI workspace. This slice adds generic Forge/Minecraft 1.7.10
platform proof and makes no new exact whole-JAR Bamboo compatibility claim. Bamboo remains `PARTIAL`
/ not loader-safe pending the remaining shared runtime capabilities, source-class finalization,
exact-corpus regression, and real-machine gameplay validation.
