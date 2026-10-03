# LegacyForgeBridge rev249 — Via serverbound full-chain diagnostics

Base artifact: rev248 SHA-256 `480964a8cd00a7bc34417fcdadf3ffcacc7cdb464aa0bd477ade9d4015679bfe`.

Delivered artifact: `legacyforgebridge-0.2.0-alpha.27-rev249-corpus4-local.32-via-outbound-diagnostic.jar`

Artifact version: `0.2.0-alpha.27-corpus4-local.32-rev249-via-outbound.1`.
Converter semantic revision remains `2026-10-03.245-deep-motion-correlation-diagnostics`; conversion/cache identity is intentionally unchanged.

## Purpose

Observation-only diagnostic release for the remaining jump desynchronization. It does not attempt a jump repair.

For every ViaFabricPlus `UserConnectionImpl.transformServerbound` invocation whose final 1.7.10 bytes decode as C03/C04/C05/C06 player movement, rev249 emits one exact pair with the same `outCorr`:

- `OUTBOUND_MODERN_PRE_VIA`: exact input bytes seen before Via transforms the serverbound packet, packet-id VarInt, a clearly-labelled 1.21.x movement-layout candidate decode, and a temporal local-player state snapshot.
- `OUTBOUND_1710_POST_VIA`: exact final bytes after Via, exact retained 1.7.10 C03-family decode, transform duration, and a temporal local-player state snapshot.

When an inbound local-player legacy S12 is logged as `RAW_1710_MOTION`, the record also lists up to four most recent movement `outCorr` values within the previous 1000 ms, including their legacy Y/onGround and candidate modern onGround/horizontalCollision fields. These are **temporal candidates only** and every record states `outboundResponseCausalProof=false`.

The modern semantic decoder is explicitly a packet-length/layout candidate. `modernRaw` is the exact byte evidence. The final C03-family decoder is the retained exact legacy layout parser. No unrelated serverbound traffic is logged by the new rev249 flow logger.

## Safety / non-mutation boundary

No velocity is suppressed or rewritten. No player position, onGround, collision flag, movement packet, Via buffer, send cadence, source jump event, original mod, or server state is changed. rev248 startup version selection remains intact. rev247/rev245 movement diagnostics remain intact through delegation.

## Runtime test

Replace rev248 with rev249 in the 1.21.11 Fabric client. Keep the same ViaFabricPlus, old-mods, server, character and equipment. Motion capture still auto-starts on the legacy connection, so no manual marker command is required. Reproduce ordinary jumps and the known platform case, exit/disconnect cleanly, then preserve `logs/lfb-motion/`.

For GPT-6 analysis, correlate `OUTBOUND_MODERN_PRE_VIA` ↔ `OUTBOUND_1710_POST_VIA` by `outCorr`, then inspect `RAW_1710_MOTION` for `out1Corr`...`out4Corr`. Compare normal first S12 and abnormal repeated positive S12 cases. Do not treat the nearest outbound flow as proven server cause solely because it falls inside the 1000 ms window.

## Executed validation

- Exact-base guarded build; ZIP integrity and duplicate-entry checks pass.
- 22 rev248 startup scenarios / 241 assertions pass.
- Existing diagnostic regression: 20 scenarios / 643 assertions pass.
- Existing cache: 25 assertions pass.
- Actual packaged Swing DesktopHelper: 145 checks each on main JAR, nested helper and 2x scaling.
- Capture context: 4 assertions pass.
- rev249 modern movement candidate decoder/context helpers: 19 assertions pass.
- rev249 synthetic packaged flow using explicit game/writer doubles: modern raw -> post-Via C03 -> inbound S12 context, 13 assertions pass.
- Nine protected gameplay/startup diagnostic classes are byte-identical to rev248.
- Independent second offline build is byte-for-byte identical.

There was no live Minecraft/Via pipeline execution in the build environment; the user's test is the first real VFP runtime validation of the two new `transformServerbound` hooks. Test doubles are not packaged in the main JAR.
