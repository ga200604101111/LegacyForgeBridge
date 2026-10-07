# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2F-3a checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Scope: classification only for the four item-launched custom-renderer projectile candidates left
after the fixed vanilla RenderSnowball slice.

No new runtime adapter is implemented by this checkpoint.

## Remaining four item-launched custom-renderer candidates

### 1. tfmoonwormshot / EntityTFMoonwormShot

Source renderer:

- fixed source texture;
- one fixed model;
- simple fixed transform/orientation;
- no child-entity render loop;
- no RenderBlocks block-backed presentation;
- no dynamic tick/position driven spin family.

This is the cleanest next target and is a good candidate for a generic
`FIXED_MODEL_PROJECTILE` presentation adapter, provided the source model parser and unique launcher
item proof both succeed.

Expected launcher family to verify next:
`ItemTFMoonwormQueen`.

### 2. tfthrownice / EntityTFThrownIce

Source renderer uses `RenderBlocks` and renders a block selected from the entity, rather than a
fixed item/model presentation.

This is a distinct **block-backed projectile** semantic family and must not be admitted by relaxing
the existing item-icon or fixed-model rules.

### 3. tfchainBlock / EntityTFChainBlock

Source renderer draws the main model and also delegates rendering of multiple chain child entities
through RenderManager.

This is a **multipart / child-entity projectile** presentation family. It requires explicit proof of
the child relationship and cannot be represented by a single-item or single-model carrier without
losing visible behavior.

### 4. tfcubeannihilation / EntityTFCubeOfAnnihilation

Source renderer uses a fixed model/texture but also applies time/position-driven rotation plus
blending/light-state behavior.

This is a **dynamic rotating translucent model projectile** family, separate from the simple fixed
model case.

## Conclusion

The four remaining item-launched custom renderer candidates are not one structural family.

Do not create a broad "custom throwable" adapter.

Recommended next slice:

1. verify MoonwormShot's exact fixed model geometry/texture/orientation proof;
2. verify exactly one registered launcher item directly constructs/spawns MoonwormShot;
3. if both are unique, implement a generic `FIXED_MODEL_PROJECTILE` adapter with renamed synthetic
   regression coverage;
4. keep ThrownIce, ChainBlock and CubeOfAnnihilation fail closed until their own semantic families
   are separately designed.

This checkpoint intentionally stops before modifying production runtime.
