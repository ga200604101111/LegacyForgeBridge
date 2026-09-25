# 2026-09-18 — Processor GUI handler branch retirement

## Scope

This slice follows converter revision 2026-09-18.128. The previous revision proved the exact legacy `IGuiHandler` server/client branches for runtime-complete single-input processors but intentionally left the shared handler unchanged.

Converter revision: 2026-09-18.129.

This revision retires only the proven processor GUI-id branches. It does not unregister or delete the shared handler and it does not authorize source-class deletion.

## Exact branch mutation

`LegacySingleInputProcessorGuiHandlerBranchStripper` accepts a target only when the staged handler still contains the exact server/client method identities and GUI id shape expected by the source proof.

For each method it requires:

- the handler id parameter to feed a `TABLESWITCH` or `LOOKUPSWITCH`;
- exactly one switch mapping for the processor `guiId`;
- a target label not shared by another GUI id and not equal to the default label;
- straight-line target flow ending in `ARETURN`;
- no nested branch/switch inside the admitted case;
- no external jump target entering the middle of the case;
- the staged server branch to still reference the proven source Container and TileEntity;
- the staged client branch to still reference the proven source Gui and TileEntity.

Only the meaningful instructions of the selected case are replaced with:

`ACONST_NULL; ARETURN`

Every unrelated GUI id and the handler class itself are preserved.

## Fresh source re-proof and rollback

`LegacySingleInputProcessorGuiHandlerStripPass` consumes:

- the runtime-complete processor rule;
- the revision .128 GUI-handler proof sidecar.

Before touching staged bytecode it reruns `LegacySingleInputProcessorGuiHandlerAnalyzer` against the original source JAR and requires the fresh result to agree with the stored proof on:

- guiId;
- handler owner;
- source Container;
- source Gui;
- server method name/descriptor;
- client method name/descriptor;
- unique target-label proof.

The mutation is built in memory. The rewritten class is reparsed before any staged file is replaced.

Post-rewrite proof requires both target cases to be exact null returns and to contain no source TileEntity, Container or Gui symbolic references. Any mismatch returns the original bytes and records a blocker.

## Sidecar

The pass writes:

`legacyforgebridge/single-input-processor-gui-handler-strip.json`

Root guarantees include:

- `guiHandlerBranchStripWired=true`;
- `freshSourceReproofRequired=true`;
- `sharedHandlerPreserved=true`;
- `unrelatedGuiIdsPreserved=true`;
- `sourceClassDeletionWired=false`.

A complete rule must report two stripped branches: one server and one client.

## Retirement readiness

`LegacySingleInputProcessorRetirementReadiness` now consumes the strip sidecar.

The GUI gate has three explicit states:

- source branch proof missing: `processor-gui-handler-branch-proof-incomplete`;
- proof exists but no strip implementation sidecar: `processor-gui-handler-branch-strip-not-wired`;
- strip implementation ran but the exact pair did not retire: `processor-gui-handler-branch-strip-incomplete`.

When proof and strip are both complete, the GUI gate is cleared.

At that point the proven source Container and source Gui are added to the processor retirement cohort before candidate-reference closure is evaluated. Their nested companion classes are expanded through the same conservative cohort logic. The shared `IGuiHandler` is deliberately not added to the retirement cohort.

This prevents a false-ready state where Block/Tile could appear isolated while presentation classes still retain source references.

## Regression

Normal CI now includes `LegacySingleInputProcessorGuiHandlerBranchStripperTest`:

- GUI id 7 is retired to a null return in both server and client methods;
- GUI id 8 remains bytecode-backed and is not modified;
- source TileEntity/Container/Gui references disappear from the retired cases;
- two ids sharing one case label fail closed and return byte-for-byte original class bytes.

The exact Bamboo processor regression now reads the new strip sidecar and checks that:

- strip wiring and fresh-reproof requirements are present;
- a complete .128 proof must advance to a complete .129 strip for the exact MillStone source;
- retirement readiness reflects the strip result;
- successful GUI retirement removes both GUI blockers and expands the presentation cohort;
- Block constructor and Block source-allocation gates remain independently blocking.

## Verification

GitHub Actions workflow `corpus2-p0-verification` passed the full build and normal test suite for commit:

`c5eecd493127f59c05b35809014e0b178fbdfcc8`

Run id: `35303886851`.

The normal Gradle `test` task excludes checksum-pinned external `exact-corpus` tests. The exact Bamboo assertion is therefore committed as the next exact-JAR guard, while the new low-level mutation safety is exercised by normal CI.

## Next boundary

Revision .129 authorizes no source deletion.

The processor retirement blockers now remaining by design are:

- `processor-block-constructor-replacement-not-wired`;
- `processor-block-source-allocation-retirement-not-wired`;
- any candidate/source reference blockers exposed by the expanded Block/Tile/Container/Gui cohort.

The next safe slice is Block-constructor/property replacement proof. Source Block allocation retirement must remain separate until that proof is complete.
