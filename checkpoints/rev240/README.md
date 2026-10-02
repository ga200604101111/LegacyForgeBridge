# rev240 — refresh legacy water caches before hiding the carrier model

Target branch: `feature/generic-conversion-iyamato-corpus3`.
Parent: `b7c914369c13df6cd51f37ac6eafe9d7f6ebfbce` (rev239).
Target runtime: Fabric Minecraft 1.21.11 / Java 21, unchanged Forge 1.7.10 server.

## Report and root-cause evidence

The user reported that spa water became invisible with the supplied rev239 main JAR.
rev239 queried liquid rules dynamically from the block's fluid getter and hid the
carrier model, but did not refresh already initialized BlockState fluid caches when
those rules were registered. A block can therefore return water while its existing
state still exposes cached EMPTY to the renderer: neither fluid nor model renders.

The lifecycle harness reproduces this condition using the actual rev239 helper
and rev233 registration helper from the supplied JAR, with Minecraft API test
doubles. This is a source/binary-backed reproduction of the cache defect, not a
claim that the user's exact game session or renderer was reproduced.

## Changes

- Keep source-driven `kind == WATER` admission; no Bamboo name or numeric-ID dispatch.
- Preserve the original rev233 registration, collision/selection and rev237 fallback tint.
- Build all metadata-state bindings before refreshing existing BlockState caches.
- Call `BlockState.initShapeCache` when client liquid rules are registered, then
  verify `BlockState.getFluidState` returns the expected native water state.
- Hide the carrier model only when the actual cached fluid matches that water.
  Stale/failed states retain the block model; other states can still refresh.
- Retain the exact vanilla source/flowing/falling mapping for metadata 0..15.
- Preserve carrier block/state identity, server authority and conversion-cache compatibility.
- Add bounded failure diagnostics and a `rev240 water cache: ..., refreshed=N/M` log.

The existing helper class name is retained intentionally to preserve the binary ABI.
The existing ConvertedLegacyBlock class itself is unchanged.

## Local build

This is a complete cumulative main JAR built incrementally over the SHA-pinned
user-supplied rev239 main JAR, not an auxiliary patch or an old renamed binary.
The new Java helper is compiled with `javac --release 21`. JDK-internal ASM makes
one exact registration-call edit and the version/revision edits. There is no full
Gradle/Loom rebuild, external dependency download, or GitHub Actions build.

Run from this checkpoint directory with Python 3 and a JDK 21 on PATH:

```sh
python3 build_local.py \
  /path/to/legacyforgebridge-0.2.0-alpha.27-rev239-corpus4-local.23.jar \
  /path/to/legacyforgebridge-0.2.0-alpha.27-rev240-corpus4-local.24.jar
```

Baseline SHA-256:
`42f7d5e8ba647c8f599fe9d152aeeab24e4fe813a08f55cdea75500766d48a3d`.
The script rejects other baselines and never packages its test-double classes.

## Delivered main JAR

- File: `legacyforgebridge-0.2.0-alpha.27-rev240-corpus4-local.24.jar`
- Size: 4,475,411 bytes
- SHA-256: `61afe2b2a21ff4ae2f2fae2afe3f95bf2013f19e2f3d461cd2577ed5c1fa8a45`
- Version: `0.2.0-alpha.27-corpus4-local.24-rev240-local-test.1`
- Converter revision: `2026-10-02.240-liquid-cache-refresh-visible-fallback`
- Cache compatibility: `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1` (unchanged)

The main JAR is delivered as the chat attachment. This repository checkpoint stores
its source, reproducible local build script, regression harness and artifact evidence.

## Validation actually performed

- SHA-pinned input, exact one-call-site patch guard and version guard: pass.
- Old helper reproduces cached EMPTY plus invisible model: 80 assertions.
- Fixed helper lifecycle: 406 assertions, also repeated using the packaged main JAR.
- All 16 metadata states, late rules, repeated registration, invalid rules/metadata,
  non-water rules, owner mismatch, cache exceptions, silent stale caches and recovery: pass.
- `java -Xverify:all` for harness execution and `javap -v` for changed/new classes: pass.
- ZIP integrity and duplicates: pass; 1,691 baseline entries, 1,694 output entries.
- Exactly four existing entries changed, three entries added, none removed.
- All other entry contents, including carrier class, manifest, nested Energy JAR,
  desktop helper, mixins and previous cumulative fixes: byte-identical.
- Independent second local build: byte-for-byte identical main JAR.

Minecraft APIs/signatures were checked against Fabric Yarn 1.21.11+build.4:
`class_4970$class_4971.method_26200` (cache initialization),
`method_26227` (cached fluid getter), `class_2248.method_9595`,
`class_2689.method_11662`, `class_3609.method_15729` / `method_15728`,
`class_3612.field_15910` and `class_2464.field_11455`.
The harness uses test doubles for Minecraft classes, not a running client.

**Not validated:** real Minecraft/Fabric/Sodium launch, in-game visuals and face
culling, a live Forge 1.7.10 server connection, shaders or resource-pack interactions.
If cache refresh fails, visibility is favored via the previous model fallback;
that fallback does not promise native-water transparency.

## Installation

Fully quit the client. Replace the rev239 LegacyForgeBridge main JAR in `mods`
with rev240; keep only one main LegacyForgeBridge JAR. Restart the client fully.
Keep original legacy mod JARs and the server unchanged. No conversion-cache wipe
or world edit is required by this checkpoint.

No workflow, PR, tag/release, main branch or Bamboo branch changes are part of this
checkpoint. Continuation commits must include `[skip ci] [skip actions]`.
