# 2026-10-08 — rev291: first runnable static model asset improvement against the exact Twilight Forest -tw.jar

Branch: `feature/generic-conversion-iyamato-corpus3`

## Why rev290 looked almost identical to rev260

The uploaded **original** `twilightforest-1.7.10-2.3.8-tw.jar` has 799 original JVM class entries, but the uploaded converter result `twilightforest-1.7.10-2.3.8-tw-lfb.jar` has only 6 generated classes. This is not a code-equivalent port. The converted manifest reports `status=partial`, `installable=false`, and `runnableWrapper=true` (not semantically complete).

Exact generated-content audit: **35** block identities, **22** magenta glazed terracotta fallback block models, **8** barrier fallback item models, **77** known entity registrations, but **0** runtime entity replacement rules and **0** executable projectile presentation rules. Prior rev279–rev290 added source-only diagnostics without making these features playable; that correctly explains the user's observation.

Exact user-provided originals (never rewritten):
- source SHA-256 `1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`;
- generated candidate SHA-256 `f2c5fc8b2bc59c261a6da35dc22d0f798418089ab51e0f1c47c721642e26ec22`.

## Real visual fix (not merely diagnostics)

Added `LegacyStaticTileModelBakerPass`: generic, fail-closed source bytecode analysis of the existing `LegacyBlockTileModelPreflight` (registered Block -> TileEntity -> TESR -> source ModelBase). The pass proves one source PNG resource, its model atlas dimensions, constructed ModelRenderer parts, box sizes, pivots, actual render call closure, one bounded GL transform sequence and source metadata static facing table. There is **no** Twilight Forest name, class whitelist or numeric ID in the production admission logic.

Generates vanilla modern **block model JSON with actual multi-part cuboids + ModelBox UVs + face angles**, source PNG copied into the generated converted candidate's own assets and an explicit blockstate variant for each `legacy_meta=0...15`. Does not execute old gameplay/updateEntity code. Does not guess arbitrary dynamic rotations or shader blend mode. Provisional source materialization should not be confused with proof of Minecraft 1.21.11 graphics behavior.

On **the exact uploaded source JAR**, the new Java Pass generates:

| Source identity | Original model cuboids | Static poses | Metadata variants | Result |
| --- | ---: | ---: | ---: | --- |
| `tile.TFMoonworm` | 4 | 6 | 16 | Source-proven static block model emitted |
| `tile.TFCicada` | 6 | 6 | 16 | Source-proven static block model emitted |
| `tile.TFFirefly` | 3 body parts + independent glow overlay | — | — | Correctly **blocked**: dynamic/multiphase legacy GL blend/depth renderer |

Existing `GenericContentPass` writes a `cube_all` placeholder before this new Pass runs. The Pass has been amended and tested to replace **only the exact single-texture generic placeholder** and its canonical one-default-variant blockstate. A specialized model, a noncanonical state or a conflicting destination file is rejected, never silently overwritten. The base item model now points to the newly generated source 3D pose for those two admitted blocks. The later `LegacyClientContentBaselinePass` preserves the new 16-state blockstate rather than expanding the original placeholder.

The source-independent Python reference preview also rewrote 2 blockstates and added 12 posed models, 2 source textures plus 1 evidence file without deleting or replacing other existing source-generated content. That **visual-preview converted candidate remains partial/noninstallable**; don't count it as a full legacy mod port.

## New complete main JAR — first genuinely compiled conversion improvement

Artifact: `legacyforgebridge-0.2.0-alpha.27-rev291-static-tesr-preview.jar`

- SHA-256: `6d1476bee30d4e9a2727dedf2fdaf87bf520f7d1f2e6b1e1a292878a0d093f5f`
- Size: **4,958,922 bytes**
- Complete Java class entries: **1,832** (previous rev290: 1,825)
- Outer ZIP entries: **1,917** (previous rev290: 1,909)
- Precisely **8 added entries** (7 newly compiled classes + 1 build provenance JSON); **4 changed existing entries** (`BuildInfo.class`, `GenericLegacyModProfile.class`, `fabric.mod.json`, `META-INF/MANIFEST.MF`); **0 deletions**.
- Existing rev290 class/runtime bytecode, rev260 Corpus3, IPC/Desktop helper and nested dependencies unchanged.
- Actual build: **Java 21 javac** compiled new generic Pass against original rev290 binary, with a temporary test/compiler-only JDK ASM import substitution; final new classfile constants use normal `org.objectweb.asm` package. **Audited JVM bytecode patch** inserts one new pass into original compiled profile, updates only two `BuildInfo` literals, and updates truthful Fabric/build version metadata. **Not** a full Gradle/Loom rebuild and **not** simply renaming an old JAR.
- Converter revision changed to `2026-10-08.291-static-tesr-source-model-preview`, invalidating rev290 cached conversion candidates.
- New complete main JAR has **32** generic profile passes, with `legacy-static-tesr-model-source-baker` registered at index 24 (zero-based), preserving all 31 rev290 passes.

