# rev246 — conversion-status desktop UI refinement

Branch: `feature/generic-conversion-iyamato-corpus3`.
Parent: `bcea7cddaa820902d1de3cb8f8a9cce6525afb31`.
Date: 2026-10-03 (Asia/Taipei).

This checkpoint changes only the desktop conversion-status presentation and artifact metadata. The rev245 deep motion-correlation behavior remains the active diagnostic subsystem and its core correlation/movement classes are preserved.

## User-visible changes

1. **「詳細資訊」現在是明確可辨識的按鈕。**
   The prior rev238 presentation deliberately called `setBorderPainted(false)`, `setContentAreaFilled(false)`, and `setOpaque(false)` on the existing `JToggleButton`, which made it look like plain text. rev246 keeps the helper's original toggle behavior but restores normal button border/background/content rendering and gives it normal button margins.
2. The label changes between **「詳細資訊 ▼」** and **「收合詳細資訊 ▲」** according to the toggle state.
3. The expanded detail area now contains **「目前支援模組列表」**. It opens a child dialog titled `LegacyForgeBridge | 支援模組 | by YingHunag09`.
4. The child dialog distinguishes corpus/live-tested targets from the generic converter policy:
   - RPGTool1 1.1 (Minecraft 1.7.10): 已實機驗證.
   - BambooMod 2.6.8.5: 重點相容 / 持續驗證.
   - iYAMATO's Mod 1.7.10-1.6.8: 重點相容 / 持續驗證.
   The text explicitly says this is **not an allow-list**. LegacyForgeBridge remains source-driven and generic; other Forge 1.7.10 mods using similar GameRegistry, Item/Block, recipe, NBT, event, entity, DataWatcher, rendering and Forge API structures may also convert and work, subject to actual semantic proof and testing.
5. The main desktop window title is now dynamically updated as:
   `LegacyForgeBridge | [目前的狀態] | by YingHunag09`
   using the same localized state mapping already used by the status UI. Initial title is `LegacyForgeBridge | 處理中 | by YingHunag09`.

## Exact artifact

Base:
`legacyforgebridge-0.2.0-alpha.27-rev245-corpus4-local.28-deepdiag.jar`

Base SHA-256:
`92d00685e9316946353bf410455e57f14a15fd4a903ce5eed749b4fafd658ae6`

Output:
`legacyforgebridge-0.2.0-alpha.27-rev246-corpus4-local.29-deepdiag-ui.jar`

Output bytes: `4,534,358`

Output SHA-256:
`6fc64f88351db277ef438b8eb226d313baa5410bf5a8e1a007c65b85bed414c4`

Fabric/build version:
`0.2.0-alpha.27-corpus4-local.29-rev246-ui.1`

Converter revision:
`2026-10-03.246-desktop-status-support-window`

The same compiled `DesktopProgressUi` and nested `State` classes are installed both in the main JAR and in `META-INF/lfb/desktop-helper.jar`; the helper is the separate desktop-process runtime, so changing only the outer copy would not have been sufficient.

## Validation actually run

- JDK 21 source compilation: pass.
- Xvfb Swing fixture against source classes: **19 assertions pass**.
- The same packaged UI test against the final outer main JAR: **19 assertions pass**.
- The same packaged UI test against the extracted nested `desktop-helper.jar`: **19 assertions pass**.
- Assertions cover visible button border/content area/opacity, expanded/collapsed label, support-list button, child dialog, RPGTool/Bamboo/iYAMATO entries, generic-not-allowlist wording, similar-mod wording, and dynamic window titles.
- Outer and nested helper `DesktopProgressUi.class` are byte-identical; their `State` classes are byte-identical.
- ZIP integrity: pass.
- Independent second build: byte-for-byte identical output.
- All outer JAR entries other than the explicitly changed UI/version/helper entries are content-identical.
- 12 other entries inside `desktop-helper.jar` are content-identical.
- `Rev243Diagnostics`, its `ReadApi`, all three `Rev245VelocityCorrelation` classes and `LegacyClientJumpMotion` are byte-identical to rev245.
- `LegacyMotionTraceLog` receives only the guarded artifact-version string replacement; correlation schema remains `rev245-deep.1`.

No live Minecraft desktop-helper launch was performed in this environment. Swing behavior was exercised on a real AWT/Swing display under Xvfb. No server/original-mod change, gameplay conversion change, movement heuristic change, Actions build, Gradle/Loom build, dependency download, PR, tag or release is part of this checkpoint.

## Reproducible build

From the repository root, with the exact rev245 base:

```sh
python3 checkpoints/rev246/build_local.py \
  /path/to/legacyforgebridge-0.2.0-alpha.27-rev245-corpus4-local.28-deepdiag.jar \
  /path/to/legacyforgebridge-0.2.0-alpha.27-rev246-corpus4-local.29-deepdiag-ui.jar
```

The builder rejects a different baseline SHA and refuses in-place overwrite.
