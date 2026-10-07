# 2026-10-07 — rev260/rev262 source handoff gap audit

Branch: `feature/generic-conversion-iyamato-corpus3`

Current checkpoint commit before this document: `328aa39a1151fc08548f29ddc01d6149c21f7b5c`.

Reference runtime supplied by the user:
`legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar`.

This document records confirmed source/repository gaps. It deliberately distinguishes exact preserved source, missing source, and source files that exist on GitHub but are older than the bytecode shipped in rev260.

## 1. rev262 checkpoint is now persisted

The Twilight Forest Block CreativeTab allocation-specific proof is stored under
`diagnostics/rev262-tf-creative/`.

It is intentionally not promoted to `src/main` yet because its rev260 integration target,
`Corpus3GenericCompletionPass.java`, is not present on this branch.

## 2. Exact or near-exact historical source that is already preserved

### rev254
Source exists under `diagnostics/rev254-generic-sapling/`, including the rev254 sapling and Fast S12 classes.

### rev255
Source exists under `diagnostics/rev255-s12-trace/`, including rev255 trace classes and the rev255 Fast S12 revisions.

### rev256 partial
`diagnostics/rev256-sapling-proof/` preserves the sapling-proof work, but not the full rev256 production overlay.

These diagnostic trees are evidence/checkpoints, not a complete current `src/main` baseline.

## 3. Confirmed production source missing from src/main

The rev260 JAR contains these production classes, while the branch has no corresponding current
`src/main/java` source at the expected path.

### Core conversion/runtime examples
- `behavior/ConvertedCorpus3BehaviorItem.java`
- `convert/Corpus3ProjectileCompilerSupport.java`
- `convert/DesktopPackHook.java`
- `convert/LegacyCreativeTabDefaults.java`
- `convert/SaplingSourceEvidence.java`
- `convert/pass/Corpus3GenericCompletionPass.java`
- `convert/runtime/Corpus3BlockStateCarrySupport.java`
- `convert/runtime/Corpus3ItemRuntimeSupport.java`

This is a confirmed minimum set, not a claim that no other older local-overlay class is missing.

### Desktop package
The rev260 JAR contains desktop runtime classes, including `DesktopConversionSession` and
`DesktopFiles`, while the branch currently has no `src/main/java/dev/yinghuang/legacyforgebridge/desktop/`
production source tree.

### rev256
- `rev256/BridgeCommandsClient.java`

### rev257
- `rev257/SaplingEvidenceLookup.java`

### rev258
- `rev258/OrderedS12Drain.java`
- `rev258/S12EntryAccess.java`
- `rev258/VelocityMailbox.java`
- `rev258/VelocityToken.java`
- `rev258/mixin/S12EntryMixin.java`
- `rev258/mixin/S12TokenMixin.java`

### rev259
- `rev259/CacheCommands.java`
- `rev259/LegacyTextResources.java`
- `rev259/SessionRetention.java`

### rev260
- `rev260/HandoffDiagnostics.java`
- `rev260/HandoffIO.java`
- `rev260/HandoffLog.java`

The rev256-rev260 build metadata embedded in the runtime explicitly records local overlay builds and
`repositoryWrites: false` / equivalent wording.

## 4. Source present on GitHub but stale relative to rev260 bytecode

These classes have source on the branch, but the rev260 JAR references later local helper classes or
the revision build metadata says they were changed by audited bytecode deltas.

Confirmed examples:

### rev256
- `convert/LegacyBlockRenderType1710.java`

### rev257
- `convert/pass/LegacySimpleBlockPresentationPass.java` references the new
  `SaplingSourceEvidence` runtime in rev260 bytecode.
- rev254 `LegacySaplingSupport` was overlaid again to call `rev257/SaplingEvidenceLookup`.

### rev258
- rev254 `FastS12` bytecode references `rev258/OrderedS12Drain`,
  `VelocityMailbox`, and `VelocityToken`.

### rev259
- `convert/LegacyConversionManager.java` bytecode references `rev259/LegacyTextResources`.
- `convert/pass/LegacyLanguagePass.java` bytecode references `rev259/LegacyTextResources`.
- `DesktopConversionSession` bytecode references `rev259/SessionRetention`.
- `rev256/BridgeCommandsClient` bytecode references `rev259/CacheCommands`.

### rev260
- `convert/ManagedCandidateInstaller.java` bytecode references `rev260/HandoffIO`.
- `DesktopFiles` bytecode references `rev260/HandoffIO`.
- `DesktopConversionSession` bytecode references `rev260/HandoffDiagnostics` and
  `rev260/HandoffLog`.
- The embedded `META-INF/lfb/desktop-helper.jar` was also repackaged in rev260 and belongs to the
  same source-recovery problem.

Therefore copying only newly named rev256-rev260 classes into `src/main` would still not recreate
the shipped behavior.

## 5. Resource/build wiring is also stale

Branch `src/main/resources/fabric.mod.json` currently contains only the older core client
entrypoints and `legacyforgebridge.client.mixins.json`.

The rev260 runtime additionally wires:
- `rev254.Rev254Client`
- `rev255.TraceClient`
- `rev256.BridgeCommandsClient`
- `legacyforgebridge.rev254.mixins.json`
- `legacyforgebridge.rev255.mixins.json`
- `legacyforgebridge.rev258.mixins.json`

The branch currently has no rev254/rev255/rev258 production mixin JSON resources under
`src/main/resources`.

BuildInfo/cache revision and the local manifest metadata are likewise newer in the runtime JAR than
the branch source baseline.

## 6. Safe write-back policy

Safe now:
- preserve exact local sources/patchers/tests under `diagnostics/`;
- add audit documentation;
- recover a production revision only when all of its new source, modified existing source, and
  resource wiring are accounted for.

Not safe yet:
- copy rev262 directly into `src/main`;
- copy only the new rev256-rev260 helper classes while leaving the bytecode-patched callers stale;
- claim the branch can reproduce rev260/rev262 with Gradle/Loom.

## 7. Recovery order

1. Recover rev256 command/resource delta.
2. Recover rev257 sapling source-evidence delta.
3. Recover rev258 S12 scheduler + mixin wiring.
4. Recover rev259 cache/session/text changes.
5. Recover rev260 handoff/desktop-helper changes.
6. Recover the missing Corpus3 converter/runtime production sources.
7. Promote rev262 Block CreativeTab proof from diagnostics into the recovered production baseline.
8. Perform a full Gradle/Loom build and corpus regressions before declaring the source tree canonical.
