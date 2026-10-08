# 2026-10-08 — Twilight Forest Part 2G / rev287: source-proven TESR dynamic Y angle

Branch: `feature/generic-conversion-iyamato-corpus3`.

## Why this is generic

The legacy Moonworm can be a placed `Block` + TileEntity + TESR with a changing, source-owned yaw field. The MoonwormShot is a *different* projectile entity despite the shared model class. Rev286 recognized only the placed block's constant X/Z attachment rotations. This continuation adds a generic bytecode-causal proof for an optional third rotation reading one TileEntity field, **not** a Moonworm-specific renderer.

The production algorithm never branches on a mod ID, source class name, block ID, item ID, texture path, registry name or the spelling of the yaw field.

## New source implementation

Added `LegacyTileDynamicYawAnalyzer`:

1. Requires an exact, current-JAR revalidation of rev286's metadata-to-X/Z fixed rotation proof (including draw owner/signature). A fabricated or stale proof cannot be admitted.
2. Reads the exactly selected source TESR draw method. Its GL sequence must contain two independently source-proven static facing rotations followed by **exactly one** extra Y-axis rotation, with no fourth rotation.
3. Accepts only `GL11.glRotatef(sourceTileFieldAngle, 0F, 1F, 0F)` with a bounded, contiguous source expression: the draw TileEntity parameter, exact source-owned `GETFIELD`, optional matching `CHECKCAST`, `I2F` for a signed integer (or direct float field), and optional immediate `FSTORE/FLOAD` of the same computed value.
4. Source member declaration and inherited-source ownership must be proved on the TileEntity class hierarchy. Other receivers, static fields, unknown owners, missing definitions, invalid descriptors and arithmetic adjustments fail closed.
5. A bounded control-flow check requires the **field-read operand and the rotation call** on all paths to the draw's normal/exception exits; a conditional rotation, jump into the middle of the source expression, unsupported switch/loop or unproved side effect does not pass.

The proof records source tile, renderer, method, field owner/name/descriptor and operand family (`INT_FIELD_TO_FLOAT` / `FLOAT_FIELD`). It explicitly forbids claiming `tileFieldSyncProven=true` or `runtimeWired=true`.

## Integrated source-only output

`LegacyBlockTileModelPreflightPass` adds an optional evidence join, keyed by rev284's exact source-registered block identity and the rev286 draw owner/method proof. A rev285 visual-state audit can label whether the **same field name was observed** in its source tick-write intersection and whether a source packet *hook exists*. These are observations, not payload or server-to-client synchronization proof.

New per-candidate fields in `legacyforgebridge/block-tile-model-preflight.json`:

- `sourceDynamicYawOperandProven`;
- `sourceDynamicYawFieldOwner`, `sourceDynamicYawFieldName`, `sourceDynamicYawFieldDescriptor`;
- `sourceDynamicYawOperand`, `sourceDynamicYawUnconditionalSourceRotation`;
- `sourceYawFieldTickWrittenObserved`, `sourceYawFieldPacketHookObserved`;
- `sourceDynamicYawFieldSyncProven=false` and `sourceDynamicYawRuntimeWired=false`.

The root adds `sourceDynamicYawOperandCandidateCount`. Existing public manifest overloads remain source-compatible. `tileStateSyncProven=false`, `animationSemanticsProven=false`, `runtimeWired=false`, `blockEntityRuntimeWired=false`, per candidate `runtimeReady=false`, and *no executable rules array* remain unchanged. New optional diagnostics use `SupportLevel.AUTO`, so an unavailable source proof cannot downgrade a previously converted mod to PARTIAL/FAILED.

**Never replay original `TileEntity.updateEntity()` gameplay/server logic on the Fabric client to make the sprite spin.** A `getDescriptionPacket` presence alone is not payload and does not prove field synchronization; visual interpolation is a separate future stage.

## Executed standalone Java 21 tests

All eight source-only suites passed against Java 21, the pinned complete rev260 JAR classpath, temporary JDK-internal ASM namespace substitutions, and compact Gson/JUnit test doubles:

| Suite | Passed |
|---|---:|
| rev287 third-Y-rotation bytecode and fail-closed source cases | 19/19 |
| rev287 independent manifest/packet/runtime-negation cases | 9/9 |
| rev286 fixed 16-metadata facing regressions | 24/24 |
| rev286 manifest regressions | 6/6 |
| rev285 TileEntity visual-state regressions | 16/16 |
| rev285 manifest regressions | 6/6 |
| rev284 ordinary Block/TileEntity/TESR source regressions | 18/18 |
| rev284 manifest regressions | 2/2 |
| **Aggregate** | **100/100** |

These are compiled and executed synthetic tests, **not** an actual Gradle/Loom build, published ASM/Gson/JUnit dependency test, Minecraft/Fabric/ViaFabricPlus startup, exact translated Twilight Forest `-tw.jar` analysis, or unchanged Forge 1.7.10 server integration. Tests make no gameplay compatibility claim.

User-supplied complete rev260 main JAR remains unmodified:
`SHA-256 03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`.

## Outstanding acceptance gates

1. Recover the exact user-target `twilightforest-1.7.10-2.3.8-tw.jar` and check that its source bytecode actually matches both rev286 and rev287 analyzers. The publicly available upstream source is a reference **only**.
2. Source-prove the other legacy TESR transform steps, model per-part `setLivingAnimations` pivot math, negative scaling, and whether the animation requires unsynchronized TileEntity fields.
3. Prove a trustworthy source-to-client state channel, distinguish server-owned state from purely cosmetic animation, and implement a modern renderer without executing old server world/damage/tick behavior.
4. Recover rev256–rev260 local source overlays before a complete new main is built from the exact pinned full rev260 binary. Run real Java dependencies, Fabric 1.21.11 client and unchanged Forge 1.7.10 server acceptance before offering a new installable JAR.

This checkpoint is **source-only**. No Minecraft release, CI/Actions, PR, tag, force push, default/Bamboo branch modification, original JAR overwrite, or server edit was performed. Every commit contains `[skip ci] [skip actions]`.