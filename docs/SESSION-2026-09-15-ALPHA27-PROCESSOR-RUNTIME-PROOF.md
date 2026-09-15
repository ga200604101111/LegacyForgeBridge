# LegacyForgeBridge — MillStone runtime proof gate

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

This slice does not enable the modern processor runtime. It adds the final source proofs required before that runtime may be selected.

## Added proof

For an already-admitted generic three-slot processor, `LegacySingleInputProcessorRuntimeAnalyzer` now proves:

- sided extraction truth table is exactly `side != DOWN || slot != inputSlot`;
- whether the source hierarchy implements the legacy CoFH `IEnergyHandler` API;
- the source minimum energy quantum used by the active processing path;
- the source maximum accepted/stored energy boundary used by `receiveEnergy`;
- the inherited integer NBT key used for legacy energy persistence;
- the acceleration/consumption shape contains the source `energy / min + 1` byte-step and `energy -= min * step` operations.

The proof is emitted into processor sidecar schema v2. A machine receives `runtimeProofComplete=true` only when sided extraction is proven and, when legacy energy is present, all energy evidence is complete.

## Exact Bamboo evidence

The checksum-pinned Bamboo 2.6.8.5 corpus proves for MillStone:

- DOWN extraction blocks only slot 0;
- min energy = 100;
- max energy = 500;
- energy NBT key = `innerEnergy`;
- the source accelerated path is reachable and proven.

The legacy overshoot behavior is preserved as evidence, not normalized: while already processing with energy 500 and minimum 100, the source computes step 6 and subtracts 600, leaving -100.

## Recipe provenance

Materialized processor recipes now retain `legacyInput` alongside modern input alternatives. It stores source registry kind, namespace, registry name, required count and metadata/wildcard metadata. This is required to migrate an in-progress legacy machine whose NBT stores `grindItemName` and `grindItemDmg` after consuming the physical input stack at process start.

## Still incomplete

This commit deliberately does not claim completion of:

- modern processor BlockEntity ticking;
- Menu/quick-move/progress synchronization;
- old processor NBT migration into modern runtime state;
- external CoFH/Forge energy transport into the modern BlockEntity;
- source-specific MillStone block renderer or GUI presentation.

Those remain subsequent runtime/presentation slices.
