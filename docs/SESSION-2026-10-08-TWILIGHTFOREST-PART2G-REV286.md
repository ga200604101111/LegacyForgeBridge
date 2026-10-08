# 2026-10-08 — Twilight Forest 2.3.8 Part 2G / rev286: source-proven six-face TESR orientation

Branch: `feature/generic-conversion-iyamato-corpus3`

## Objective and implementation

Moonworm is a **placed legacy Block with an animated TileEntity**, while MoonwormShot is a **separately registered projectile Entity**. Sharing `ModelTFMoonworm` does not make these the same conversion family. No Twilight Forest names, IDs, or texture paths are used as production admission keys.

Added `LegacyTileFacingRotationAnalyzer` for the generic rev284 `LegacyBlockTileModelPreflight.Candidate` family. It reads source-owned TileEntity/TESR bytecode without running original classes, checks TileEntity and TileEntitySpecialRenderer ancestry (including multi-hop source superclasses), and admits exactly one directly reachable draw method or one canonical, correctly typed delegated helper. Bounded, path-specific execution of **only** the metadata-comparison and fixed-angle assignment prelude derives exact `metadata 0..15` source poses, including an explicitly demonstrated `&7` mask.

The two accepted fixed-axes must be unconditional, ordered calls:
1. `GL11.glRotatef(rotationX, 1, 0, 0)`
2. `GL11.glRotatef(rotationZ, 0, 0, 1)`

The proof rejects missing/duplicate source metadata reads, unsupported branches, loops/backward jumps, class or cast mismatches, changed metadata locals, dynamic angles, unknown JVM method calls, GL state mutations in the facing prelude, hidden source wrapper transforms, invalid axes, extra writes between the fixed rotations, unbounded bytecode and static maps with no facing variation. A possible **third/dynamic** source GL rotation is recorded but NOT executed or interpreted as synchronized.

`LegacyBlockTileModelPreflightPass` now publishes the independent source mapping inside its **existing, source-only** `legacyforgebridge/block-tile-model-preflight.json` file:

- per candidate: `sourceFacingMapProven`, `sourceFacingRendererRuntimeWired=false`, `sourceFacingMetadataMask`, `sourceFacingDrawMethod`, `sourceFacingDrawDescriptor`, `sourceAdditionalGlRotationPresent`, and `sourceFacingMap0to15` with metadata/X/Z angle entries;
- root: `sourceStaticFacingMapCandidateCount`;
- continued hard gates: `runtimeWired=false`, `blockEntityRuntimeWired=false`, `modernBlockGeometryProven=false`, `tileStateSyncProven=false`, `animationSemanticsProven=false`, per candidate `runtimeReady=false`, and **no executable `rules` array**.

Candidate identity must match both source TileEntity and renderer classes before emitting a map. Missing/unproved mappings are not guessed. Optional evidence still uses `SupportLevel.AUTO`, so the audit cannot downgrade unrelated original candidate conversion status.

## Upstream source reference (not an exact translated-JAR assertion)

Benimatic Twilight Forest 2017 upstream source has `BlockTFMoonworm` extending regular `BlockTFCritter`; its `hasTileEntity` / `createTileEntity` creates a TileEntity. `TileEntityTFMoonwormRenderer` computes `rotX,rotZ` from metadata, then **also** invokes `glRotatef((float)currentYaw,0,1,0)`, scales `(1,-1,-1)`, and calls `ModelTFMoonworm.setLivingAnimations(tile,partialTime)`.

For the upstream source shape, the static-facing expectation is:

| Legacy metadata | X rotation (degrees) | Z rotation (degrees) |
|---:|---:|---:|
| 1 | 90 | -90 |
| 2 | 90 | 90 |
| 3 | 90 | 0 |
| 4 | 90 | 180 |
| 5 | 0 | 0 |
| 6 | 180 | 0 |
| 0, 7–15 (without masking) | 90 | 0 |

An explicit source `&7` mask produces different mappings for high-bit metadata, and rev286 proves the difference instead of assuming one. **This table is from upstream Java source and generic synthetic tests, not direct analysis of `twilightforest-1.7.10-2.3.8-tw.jar`.**

Source:
- `BlockTFMoonworm.java`, `TileEntityTFMoonworm.java`, `TileEntityTFMoonwormRenderer.java`, `ModelTFMoonworm.java` at Benimatic/twilightforest commit `98b88bde74d6db0aa463dba304f5c13acb6140fb`.

## Local evidence / test boundaries

The new Java sources were compiled under Java 21 against rev284/rev285 signatures and the user-supplied, pinned **rev260 complete main JAR** classpath, using temporary JDK-internal ASM import substitution and Gson/JUnit test doubles:

| Suite | Executed | Passed |
|---|---:|---:|
| rev286 metadata facing/GL branch source tests | 24 | 24 |
| rev286 sidecar manifest + runtime-negation tests | 6 | 6 |
| rev285 visual-state source analysis regressions | 16 | 16 |
| rev285 visual-state manifest regressions | 6 | 6 |
| rev284 Block+Tile+TESR registrations and binding regressions | 18 | 18 |
| rev284 manifest regressions | 2 | 2 |
| **Total** | **72** | **72** |

All source files were verified against the exact GitHub blob SHA after push. This is **not** a full real-ASM, Gradle/Loom, Minecraft 1.21.11/ViaFabricPlus or Forge 1.7.10 multiplayer run. The exact translated Twilight Forest `-tw.jar` was not supplied. No living mobs/projectiles or placed blocks became newly playable through this source-only work.

The original complete rev260 user JAR stays byte-identical: SHA-256 `03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`. This rev286 checkpoint produces **no** new installable full main JAR.

## Next hard gates

1. Source-prove remaining TESR transform sequence: block translation/centering, fixed static face rotations, dynamic yaw, negative scale, matrix push/pop, lighting and per-part animation as **separate** bounded stages.
2. Resolve whether every TileEntity render-state field is packet/NBT-sourced, can be deduced from authoritative block metadata, or must be suppressed; never execute old server gameplay tick logic on modern clients.
3. Source-prove metadata-dependent block bounds/collision/placement and connect client render pose with the exact modern block model.
4. Run exact translated Twilight Forest JAR; recover rev256–rev260 build overlays; compile against real Fabric/Loom APIs, then execute real client + unchanged Forge 1.7.10 acceptance.
5. Only then use the pinned rev260 complete JAR as an overlay base for a *genuinely compiled and validated* newer main JAR.

No GitHub Actions/PR/tags/releases were created, no force-push, and no modification was made to main/Bamboo, original Twilight Forest, user rev260 main JAR, or Forge server. Every commit includes `[skip ci] [skip actions]`.
