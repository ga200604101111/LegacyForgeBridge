# 2026-10-08 — Twilight Forest Part 2F-3b: fixed-model projectile source proof

Branch: `feature/generic-conversion-iyamato-corpus3`

This checkpoint advances the rev278 four-renderer census by source-proving the simple fixed-model path **without** claiming an executable modern renderer, game conversion, or release JAR.

## External reference scope

Reference source: [Benimatic/twilightforest, commit `98b88bde74d6db0aa463dba304f5c13acb6140fb`](https://github.com/Benimatic/twilightforest/tree/98b88bde74d6db0aa463dba304f5c13acb6140fb). This is upstream Java source, **not the exact** `twilightforest-1.7.10-2.3.8-tw.jar` binary previously inspected. The exact `-tw` JAR was not available to re-run in this session, so source-to-shipped-bytecode equivalence remains unverified.

All identities below are **test evidence**, never a production class-name, mod-name, registry-ID or weapon-ID dispatch key.

## Fixed projectile renderer

- Source registration: `TFClientProxy.java:292`, `EntityTFMoonwormShot.class` -> `new RenderTFMoonwormShot()`.
- Entity registration: `TwilightForestMod.java:455`, `tfmoonwormshot`, tracking range 150, update 3, velocity updates true.
- `RenderTFMoonwormShot` constructs one `ModelTFMoonworm` and binds one model texture `twilightforest:textures/model/moonworm.png`.
- `doRender(Entity,DDDFF)` translates by the supplied world-relative coordinates, rotates **90 degrees around the source axis (1, 0, 1)**, then invokes `wormModel.render(0.075F)`. The axis has length sqrt(2); preserve its normalized direction when mapping to quaternion/matrix, not two successive axis rotations.
- The entity renderer does not use its yaw, partial tick, block renderer, item icon, RenderManager child loop, dynamic spin, or translucency APIs.
- `ModelTFMoonworm.render(float)` draws four `ModelRenderer` cuboids with fixed UV, local box and pivot data (see table). Declared logical atlas is 32x32.

| Part | UV | Local addBox origin | Dimensions | Rotation point |
| --- | --- | --- | --- | --- |
| Shape1 | (0, 4) | (-1,-1,-1) | (4,2,2) | (-1,7,3) |
| Shape2 | (0, 8) | (-1,-1,-1) | (2,2,4) | (3,7,0) |
| Shape3 | (0,14) | (-1,-1,-1) | (2,2,2) | (2,7,-2) |
| head | (0, 0) | (-1,-1,-1) | (2,2,2) | (-3,7,2) |

**Critical call-closure boundary:** `ModelTFMoonworm` also has `setLivingAnimations(TileEntityTFMoonworm,float)`, which mutates part pivots based on tile-entity animation state. The projectile renderer calls only `render(float)`, not that animation method; the same *model class* being animated elsewhere is not evidence of a dynamic *projectile render path*. A generic analyzer must prove its selected entrypoint and reject mutations in that reachable draw closure. It must not globally ignore model animation methods.

## Unique launcher source shape and server authority

- `TFItems.java:218,329` constructs and registers `ItemTFMoonwormQueen`.
- `onItemRightClick` only starts item use. `onPlayerStoppedUsing(ItemStack,World,EntityPlayer,int)` checks elapsed charge time (>12 ticks), world server side (`!world.isRemote`), and item durability; the source then directly calls `world.spawnEntityInWorld(new EntityTFMoonwormShot(world,player))`.
- This is a **release-use** launcher, not an immediate right-click spawn. The launcher also has a distinct `onItemUse` block-placement path; do not merge these game behaviors.
- Projectile `onImpact` performs source-side placement/interaction and particles, with server lifecycle ownership. **Never** execute legacy impact, block placement, damage, or entity spawning on the Fabric client.

## Additional presentation semantics to preserve or gate

- Source entity overrides `getBrightness(float)=1.0F` and `getBrightnessForRender(float)=15728880`, indicating full-bright intent. A fixed-mesh adapter cannot claim visual parity until modern light handling is explicitly mapped/proved.
- Source `makeTrail()` implementation has only commented-out particle statements in the pinned upstream source. This is **not** proof about a modified translated binary.
- Texture existence, actual PNG dimensions, bytecode field descriptors, model constructor and render method shapes, unique renderer identity and unique registered launcher all require rechecking against exact corpus binary before promotion to runtime.

## General converter admission contract

A future `FIXED_MODEL_PROJECTILE` route must require **all** of these:

1. Entity is source-registered through a proven lifecycle, has the supported projectile base and proven FML spawn/watcher envelope, no unsupported additional spawn payload;
2. precisely one source-owned renderer registration and unique registered item launcher with bytecode dataflow from a supported use/release callback through exact projectile construction to `World.spawnEntityInWorld`;
3. a source-local `Render` subclass allocates one source-local `ModelBase`, with one bound, present texture; verifies renderer draw contains only the supported push / translation / constant-axis rotation / texture bind / model draw / pop path;
4. prove every `ModelRenderer` cuboid has one constructor UV, one static `addBox`, one pivot and one draw in the **reachable** `render(float)` method; detect unproved part mutation, nested callbacks, branches, child entities and extra render state;
5. serialize UV, cuboids, pivot, model scale, fixed orientation, texture, light behavior and provenance into a dedicated presentation sidecar, separate from `THROWN_ITEM` and `ORIENTED_ITEM`;
6. construct a modern client-only mesh renderer with correct texture and unit-safe axis-angle orientation, no client simulation of projectile gameplay;
7. synthetic renamed tests, missing/ambiguous asset and dynamic/multipart rejection tests, exact-`-tw` analyzer evidence, full source restoration (rev260 handoff gap), then a real Loom build and in-game visual acceptance.

Threw ice / chain block / annihilation cube must remain independently gated as block-backed, multipart, and dynamic translucent renderer families.

## Validation boundary

This is a source/evidence checkpoint only. Neither a complete main JAR nor a live Fabric/VFP or original server test is implied. See `docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md` before attempting any full production build.
