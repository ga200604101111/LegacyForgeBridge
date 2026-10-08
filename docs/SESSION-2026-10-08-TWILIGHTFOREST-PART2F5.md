# 2026-10-08 — Twilight Forest Part 2F-5 / rev283 (static cuboid mesh and fullbright proof)

Branch: `feature/generic-conversion-iyamato-corpus3`.

## Scope and invariant

This is **source-only** engineering work on Minecraft 1.7.10 `ModelBox` cuboid presentation and exact constant full-bright light intent, preparing a generic Fabric 1.21.11 remote projectile renderer. No original-mod, Forge server, gameplay authority, transport, old JAR, or executable renderer is changed. All existing source-only preflight candidates remain `runtimeReady=false`, `runtimeWired=false`, not elements of the active projectile `rules` array.

No Twilight Forest entity, mod name or registry numeric ID is a production dispatch key.

## Generic geometry semantics

`src/main/java/dev/yinghuang/legacyforgebridge/convert/LegacyModelBoxMesh1710.java` compiles source-proven `LegacyFixedModelProjectileAnalyzer.Proof` cuboids into bounded six-face quads, with:

- exact 1.7.10 ModelBox six face vertex ordering (original `quadList[0..5]`);
- exact 1.7.10 `TexturedQuad` assignment of normalized atlas UV, including inverted top-face V;
- per-part constructor `addBox` origin plus `setRotationPoint` pivot, multiplied by the renderer's proven `render(scale)` factor;
- constant renderer GL axis-angle normalized and baked into the vertex positions and normals **exactly once**;
- source `TexturedQuad.draw()` normal winding;
- immutable output with `texture`, `atlasWidth/Height`, `quads`, `vertexCount`, `triangleCount` and `sourceAxisAngleBaked` provenance.

The converter fails closed for unsafe/unknown texture paths, atlas dimensions above 2048, UV rectangles extending outside the actual image, nonfinite values, degenerate faces, zero rotation axis, invalid per-part sizes, ambiguous duplicate part names, or more than 64 cuboids. It does **not** infer mirrored/animated parts, modern material, lighting, entity registration or legacy gameplay from these quads.

Proof references (historical implementation, not a modern model API):

- 1.7.10 ModelBox `quadList[0..5]`: https://www.javatips.net/api/MoKitchen-master/minecraft/net/minecraft/client/model/ModelBox.java
- 1.7.10 TexturedQuad vertex UV and normal computation: https://www.javatips.net/api/TheMinecraft-master/minecraft/net/minecraft/client/model/TexturedQuad.java

`LegacyFixedModelProjectilePreflight` now requires this bounded mesh proof before publishing a geometry candidate. A UV-unsafe model is explicitly skipped with a source reason. The existing `LegacyProjectilePresentationPass` diagnostic sidecar gains `legacyModelBoxFaceProof`, `modelBoxFaceCount`, `modelBoxVertexCount`, `modelBoxTriangleCount`, and `sourceAxisAngleBakedIntoMesh` fields; the previous original-item projectile output remains unchanged.

## Generic source fullbright proof

`LegacyProjectileFullbright1710Analyzer` inspects only source-local brightness getter bytecode, accepting **exactly**:

- source `getBrightness(F)F` / SRG `func_70013_c` returning constant 1.0F;
- source `getBrightnessForRender(F)I` / SRG `func_70070_b` returning constant `0x00F000F0` (15728880).

Source-owned getter uniqueness is required. Missing methods, dynamic getters, different constants, alias collisions and damaged classes fail closed. This evidence is **optional**, preserving the geometry candidate even when lighting is unknown. It never installs a modern shader/lightmap hook. The `LegacyFixedModelProjectilePreflight.Candidate` record now carries `fullbrightProof` independently from `launcherProof`, with overloads preserving existing synthetic fixtures.

Preflight sidecar additions:

- per candidate: `sourceConstantFullbright1710Proven`;
- if proven: `legacyBrightness`, `legacyPackedLight`, `sourceBrightnessGetter`, `sourcePackedLightGetter`;
- aggregate: `sourceConstantFullbrightCandidateCount`.

These values describe the **legacy source**, not a completed 1.21.11 lighting implementation. Existing safety fields remain false.

## Exact upstream reference vs local translated JAR

The upstream Java code at [Benimatic/twilightforest commit 98b88bde](https://github.com/Benimatic/twilightforest/tree/98b88bde74d6db0aa463dba304f5c13acb6140fb) describes `RenderTFMoonwormShot` applying `glRotatef(90F,1F,0F,1F)`, drawing `ModelTFMoonworm.render(0.075F)`, and `EntityTFMoonwormShot` returning 1.0F / 15728880. `ModelTFMoonworm` has four fixed cuboid parts; its unrelated TileEntity-only animation entrypoint does not invalidate the source projectile drawing closure.

The offline reference OBJ `tf_moonworm_upstream_reference_geometry.obj` uses **those upstream source dimensions** and contains **4 parts, 24 quads, 48 triangles, 96 independent vertices**, with UV coordinates and face normals. It contains **no copied texture**, and it is **not** the actual Fabric client renderer.

The exact user-specific `twilightforest-1.7.10-2.3.8-tw.jar` was not available in this session, so **no exact source-JAR admission or bytecode parity is claimed**. The original uploaded full LegacyForgeBridge rev260 JAR is **not** the Twilight Forest JAR.

## Local regression results (Java 21)

- Old-main release packaging/structural preservation: **22/22** Python unit tests.
- rev281 launcher/source renderer preflight, retested with mesh/light prereqs: **16/16** renamed synthetic bytecode tests.
- rev283 ModelBox/UV/pivot/axis-angle/bounds: **10/10** direct Java 21 tests.
- rev283 packed light/brightness source getter classification: **7/7** synthetic Java 1.7 class-bytecode tests.

Total independent checks executed in the continuation: **55**. ASM-oriented synthetic tests temporarily use the Java 21 JDK-internal ASM-compatible namespaces, **not** a real Fabric 1.21.11/Gradle/Loom/JUnit runtime; manifest JUnit assertions added to GitHub are source regressions, **not claimed executed** by this total. The uploaded original rev260 main remains byte-identical, SHA-256:
`03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`.

## Next hard gates

1. Obtain the **exact** Twilight Forest `-tw.jar`, check its SHA-256, run bytecode-backed entity/renderer/item/lighting source proofs and negative audit.
2. Restore missing rev256–rev260 cumulative production source or audited binary deltas, preserving the shipped Corpus3 and desktop/transport features.
3. Implement the modern client-side EntityRenderer for these quads with texture/material, baked pose, packed lighting and packet-driven remote transforms; keep impact, block placement and damage on the original server.
4. Execute full external dependency compilation, Gradle/Loom, real Fabric 1.21.11 game/client startup and a native 1.7.10 / unchanged Forge server comparison.
5. Only after these gates build and independently verify a complete new main JAR from the pinned original rev260 baseline.

No GitHub Actions, PR, release, force push, main or Bamboo branch change was performed. All continuation messages contain `[skip ci] [skip actions]`.
