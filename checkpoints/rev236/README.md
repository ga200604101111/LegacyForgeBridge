# rev236 — preserve legacy 1.7.10 client jump prediction

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev236 is a cumulative binary overlay on the exact rev235 artifact.

## Root cause found

The exact RPGTool corpus proves its jump handler is:

- Forge `LivingJumpEvent`;
- if the player wears `WingBase` in legacy chest slot 3;
- `motionY += 0.15D`;
- `isAirBorne = true`.

Minecraft/Forge 1.7.10 ordering is:
1. vanilla `EntityLivingBase.jump()` sets about `motionY = 0.42` (plus vanilla jump potion/sprint adjustments);
2. Forge posts `LivingJumpEvent` after vanilla jump;
3. RPGTool adds `+0.15`, giving about `0.57` for an ordinary wing jump.

The unchanged 1.7.10 server also detects the ground->air upward transition from C03 movement and calls the server-side player's `jump()`, so the event is valid on both entity copies.

rev235's client compatibility layer did something that original 1.7.10 does not do: after executing the converted client jump event, it detected an upward Y delta and immediately restored Y velocity to the pre-event value under the trace label:

`SOURCE_UPWARD_CLIENT_PREDICTION_SUPPRESSED`

That erased RPGTool's `+0.15` client prediction.

## rev236 repair

- Preserve the complete velocity result of the converted client `LivingJumpEvent`.
- Do not force a fixed jump height.
- Do not cancel incoming velocity packets.
- Do not rewrite incoming X/Y/Z velocity packets.
- Position corrections remain authoritative.
- Existing jump/motion trace instrumentation remains active.
- The binary patch keeps the original method control-flow/frame shape; only the suppression threshold is made unreachable.

This follows the old model more closely: the local client predicts its own jump and source event; any real server velocity/correction packet that actually arrives is still applied normally.

## Why a 1.7.10 JumpProbe is not required before this fix

The current primary defect is already proven inside LFB itself, so a separate diagnostic mod is not needed merely to discover this bug.

A paired 1.7.10 JumpProbe is still useful if live rev236 differs after this repair. It should record:
- client jump before/after Forge event;
- outbound C03 Y/onGround;
- inbound S12 velocity;
- inbound S08 position correction;
- server jump detection and server-side LivingJumpEvent;
- exact packet ordering/ticks.

That would distinguish a Via translation difference from ordinary 1.7.10 behavior without guessing or canceling packets.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev236-corpus4-local.20.jar`
- bytes: `4,456,800`
- SHA-256: `abddbcee9af6743fe9ac1f8a1b7a8074377f1c25c78bc7b7d7feaeb086003a4c`
- internal version: `0.2.0-alpha.27-corpus4-local.20-rev236-local-test.1`
- converter revision: `2026-10-01.236-preserve-legacy-client-jump-prediction`
- cache compatibility unchanged from rev233.

## Binary audit vs rev235

- base entries: 1683
- output entries: 1684
- duplicate entries: 0
- removed entries: 0
- added: `legacyforgebridge/rev236-build.json`
- changed existing:
  - `dev/yinghuang/legacyforgebridge/BuildInfo.class`
  - `dev/yinghuang/legacyforgebridge/behavior/LegacyClientJumpMotion.class`
  - `fabric.mod.json`
- manifest unchanged
- nested Energy JAR unchanged
- rev233 Bamboo liquid classes unchanged
- rev235 armor classes unchanged

## Validation

- ZIP integrity: pass
- `javap` parse of patched jump/runtime classes: pass
- jump method StackMap/control-flow shape: unchanged
- incoming motion mixin contains no `CallbackInfo.cancel()`: verified
- live Minecraft launch: not available in the build environment

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
