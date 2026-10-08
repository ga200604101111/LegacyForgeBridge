# 2026-10-08 — rev292 complete main: batch block/weapon visuals + Prism restart watchdog

Branch: `feature/generic-conversion-iyamato-corpus3`. All continuation commits include `[skip ci] [skip actions]`.

## User-reported defects

1. The converted Twilight Forest 2.3.8 resource preview still looks mostly incomplete, including magenta block placeholders, barrier item icons and flat weapon/tool models.
2. Prism Launcher restart **closed Minecraft but did not actually start Minecraft again**. The old rev291 `DesktopHelper.poll()` treated a successful Prism `--launch` process creation as sufficient to dispose its helper window, so there was no ongoing readiness/error monitoring; the 1.5-second delay after the old JVM exited also risked launching before Prism released its instance lock.

Both problems are addressed as **candidate preview fixes**, not universal guarantees of full mod equivalence.

## Actual complete main JAR delivered

`legacyforgebridge-0.2.0-alpha.27-rev292-batch-visual-prism-restart-preview.jar`

- Complete main archive: **4,974,541 bytes**.
- SHA-256: `3dee8cd19bcfe82daaab23c8ae9e2f0b8041f6aa2f73bbd5bda1b3f224190574`.
- Exact base rev291 SHA-256: `6d1476bee30d4e9a2727dedf2fdaf87bf520f7d1f2e6b1e1a292878a0d093f5f`.
- Original source Twilight JAR SHA-256: `1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`.
- Compared with rev291: **4 new entries, 6 modified entries, 0 removed**, **1,835 class files**. ZIP CRC passed. Every unchanged original member stayed byte-identical.
- Java 21 main class overlay was compiled against the **actual rev291 shipped ABI**. Temporary JDK-internal ASM for local Java compilation/testing was remapped to real `org.objectweb.asm` class references in the shipped classes. No test-only ASM shadow classes are distributed.
- Two bounded, verified binary changes in original main: `GenericLegacyModProfile.class` injects the batch recovery pass **immediately after** the existing `LegacyStaticTileModelBakerPass`; `BuildInfo.class` changes the version/cache-revision literals, forcing conversion regeneration.
- The DesktopHelper watcher is patched **in both** the outer main JAR and the nested `META-INF/lfb/desktop-helper.jar`; the latter's only changed internal file is `dev/yinghuang/legacyforgebridge/desktop/DesktopHelper.class`. Desktop APIs, networking, existing conversion classes and other dependency bytes are preserved.
- `fabric.mod.json` version, `META-INF/MANIFEST.MF` honest `rev292` and assembly method, and a source/provenance JSON are updated.
- **Not a full Gradle/Loom build**; binary-overlay assembly as documented, and full real Fabric 1.21.11 / Windows Prism end-to-end validation has not occurred.

## Source-driven visual recovery

New `LegacyBatchVisualRecoveryPass` processes **all proven registered blocks and items** from their source Forge 1.7.10 JAR; **no Twilight Forest/weapon/block name or numeric registry ID controls dispatch**. Source inference uses direct original texture field assignment, exact item/block icon registration and bounded vanilla reference cases. It only replaces known magenta/barrier fallbacks and repairs tool-like item presentation to handheld geometry; existing source-proven specialized models are kept.

Tested with the user's exact original `twilightforest-1.7.10-2.3.8-tw.jar` and a fresh staging tree from their original `-lfb.jar`:

- **22** magenta placeholder block visuals recovered.
- **20** held weapon/tool visual models changed to handheld style (including source-backed stand-in icons for source cases that inherit vanilla icons).
- **7** missing/barrier item icons recovered.
- rev291 previously generated static multi-cuboid placed critter meshes remain untouched.
- One caveat: blocks with multiple source icons but no source-proven per-face/per-meta selector use a **preview selector only**; source icon identity does not guarantee faithful placement/biome/state rendering.
- `legacyforgebridge/batch-visual-recovery-preview.json` explicitly declares `visualPreviewOnly=true`, `specialRendererEquivalenceProven=false`, `tileStateSyncProven=false`, and `runtimeCombatSemanticsProven=false`.

