# LegacyForgeBridge — Bamboo alpha.27 MillStone base runtime

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

## Scope

This slice moves the already source-proven generic three-slot processor family from analysis-only output to a functional modern runtime. Bamboo `MillStone` remains the checksum-pinned acceptance anchor; production runtime selection is driven only by the generated generic processor sidecar.

## Green baseline

Runtime commit:

- `7efdebb7e8b39f84c4bfdc8637361410a9500666`
- GitHub Actions `corpus2-p0-verification` run `#222`: success
- source hygiene: success
- full Gradle build/tests: success
- remapped artifact upload: success
- artifact ZIP SHA-256: `51216e7ee39435d5cee48b4484e328b506ec39be712e4295b2b7b1eb75e86cd3`
- `legacyforgebridge-0.2.0-alpha.27.jar` SHA-256: `77f1838155180387b22dac18d8c02eab17bb9d9b440a92890fafb22bb39b9bb3`

The converter revision is `2026-09-15.6`, so older cached processor candidates are invalidated.

## Runtime admission gate

A processor is selected as a modern runtime-backed block only when all of the following are true in its generated sidecar:

- topology analyzer admitted the three-slot processor family;
- sided extraction proof is complete;
- optional legacy energy contract is completely proven;
- every source registration recipe was materialized without guessing;
- `runtimeProofComplete=true`;
- `baseRuntimeComplete=true`;
- `materializedRecipeCount == sourceRecipeCount`.

Otherwise the generated registry identity remains a normal converted block and no machine behavior is fabricated.

## MillStone semantics preserved by the base runtime

The source-proven MillStone runtime now preserves:

- three inventory slots: input `0`, primary output `1`, bonus output `2`;
- source stack limit `64`;
- sided exposure: DOWN `[2,1]`, UP `[0]`, horizontal `[0]`;
- DOWN extraction may not take slot `0`;
- manual/hopper insertion accepts arbitrary items only in slot `0`, matching source `isItemValidForSlot`;
- shift-click sends an item to slot `0` only when the modernized Grind recipe table resolves it;
- source interaction distance squared `64.0`;
- comparator output and inventory drops;
- source menu slot positions and player inventory layout;
- standard inventory persistence plus source progress keys `grindTime`, `grindItemName`, `grindItemDmg`;
- LFB `lfbActiveRecipe` for deterministic modern continuation while preserving fallback to legacy source identity;
- source stored-energy key `innerEnergy`;
- immediate input consumption when a grind starts;
- base progress threshold `400` and completion only after progress becomes greater than `400`;
- primary output merging and source bonus chance semantics;
- source behavior when an occupied output no longer matches: no fabricated replacement is produced;
- exact legacy acceleration arithmetic, including the unusual `500 / 100 + 1 = 6` progress step and `500 -> -100` stored-energy overshoot after subtraction.

## Modern implementation

The base runtime consists of:

- a source-gated processor runtime registry;
- one generic processor `BlockEntityType` per admitted generated block;
- `ConvertedLegacyProcessorBlock`;
- `ConvertedLegacyProcessorBlockEntity` implementing `WorldlyContainer`;
- a shared LFB `MenuType` and `ConvertedLegacyProcessorMenu`;
- a functional generic client screen;
- server-side ticking, NBT persistence, comparator behavior, drops and menu synchronization.

## Deliberately unresolved

This slice does **not** mark Bamboo or MillStone fully complete.

The exact sidecar boundary is:

- `baseRuntimeComplete=true`
- `tickingRuntimeComplete=true`
- `menuRuntimeComplete=true`
- `sidedTransferRuntimeComplete=true`
- `progressNbtRuntimeComplete=true`
- `energyStorageRuntimeComplete=true`
- `genericScreenRuntimeComplete=true`
- `energyIngressRuntimeComplete=false`
- `sourcePresentationComplete=false`
- `runtimeComplete=false`

The remaining MillStone work is therefore:

1. bridge the external legacy CoFH energy ingress contract without pretending another energy API is equivalent;
2. migrate the original machine presentation/renderer and GUI presentation from source evidence;
3. keep wider Bamboo loader-safety gates closed until other source classes are replaced or proven unnecessary.

The whole candidate remains `PARTIAL` and not installable under the final loader-safety gate.
