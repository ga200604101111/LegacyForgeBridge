# 2026-10-08 — Twilight Forest 2.3.8 Part 2G / rev285: TileEntity visual-state and sync audit

Branch: `feature/generic-conversion-iyamato-corpus3`

## Why Moonworm requires a separate, generic placed-block path

The publicly inspectable historic upstream code (Benimatic/twilightforest @ `98b88bde74d6db0aa463dba304f5c13acb6140fb`) shows the **placed Moonworm is a Block** extending an ordinary `Block` ancestor via `BlockTFCritter`. `BlockTFMoonworm` creates a `TileEntityTFMoonworm`; the 1.7.10 client binds `TileEntityTFMoonwormRenderer` and calls an animated `ModelTFMoonworm`. The placed block has metadata-dependent attachment bounds and source constant light value 14. Its TESR observes block metadata and TileEntity yaw, applies source GL transforms, and calls `ModelTFMoonworm.setLivingAnimations(tile, partialTime)`. Source `TileEntityTFMoonworm.updateEntity()` mutates yaw/delay fields.

Separately, the **Moonworm Queen** spawns `EntityTFMoonwormShot`, which uses a projectile renderer. Sharing model geometry does not mean the placed-block renderer or state synchronization is identical to that projectile. Do not make Moonworm, TwilightForest or their legacy numeric IDs a production conversion special case.

Historical upstream sources are a reference for converter design; they are **not** a byte-for-byte proof against the requested `twilightforest-1.7.10-2.3.8-tw.jar`, which was not available for exact analysis in this session.

## Implemented changes (source-only)

- `src/main/java/dev/yinghuang/legacyforgebridge/convert/LegacyBlockTileVisualStateAnalyzer.java` scans legacy JAR bytecode without loading, linking or executing mod classes. It accepts only source-proven rev284 registered ordinary Block → TileEntity → TESR → ModelBase candidates.
- It walks a bounded, source-local call closure (maximum 24 methods) from `renderTileEntityAt` and from the TileEntity `updateEntity` entrypoint, tracking: source-declared TileEntity fields **observed** with renderer `GETFIELD`, direct model-animation methods reached by the renderer with the matching TileEntity descriptor and their TileEntity reads, `ModelRenderer` pivot `PUTFIELD` observations, TileEntity tick-side field `PUTFIELD`, and intersections of fields used for render and mutated on tick.
- It separately records the occurrence of `getBlockMetadata`, `GL11.glRotatef`, fixed negative `glScalef` argument patterns, 1.7.10 NBT hooks, and source description-packet / data-packet method hooks.
- A packet hook's **presence is not a proven packet payload mapping or client state bridge**; an NBT read/write hook is not proof of packet synchronization. An opcode field read is not in itself a full receiver-proven dataflow; fields are labeled `Observed` deliberately. All output state readiness fields stay false.
- A list of source candidates above 512 is rejected. Duplicate/missing candidate identities, missing source class/draw and incomplete bounded method-closure proof are diagnosed rather than used as positive evidence.

Updated `src/main/java/dev/yinghuang/legacyforgebridge/convert/pass/LegacyBlockTileModelPreflightPass.java` to attach **optional source-only visual-state evidence** to the existing `legacyforgebridge/block-tile-model-preflight.json` sidecar:

- `rendererTileFieldsReadObserved`, `modelAnimationTileFieldsReadObserved`, `tileTickFieldsWrittenObserved`, `tickDrivenRenderFieldsObserved` and `modelPivotFieldsWrittenObserved`
- `sourceModelAnimationCallObserved`, `sourceTileMetadataLookupObserved`, `sourceGlRotateCallsObserved`, `sourceNegativeScaleObserved`
- `sourceNbtHooksPresent`, `sourceTilePacketHookPresent`, `sourceTileSyncAssessment`
- `sourceTileStateSyncProven=false`, `sourceFacingMapProven=false`, `sourceAnimationRuntimeWired=false` and `runtimeReady=false`
- aggregate `visualStateAuditCandidateCount` and `tileTickVisualDependencyCandidateCount`.

Previous rev284 `manifest(String,String,Analysis)` stays intact. If optional state analysis is unavailable, the preflight still emits its original non-executable source evidence, and its diagnostics use `SupportLevel.AUTO` so the audit cannot demote a previously converted mod. No executable `rules` entry is emitted; the existing `runtimeWired=false`, `blockEntityRuntimeWired=false`, `animationSemanticsProven=false`, `tileStateSyncProven=false` and `modernBlockGeometryProven=false` remain unchanged.

No production mod-specific names/IDs/texture filenames or class tables were introduced.

## Tests run (Java 21)

- New `LegacyBlockTileVisualStateAnalyzerTest`: **16/16** renamed synthetic bytecode cases passed (source tick-driven/render field intersections, metadata/GL calls, reachable vs unreachable model pivot write, no tick, unrelated tick field, NBT-only, packet-hook-only, unknown source fields, missing entrypoint/class, duplicate candidate, >512 candidate rejection).
- `LegacyBlockTileVisualStateManifestTest` plus existing rev284 manifest tests: **6/6** passed, checking JSON details and that a source packet hook never grants runtime sync.
- Original rev284 source registration/candidate cases: **18/18** passed unchanged.

Total **40/40** independent regression methods executed. The ASM-oriented test harness temporarily substituted Java 21 `jdk.internal.org.objectweb.asm` names and used minimal JUnit/Gson stubs plus the real uploaded rev260 binary for ABI classpath. These are **not** Gradle/Loom/JUnit tests in an actual Fabric mod or native Minecraft/Forge 1.7.10 multiplayer acceptance. The exact committed GitHub Java blobs were compared with the locally tested source bytes after each commit.

Original complete main `legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar` remains unchanged, SHA-256:
`03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`.

## Remaining hard gates

1. Obtain and hash the exact `-tw.jar`; confirm whether and how its TileEntity fields are synchronized by FML/NBT/packets, including client-side tick behavior. Historical upstream source alone cannot certify the target JAR.
2. Derive a *proven six-direction source metadata → block bounds → TESR transformation table*, including call ordering and orientation, without guessing angles or deploying per-mod adapters.
3. Prove per-part animation math/texture/light state, then build a dedicated generic modern client-only BlockEntity/TESR renderer with remote state semantics.
4. Recover rev256–rev260 original complete build source overlays. Preserve old working rev260 main JAR and DesktopHelper and perform Gradle/Loom/build plus Fabric 1.21.11 ↔ unchanged Forge 1.7.10 real server validation before a new main JAR is delivered.

No GitHub Actions, PR, release, force push, or main/Bamboo branch change. Every commit message includes `[skip ci] [skip actions]`. This checkpoint **does not make any additional Twilight Forest block playable**.