Packaged JAR end-to-end harness: **33 registered profile Passes**, with new batch Pass directly following the static tile-model Pass. Actual source JAR -> staged resource pass produced the 22/20/7 output and correct nonadmission flags; optional diagnostic did not alter existing conversion status. No claim that all **77 Twilight entities**, AI, special weapons/skills, portals/worldgen or all tile animations are now runnable.

## Prism restart helper failure and fix

The original `DesktopHelper.poll()` after `PrismTarget.command()` (`--dir <dataRoot> --launch <instance>`) set `LAUNCH_SENT` and immediately `closed=true` then disposed its Swing frame. This **exited the helper even though a launched game had not been observed**.

A bounded JDK ASM patcher, `tools/release/PatchDesktopHelperRestart.java`, now replaces only that proven early-close tail with `show(initial); return`, allowing the existing `checkGameReady()` state machine to remain active. Delays updated:
- Wait after old game process exit: **1.5 seconds -> 7 seconds**, to reduce conflict with Prism instance occupancy state.
- No-game-ready manual-review watchdog: **180 -> 90 seconds** (a missing new client becomes visible rather than silently treated as success).
- Legitimate success/cancel paths remain unchanged; the helper closes only after actual matched successor readiness.

Prism simulation with a Linux Xvfb virtual display and a **fake executable** implementing the Prism CLI:

| Tested main | Issued exact fake Prism `--launch` command | Helper after dispatch |
| --- | --- | --- |
| rev291 | yes | **Exited normally** (status `VERIFYING`, process exit 0) |
| rev292 | yes | **Remained alive** through the 12-second external test timeout; status `LAUNCH_SENT` |

The fake Prism command logging confirmed the `--dir` and `--launch` arguments. Exit code **124** for the new helper comes from the external timeout and means the helper **did not exit early**. It is *not* proof a Windows Prism Launcher actually started a second game; real Windows Prism startup and Ready IPC still require user testing. The synthetic test was run against **the complete packaged rev291 and rev292 JARs**, not only source methods.

## How to test safely

1. Back up the PrismLauncher instance and `legacy-cache` directory.
2. Move rev260, rev290, rev291 main JARs out of `mods` and place **only rev292** there. Keep Java 21, Fabric 1.21.11 and the previously installed required Fabric API, ViaFabricPlus, Cloth Config and Team Reborn Energy dependencies.
3. Place the original 1.7.10 Twilight Forest `-tw.jar` in `old-mods` only; do **not** manually reinstall the stale `-lfb.jar` into `mods`.
4. Launch and allow the converter to regenerate cache due to the converter-revision change. Follow normal restart prompts, then compare source-backed block textures, handheld item appearance and Prism relaunch.
5. The converted `-lfb.jar` should include `legacyforgebridge/batch-visual-recovery-preview.json`. The progress/counts are **resource-only preview**, not a compatibility certification.
6. If Prism still fails: collect `logs/latest.log`, Prism instance launch logs and `legacy-cache/desktop/sessions/<newest>/status.properties`, `helper-state.properties`, `launcher-control.properties`. Do not delete the session evidence before collecting.
7. Restore the older main and backup for rollback if needed.

## Open issues and release discipline

- User Windows Prism Launcher **not** remotely controlled or tested; Prism executable location/flags, permissions, instance locks and signal wiring might still be environment-specific.
- Original converted `-lfb.jar` marked `partial` with 0 executable rules for 77 registered living/projectile entities. **rev292 does not fix this fundamental limit**.
- Many original weapons need actual semantic use/damage/projectile handling; some complex blocks need exact multiple-texture geometry and behavior. Legacy server remains authoritative.
- No real Fabric/Minecraft runtime or Forge 1.7.10 in-game acceptance, nor source-complete Gradle/Loom build, is claimed.
- No GitHub Actions, PR, tag, release, force-push, or main/Bamboo edits; no original mod, rev291 or original Forge server mutation.

Related source files:
- `src/main/java/dev/yinghuang/legacyforgebridge/convert/pass/LegacyBatchVisualRecoveryPass.java`
- `tools/release/PatchDesktopHelperRestart.java`
- `tools/release/Rev292PatchBinaries.java`
- `docs/SESSION-2026-10-08-REV291-STATIC-TESR-VISUAL-PREVIEW.md`