## Tests actually executed

1. ZIP CRC, no class/resource removals, full content preservation and explicit changed-entry audit: **PASS**.
2. Actual packaged Java bytecode (loaded through a **test-only** JDK-internal ASM relocation of the new complete JAR; no test shim is inside the distributed main) configures all **32 Passes**, contains new Pass at index 24: **PASS**.
3. Actual packaged Pass **apply()** invoked against user-provided original Twilight Forest source JAR and staging containing the **real old `cube_all` block models** but the **canonical pre-baseline default blockstates**: **PASS**. It emits **2** accepted source-driven model candidates and explicitly skips Firefly (unsupported blend).
4. All baked models have 4 or 6 parts, 6 unique source static poses, 16 metadata state mappings, exactly six valid UV faces per source cuboid: **PASS**.
5. All 16 metadata pose geometry/UV outputs were cross-compared with a separately implemented Python source interpreter: **PASS**.
6. Complete main original class/revision versions and preservation of Fabric entrypoints/mixins checked: **PASS**.
7. **Not executed**: Real Minecraft 1.21.11 Fabric loader, ViaFabricPlus, OpenGL rendering, original Forge 1.7.10 multiplayer, full Gradle/Loom, dynamic server sync, TileEntity animation.

The exact pre-baseline stage matters: manually reusing the previously converted `-lfb.jar` (which already has 16 expanded `legacy_meta` variants) as *input staging* was correctly rejected by the Pass as a specialized state instead of being silently replaced. Fresh normal conversion runs `GenericContentPass` before the Pass, and `LegacyClientContentBaselinePass` after it. For debugging an old candidate, the separate offline static-preview artifact is available but **should not** be mixed with the autoregenerated candidate installed by the converter.

## How to test without corrupting the world

1. Back up PrismLauncher instance/world and the current `mods` folder.
2. Remove **rev260/rev290** main JARs, install **only rev291** full main JAR in Fabric 1.21.11 `mods`; keep the same Fabric API, ViaFabricPlus, Cloth Config, Team Reborn Energy and Java 21 dependencies.
3. Keep the **original Forge 1.7.10** `twilightforest-1.7.10-2.3.8-tw.jar` in `old-mods` (not Fabric `mods`); do not install a duplicate manually converted Twilight Forest JAR in `mods`.
4. Start client; automatic conversion should invalidate old cache due converter-revision change and generate a new Twilight candidate. Restart if requested.
5. Check newly generated candidate JAR under `legacy-cache/converted/` for `assets/twilightforest/models/block/tile.tfmoonworm_lfb_source_static_*.json`, `tile.tfcicada_lfb_source_static_*.json`, new `assets/twilightforest/textures/block/lfb_static_source/*.png`, and `legacyforgebridge/static-tesr-visual-preview.json`. Confirm the old `cube_all` model replaced by a parent to a source 3D model.
6. In-game compare placed Moonworm/Cicada blocks to rev290; if still invisible/cubical/missing, upload screenshots, generated JAR, `logs/latest.log` and the report. Real client rendering remains unverified.

**Unfinished:** the other ~20 placeholder blocks, at least 8 barrier items, all living mobs/boss AI, 0/77 converted entity runtime rules, portal/dimension, 1.7.10 server-authoritative TileEntity synchronization, dynamic yaw/pivot animations, Firefly multi-pass, lighting shader/material parity. A few fixed static model assets are **not** a full Twilight Forest port.

Project boundaries honored: no original/server modifications, no Actions, no PR, no tag/release, no force-push, no default/Bamboo changes, all commits include `[skip ci] [skip actions]`. Full JAR is delivered locally through this conversation, **not** claimed as a GitHub CI or a native runtime-validated release.

See `diagnostics/rev291-twilight-exact-source-visual/verification.json`, `docs/SESSION-2026-10-08-REV290-COMPLETE-MAIN-DIAGNOSTIC-PREVIEW.md`, `docs/TWILIGHT-FOREST-238-ACCEPTANCE.md`.
