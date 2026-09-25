# 2026-09-23 — rev180: vanilla arrow renderer delegation

Base: `cec11ab76e2cce2bcd76e891008801b6dc0d44a9` / converter rev179.
Branch: `feature/generic-conversion-bamboo-corpus2`.

## User feedback and scope

The user confirmed rev179 still fails the reported Bamboo live checks. In particular, projectile
presentation should follow vanilla arrow orientation, rather than rotating an inventory icon.
This slice handles arrow-family presentation only. Trays, bamboo shoots/blocks, wind/water wheels,
held-only fox-fire selection and bamboo-bow pull textures remain OPEN; this change does not fix them.

## Implementation

- `ConvertedLegacyOrientedProjectileRenderer` delegates submission directly to the target
  Minecraft `ArrowRenderer`. That renderer owns the vanilla arrow model and axis transforms.
- A subclass of `ArrowRenderState` carries the remote pose and source entity texture. The
  source-proven fixed texture is used directly, not an inventory icon or item atlas. The vanilla
  arrow texture is the explicit fallback when an older candidate has no fixed texture.
- Packet yaw/pitch are interpolated with vanilla `Mth.rotLerp`, including the +/-180 seam.
  The last spawn velocity no longer overwrites later server orientation or a landed arrow's pose.
- FML spawn initializes both current and previous angles before adding the carrier to the level.
- No vanilla Arrow entity is created or ticked. Damage, flight physics, impact, pickup and removal
  remain authoritative on the legacy server. No missing impact/shake state is invented.
- The v1 `ORIENTED_ITEM` adapter name is retained for candidate compatibility. Throwables still
  use `ThrownItemRenderer`. Ordinary vanilla arrow entities outside this registry are untouched.
- There are no Bamboo mod IDs, item names or source class names in the production change.

This deliberately reuses the vanilla arrow mesh; it is not proof that every custom legacy
immediate-mode mesh has been reproduced exactly. Custom texture UV appearance remains a live-check
item. No texture is guessed from a model/item name.

## Regression coverage

State tests cover cardinal/diagonal/vertical shots, interpolation, both angle-seam directions,
stationary final orientation and source entity texture/fallback selection. Bytecode tests verify
actual vanilla renderer inheritance/submission, the exact target descriptor/model, spawn endpoint
initialization and the absence of inventory-model rendering or extra gameplay projectiles.

## Validation boundary

Commit this slice as one batch and then run the full Gradle build/test/remap workflow once. A green
build is not a Minecraft live test and is not closure of the user's Bamboo issue list. Do not mark
this slice live-verified until the user tests the resulting JAR on the legacy server.

Suggested in-world check: fire in four cardinal directions, diagonally, up and down; observe arrows
from multiple camera positions, during ascent/descent and after impact. They must not face the
camera, jump through a full turn, or change angle because an old spawn velocity is reused.
