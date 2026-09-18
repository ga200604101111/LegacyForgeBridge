# 2026-09-18 — Processor Block construction replacement

## Scope

This slice follows converter revision 2026-09-18.129. Runtime-complete single-input processors already replace gameplay, presentation, TileEntity construction, registrations and the exact shared-IGuiHandler processor branches, but the modern processor Block was still created from generic `BlockBehaviour.Properties`.

Converter revision: 2026-09-18.130.

This revision proves and materializes a bounded source Block constructor/property subset into the modern processor Block. It does not remove the source Block allocation and does not authorize source-class deletion.

## Constructor proof boundary

`LegacySingleInputProcessorBlockConstructionAnalyzer` proves only a deliberately narrow constructor topology:

- the registered processor source Block has a no-argument constructor;
- constructor control flow is straight-line and has no try/catch, jump or switch;
- source-owned no-argument constructor delegation may be followed through the source hierarchy;
- the terminal external constructor must be vanilla 1.7.10 `BlockContainer(Material)`;
- the Material argument must be the source-proven vanilla rock/stone singleton;
- constructor-local Block property setters must be constant and have an explicitly mapped modern equivalent;
- unknown method calls, static writes, unsupported opcodes, unsupported materials and unsupported setters fail closed.

The admitted constructor-local property effects are:

- hardness;
- blast resistance;
- sound type;
- light emission.

Map color is fixed by the admitted stone material proof.

Legacy resistance semantics are reproduced rather than copied naively: 1.7.10 `setResistance(x)` stores an internal value of `x * 3`, while hardness can raise that internal resistance floor. The proof computes the resulting modern explosion resistance from the final legacy internal state.

Legacy light level is converted with the legacy integer emission rule `(int)(15 * level)`.

## Source override protection

A legacy `invokevirtual` constant-pool owner may name a source subclass even when the actual setter is inherited from vanilla Block.

The analyzer therefore does not trust an arbitrary source owner merely because the method name resembles a vanilla setter. It walks the source hierarchy and requires that no source class declares an override with the same method identity before the lookup reaches vanilla `Block` or `BlockContainer`.

This keeps constructor replacement fail-closed when a source override could add global or instance side effects.

## Modern runtime materialization

`LegacySingleInputProcessorBlockConstructionPass` writes:

`legacyforgebridge/single-input-processor-block-construction-replacement.json`

A proof-complete machine also receives:

- `blockConstructorReplacementProven=true`;
- `blockConstructionRuntimeWired=true`;
- a `blockConstruction` object in the primary processor rules sidecar.

The materialized object contains:

- `destroyTime`;
- `explosionResistance`;
- `soundType`;
- `mapColor`;
- `lightLevel`.

`LegacySingleInputProcessorRegistry` parses those values as a bounded `BlockConstruction` rule and `GeneratedModSupport.registerBlock` applies them to `BlockBehaviour.Properties` before constructing `ConvertedLegacyProcessorBlock`.

The source proof therefore changes the real modern Block runtime; it is not only readiness metadata.

## Explicitly excluded allocation-site effects

Constructor proof and source allocation retirement remain separate.

This revision does not claim that fluent calls surrounding a source allocation such as:

`new SourceBlock().setX(...).setY(...)`

have been replaced merely because the source constructor itself is proven.

The construction sidecar records:

- `allocationSiteEffectsIncluded=false`;
- `sourceAllocationStripWired=false`;
- `sourceClassDeletionWired=false`.

Any allocation-site setters or holder assignments must be independently proved before the allocation can be removed.

## Retirement readiness

`LegacySingleInputProcessorRetirementReadiness` now consumes the Block construction sidecar.

The Block constructor gate has three states:

- sidecar absent/invalid: `processor-block-constructor-replacement-not-wired`;
- analysis wired but proof incomplete: `processor-block-constructor-replacement-incomplete`;
- proof complete and modern properties runtime wired: the constructor gate is clear.

The independent `processor-block-source-allocation-retirement-not-wired` gate remains mandatory.

## Regression

Normal CI includes `LegacySingleInputProcessorBlockConstructionAnalyzerTest`, covering:

- stone `BlockContainer` constructor plus constant hardness/resistance/sound/light;
- legacy resistance-floor conversion;
- unsupported Material rejection;
- unsupported light-opacity setter rejection;
- non-straight-line constructor rejection.

The exact Bamboo processor regression now consumes the construction sidecar and mirrors its proof state into retirement readiness. If the exact source constructor is admitted, the primary machine rule must expose the materialized modern Block construction data and the constructor blocker must disappear while the independent source-allocation blocker remains.

The normal Gradle test task excludes checksum-pinned external `exact-corpus` tests, so the low-level proof/runtime wiring is exercised by normal CI while the exact Bamboo assertion remains the external-corpus guard.

## Verification

GitHub Actions workflow `corpus2-p0-verification` passed source-diff hygiene and the full Gradle build/test suite for commit:

`7d208e6223f6e146e8467337f7ec285adc8c0bc0`

Run id:

`35304777883`

## Next boundary

Revision .130 authorizes no source deletion.

The next safe slice is source Block allocation retirement. It must require:

1. revision .130 constructor replacement proof;
2. completed legacy `registerBlock` neutralization;
3. one exact, uniquely proven residual source Block allocation;
4. proof that any allocation-site fluent property effects are either absent or independently represented by the modern runtime;
5. post-strip source-reference closure.

Only after that allocation residue is removed may retirement readiness clear `processor-block-source-allocation-retirement-not-wired`.
