# rev194 — main-bridge furniture presentation and remote menus

Main replacement artifact: `legacyforgebridge-0.2.0-alpha.27-rev194.jar`.

SHA-256: `5cc1cdd4ef617150e87af5e607c0b56241f067853ef63a527efcaf2302e42376`.

This revision is integrated into the main bridge. Do not install the obsolete `lfb_visual_stack_hotfix` add-on. Preserve original mods in `old-mods`, settings and server data. The converter fingerprint changes, so complete conversion and restart the client before testing the new generated contracts.

## Implemented

- Tray items: recover source zero-based radial placement and camera-yaw billboarding. Adapt centred modern meshes by anchoring the actual item AABB bottom above the source plate board, rather than placing the item origin inside it. Plate orientation remains independent from camera-facing food. Preserve rev193 vanilla ID/metadata conversion.
- Grid pot: server-supplied BlockItems use their native world block models, including crossed plant planes, not flat inventory icons. Preserve converted metadata and the independent 1/3-wide, 3/8-high carrier model. The source cactus width/height branch has a separate native-model transform.
- Hanging cuboid/sheet entity family: recover registration, renderer, fixed texture, two-part model, zero-depth sheet and source wind dynamics using closed instruction-shape proofs. Bamboo's original wind chime is admitted from its source registration, not a hardcoded name/numeric-ID allow-list. Render the body, two-sided sheet and hanger; do not duplicate server sound or gameplay. The old screen-space line is adapted to a thin native cuboid, and random motion is presentation-only.
- Initial chunk hydration: create missing client block entities for bridge-owned EntityBlock states on chunk load. Palette-gated, legacy-connection-only, no per-tick world scan. Existing block entities/NBT are not replaced. This addresses the code path consistent with lanterns appearing only after block updates; no live lantern test was performed.
- Remote GUI family: recover actual FML lifecycle handler registration, matching server/client GUI branches, world tile/block identity, 47-slot layout, output-slot rejection, shift routing, property fields, source GUI geometry and 256x256 texture. Dispatch FML OpenGui into a native 47-slot client menu and source-textured native container screen. The remote server remains authoritative for inventory, recipes, fuel and rewards. Reused ViaLegacy window types are cleared before the client menu is queued, avoiding stale furnace/enchanting offsets. Native slot/property/click/close paths are retained, but have not been validated through a live complete Via/Fabric connection.

The GUI is a remote 1.7.10-server feature, not a local single-player campfire implementation. Unknown or modified source behavior fails closed. Class-renaming and changed numeric spawn-ID tests passed; this is not universal mod compatibility.

## Local verification

- Exact rev193 input: `840fb56d7cd8013b3c813fd3d0f4781acd4e571abd105d4be512c99046ec8cc8`.
- Local JDK 21 incremental compilation and checksum-guarded replacement of selected host method bodies; no full clean Gradle/Loom build.
- 176 assertions against delivered renderer/menu/dispatch/hydration method bodies with explicitly declared recording API doubles. These are not Minecraft, Fabric, Mixin or real network integration tests.
- 25 source-mutation/contract assertions: incompatible layouts, missing registration/texture and changed dynamics rejected; renamed source classes/numeric IDs remain supported; all six generated visible-entity rules and the new GUI rule accepted by delivered runtime parsers.
- Selected 19-pass original Bamboo conversion pipeline completed with `PARTIAL` status. Six visible-entity contracts and one remote GUI contract were emitted. Original mod bytecode was analyzed, not executed.
- ASM BasicVerifier: 45 changed/new classes, 448 methods. Existing manifest/Mixins and 1,408 other base entries unchanged. Packaging repeated with identical compiled inputs produced identical JAR bytes.
- The complete 22-file Java source delta applied cleanly to an isolated restored local rev193 baseline and matched the modified files byte-for-byte.

No live gameplay validation, actual complete packet-pipeline replay, full Gradle test suite or GitHub Actions build was performed. Optional GridPot insertion-predicate conflicts remain outside this revision; displaying existing server contents does not certify local insertion rules.

## Restore source

This branch still keeps the rev188 root source plus cumulative checkpoints. Do not compile that root directly and call it rev194.

```
python checkpoints/rev194/restore.py --output ../LegacyForgeBridge-rev194
```

The script first restores rev189-193 using the existing checked source checkpoints, then verifies and applies rev194. Source-owned Java changes use normal source symbols; no intermediary class/method token is introduced by the patch. Compiled API test doubles and third-party/Minecraft/dependency binaries are not packaged into the main JAR or the source checkpoint. The checkpoint contains test-double-generating source text for inspection; it contains no original mod binaries or credentials. Four standalone local validation source scripts are included in the checkpoint for inspection; local source/build attachments contain the remaining incremental-build material.
