# 2026-10-08 — rev290 complete main JAR diagnostic preview over rev260

Branch: `feature/generic-conversion-iyamato-corpus3`.

## Result

Produced an **actual compiled, complete main mod JAR**, not a renamed old artifact:

`legacyforgebridge-0.2.0-alpha.27-rev290-diagnostic-preview.jar`

- SHA-256: `87ab6768dacb6ee579376e49815e668624ff8660aba51103f5e460a4ed2b5ea8`
- Size: **4,938,009 bytes**
- Java class files: **1,825** (rev260 baseline **1,743**, **82 added**)
- Archive diff: **83 additions, 4 changes, 0 deletions**.
- Baseline: `legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar`, SHA-256 `03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9` (never mutated).

The changes are 82 newly compiled production classes from rev279–289 source-proven render/animation and NBT analyzers, 2 narrowly patched old classes (the **original rev260** `GenericLegacyModProfile.class` and `BuildInfo.class`), a version-bumped `fabric.mod.json`, updated truthful `META-INF/MANIFEST.MF` build method/revision and a new `legacyforgebridge/rev290-diagnostic-preview-build.json` source/provenance manifest.

Every pre-existing class, resource, nested desktop-helper JAR and Energy dependency remains in the complete artifact. The old `Corpus3GenericCompletionPass` is **not** reconstructed from missing source or substituted by an old rev248 JAR. The converter cache revision changes from `2026-10-06.257-sapling-source-evidence-collision-occlusion` to `2026-10-08.290-tile-source-diagnostic-preview`, forcing regeneration of previously cached converted candidate JARs. Original `CACHE_COMPATIBILITY_VERSION` and conversion schema are retained.

## Exact assembly

Compiled Java 21 source against the **actual rev260 main binary** plus a local actual Gson 2.8.9 jar. For compilation only, converted source imports from `org.objectweb.asm` to the Java 21 JDK internal ASM-equivalent package using `--add-exports`. After compilation, changed only JVM constant-pool ASM package references back to `org.objectweb.asm` in the new application classes (the packaged artifact assumes the **normal Fabric loader ASM dependency**). No JDK internal ASM, test substitute, test class or extra assembly helper JAR is packaged.

Used JDK ASM against the *actual rev260 bytecode* to insert exactly two new source-only passes into `GenericLegacyModProfile.configure` directly after the existing `LegacyProjectilePresentationPass`:

1. `LegacyProjectileFixedModelPreflightPass`, output `legacyforgebridge/projectile-fixed-model-preflight.json`
2. `LegacyBlockTileModelPreflightPass`, output `legacyforgebridge/block-tile-model-preflight.json`

The other 29 generic profile Passes are unchanged. The patched `BuildInfo` changes only the version and converter revision literal values; app runtime and save/gameplay logic are untouched.

The original old main binary did **not** include rev284–289 sources; these now have executable source-analysis code in the generated module. However **source analysis is not Minecraft gameplay support**. Both JSON files remain `sourceOnly=true`, `runtimeWired=false`, with unknown/unproved network state and no new executable EntityRenderer or BlockEntityRenderer rule.

## Tests actually run on the assembled JAR

- Python rev282 complete-archive structural guard: **PASS_STRUCTURAL_ONLY**.
- CRC: **PASS**; no old archive entries were deleted; old nested DesktopHelper and Energy preserved; all archive items match baseline hashes except 4 expressly reviewed replacements.
- Actual newly packaged `GenericLegacyModProfile.configure`: **31 Passes**, the original projectile presentation at index 21, newly added fixed-model projectile preflight at 22, Block+TileEntity preflight at 23, no duplicate IDs.
- Packaged `BuildInfo.VERSION` and `CONVERTER_REVISION` checked by Java reflection.
- One synthetic Forge 1.7.10 JAR with a real bytecode `@Mod` pre-initialization event and `GameRegistry.registerBlock`, `GameRegistry.registerTileEntity`, `ClientRegistry.bindTileEntitySpecialRenderer` calls: **one candidate source-proven**, actual JAR `LegacyBlockTileModelPreflightPass.apply` wrote a **3,111-byte** JSON with state dependencies, light level 14, nonadmission, and correct false runtime/sync fields; optional audit left conversion status unchanged.
- Actual packaged fixed-model analyzer: 4-cuboid, 24-face legacy ModelBox proof **PASS**, independent launcher source dataflow **PASS**, independent manifest denies runtime admissions **PASS**.
- 48 classes from rev284–289 were compiled first and all 82 new classes (including rev279–283) were compiled successfully. The prior source-only synthetic regression checkpoints, up through rev289 **184/184**, were separately exercised with Java 21 and controlled dependency doubles in earlier sessions. Do **not** describe those as an official Fabric JUnit or Minecraft gameplay run.

The test runtime used a **non-distributed** ASM package relocation of JDK internal ASM to mimic the `org.objectweb.asm` library for standalone checks. The **actual Fabric loader / Minecraft 1.21.11 client was not started**, and the exact translated `twilightforest-1.7.10-2.3.8-tw.jar` was not supplied in this session. Compatibility under actual Fabric Loader ASM9 and ViaFabricPlus has **not** been confirmed in game. Thus this is a candidate **test preview** and not an official compatible release.

## Safe user testing

1. Make a backup of the current PrismLauncher instance. Keep the original rev260 main outside the instance's `mods` directory.
2. Install **only** the rev290 diagnostic preview JAR in the instance `mods` folder; Fabric 1.21.11, Java 21, Fabric API, ViaFabricPlus, Team Reborn Energy, and Cloth Config remain required.
3. Place original 1.7.10 mod binaries in `old-mods`, not in the Fabric `mods` folder.
4. Launch, follow restart-required instructions if any. The converter version invalidates cache fingerprints to refresh converted candidates.
5. Open converted `legacy-cache/converted/*-lfb.jar` with ZIP/7-Zip and inspect `legacyforgebridge/block-tile-model-preflight.json` and `legacyforgebridge/projectile-fixed-model-preflight.json` **if source proof was found**. A missing sidecar may mean source patterns were rejected, not necessarily an installation failure.
6. If any game startup or conversion fails, remove rev290, restore rev260 and inspect `logs/latest.log`, `crash-reports`, `legacy-cache/reports`.

## Not yet complete

Modern 1.21.11 tile/animated part renderer, authoritative tile packet synchronization, entire Twilight Forest ecosystem and bosses, real Forge 1.7.10 server game validation, full Gradle/Loom source build and complete source overlays recovery are still **unfinished**. Do not call this rev290 a finished Twilight Forest update.

No GitHub Actions / PR / release / tag / force push / main / Bamboo changes. The archive was produced **locally**, not from GitHub CI. Source code for the independent projectile pass was also committed to this feature branch. Every repository continuation commit includes `[skip ci] [skip actions]`.
