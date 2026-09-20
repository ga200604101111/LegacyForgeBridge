# 2026-09-20 — Cloth Config blocking-pose editor (.145)

## User-facing behavior

- Cloth Config exposes enabled, left-hand mirroring, first-person translation/rotation and third-person translation/rotation as in-game controls.
- The default rotations are first person `[0, 20, 0]` and third person `[-30, 40, 40]`; translation defaults to zero.
- Scale controls were removed. Existing schema-1 files are accepted once, their scale vectors are validated and discarded, then the file is rewritten as schema 2.
- Saving the screen writes and installs one immutable settings snapshot immediately. The previous 20-tick file timestamp polling was removed.
- The screen is available from Mod Menu when installed and from the rebindable `O` key in vanilla Controls.

## Rendering boundary

The correction is admitted only while an item is actively using `ItemUseAnimation.BLOCK`, and shields are excluded. It therefore follows the animation decision already made by Via/vanilla and does not require LegacyForgeBridge metadata. Original/Via swords and converted mod weapons share the same global configuration.

Translation now uses JOML `translateLocal` on the current pose matrix. This pre-multiplies the offset in parent/screen axes after Via/vanilla has established the sword pose, instead of rotating the offset through the sword's local axes. Rotation remains an appended visual transform. No use action, packet, damage, attack blocking or Via internals are modified.

## Verification

The ordinary Gradle build compiles the Cloth Config screen and optional Mod Menu entry, runs schema migration/default/matrix-axis tests, pins the exact 1.21.11 render injection descriptors, and verifies both mixins follow the generic non-shield BLOCK animation without consulting LFB metadata.
