# rev245 — deep motion correlation diagnostics

Branch: `feature/generic-conversion-iyamato-corpus3`.
Parent checkpoint commit: `6bad868a91e353cb2172acc57c48709b83e524e0`.
Target: Fabric Minecraft 1.21.11 / Java 21 connecting to an unchanged Forge 1.7.10 server.

This is still **observation-only**. It does not claim the unwanted second upward lift is fixed, and it does not restore the rev241/rev242 velocity-history heuristics. Original source jump handling and the rev242 landing/particle adapter remain unchanged.

## What rev245 adds

The high-volume motion log now carries `capture`, `captureAgeNs` and `threadId` on every record. A new read-only correlator annotates the already existing motion stages:

- `RAW_1710_MOTION` -> `VIA_OUTPUT_MOTION`: exact legacy `wire` identifier.
- `NETTY_PACKET_DISPATCH` -> `CLIENT_APPLY_HEAD/TAIL`: exact modern packet-object identity when exposed by the existing hooks.
- `VIA_OUTPUT_MOTION` -> modern packet object: vector + monotonic time + order **candidate only**. The log explicitly labels this `vector_time_candidate`; it is not server causal proof.
- Subsequent tick, move, camera, velocity/position-write and source-event observations can carry `afterVelocityCorr` for a bounded 1.5 s post-apply window.
- Positive-Y client applies get `upwardOrdinal`. A second positive apply before an observed ground reset / within the same local jump can be marked `secondLiftCandidate=true` with `causalProof=false` and the previous correlation id/time delta.

The rev244 diagnostic corrections are included: `field_70133_I` is logged as `velocityChanged`, observer counters are capture-local, stopped/non-legacy/off-thread callbacks do not inflate tick-start coverage, and pending move/failure-dedup state is reset between captures.

## Exact full main artifact built locally

The build used the user-supplied complete rev243 main JAR as the only binary baseline:

- Baseline: `legacyforgebridge-0.2.0-alpha.27-rev243-corpus4-local.27-diagnostic.jar`
- Baseline SHA-256: `87d4e3cbe4939b21c4eb955b66508bbc2724aafbf6b557c87245cc345121ef63`
- Output: `legacyforgebridge-0.2.0-alpha.27-rev245-corpus4-local.28-deepdiag.jar`
- Output SHA-256: `92d00685e9316946353bf410455e57f14a15fd4a903ce5eed749b4fafd658ae6`
- Output bytes: `4,527,570`
- Version: `0.2.0-alpha.27-corpus4-local.28-rev245-deepdiag.1`
- Converter revision: `2026-10-03.245-deep-motion-correlation-diagnostics`

No Gradle/Loom, Maven download, GitHub Actions, original-mod change or server change was used. `build_local.py` rejects any baseline whose SHA-256 differs.

Only nine pre-existing entries changed: BuildInfo metadata, file/FML logger metadata, LegacyClientJumpMotion diagnostic literals, LegacyMotionTraceLog, Rev243Diagnostics + its ReadApi nested class, Rev243TraceWriter diagnostic literals and `fabric.mod.json`. Three correlation classes plus `rev245-build.json` were added. The remaining **1,704 baseline entries were content-identical**. No entries were removed.

Two independent builds from the checked-in `build_local.py` were byte-for-byte identical to the final output.

## Validation actually run

Packaged JAR checks, not just source checks:

- ZIP integrity and duplicate-entry check: pass.
- All 11 changed/added Java class files parse with `javap` and are Java 21 class version 65.
- BuildInfo, file logger, FML trace, motion diagnostics, writer banner and packet-apply action labels all report rev245 metadata.
- The existing 20 rev244 diagnostic regression scenarios all pass against the packaged rev245 classes. Their assertion counts total 643, including 600 read-only checks over 200 varied player states.
- New correlation suite: 37 assertions pass for exact wire linking, vector/time candidate linking, exact packet-object linking, apply timing, post-apply context, second-positive-Y candidate tagging, ground reset, unmatched Via output and explicit non-causal labeling.
- Real `Rev243TraceWriter` disk test: 8,006 accepted / 8,006 written, queue drained, `dropped=0`, `truncated=0`, `discarded=0`, TRACE_END present. The 8,000 generated raw/Via motion events all carried correlation annotations.
- Independent rebuild SHA-256 matched exactly.

The regression suites use explicit Minecraft/Fabric test doubles. The real-writer test uses the actual packaged disk writer. There was **no live Minecraft/Fabric launch, no live ViaFabricPlus pipeline, no original Forge 1.7.10 paired client, and no server-side call-site tracing** in this environment.

## How to capture the next useful log

Install only the rev245 main JAR in place of the previous LegacyForgeBridge main JAR. Keep the original legacy mod JARs, conversion cache and server unchanged.

Capture starts automatically on the legacy session. For the jump issue, do ordinary jumps and wing jumps under the same conditions where the second lift occurs. When you visibly feel/see the unwanted second lift, immediately use `/lfbtrace mark`. Do this for several bad jumps and a few normal jumps in the same capture. `/lfbtrace stop` closes/drains the capture. Send the complete timestamped files under `logs/lfb-motion/` plus `logs/legacyforgebridge.log`.

Do not edit or excerpt the motion files before sending them. A USER_MARK is useful but is not required for every jump. This build already captures raw/Via/apply/tick/move/camera context continuously, so repeated identical 1.21.11 tests are no longer needed after enough marked good/bad examples are collected.

## Remaining boundary

Even with rev245, the conversion gap from Via output bytes to the later modern packet object has no shared exact identifier in the retained hooks. Matching across that gap is therefore intentionally marked as a candidate. The log still cannot see the Forge 1.7.10 server call site or server tick that created a velocity packet. A paired original 1.7.10 client remains valuable if we need to determine whether the duplicate positive velocity is server behavior or a modern-client compatibility difference.

Do not use these diagnostics as evidence that the root cause is fixed. Continue on the designated feature branch only, with `[skip ci] [skip actions]`; do not dispatch Actions, open a PR, create a tag/release, change main/Bamboo, or modify the unchanged server/original mods.

Reproducible packaged tests: `python3 checkpoints/rev245/test_local.py /path/to/legacyforgebridge-0.2.0-alpha.27-rev245-corpus4-local.28-deepdiag.jar`.
