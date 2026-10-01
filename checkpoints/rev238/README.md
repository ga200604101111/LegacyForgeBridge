# rev238 — compact conversion status window layout

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev238 is a cumulative binary overlay on the exact rev237 artifact and retains rev236 jump prediction, rev235 generic armor mirroring, and rev237 source-proven WATER translucency.

## UI correction

The old details toggle used a decorative glyph that could render as an unsupported square/tofu character on the user's Windows font stack. rev238 removes that glyph entirely.

Collapsed layout is now:

```
狀態: [狀態]
模組 1 / 3                                      轉換時長: 12.4 秒
[================ progress ===================]
詳細資訊
[轉換診斷] [取消自動退出/重啟]
```

Behavior:
- `詳細資訊` remains clickable but is rendered as plain text: no box, no unsupported arrow glyph, no painted toggle border.
- module index/total and elapsed time share one horizontal row;
- elapsed time is right-aligned to the window edge;
- progress bar displays the real conversion percent;
- `開啟轉換診斷` is renamed to `轉換診斷`;
- cancel action is labeled `取消自動退出/重啟`;
- successful conversion still auto-hides after 3 seconds;
- ERROR/WARN/MANUAL/launch failure cancels auto-hide and re-opens a hidden status window.

## WATER translucency retained

rev237's generic source-proven WATER handling is unchanged:
- translucent render layer;
- tint-indexed water model faces;
- biome water RGB;
- ARGB alpha `0xA0`;
- no Bamboo registry-name special case.

## Validation

- Swing/Xvfb layout harness: pass
- no visible details-toggle border/content fill: pass
- labels: `詳細資訊`, `轉換診斷`, `取消自動退出/重啟`: pass
- module/elapsed single-row layout: pass
- real percentage progress: pass
- successful auto-hide after 3 seconds: pass
- ERROR re-open after auto-hide: pass
- nested desktop helper Java 21 `-Xverify:all`: pass
- ZIP integrity: pass

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev238-corpus4-local.22.jar`
- bytes: `4474088`
- SHA-256: `74ad65eb609b97317d507a313ff1dffc36e79843c3bdb43becd165a0e5eaedb8`
- internal version: `0.2.0-alpha.27-corpus4-local.22-rev238-local-test.1`
- converter revision: `2026-10-01.238-compact-progress-window-layout`
- cache compatibility remains `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`

Binary audit vs rev237:
- base entries: 1688
- output entries: 1689
- duplicates: 0
- removed: 0
- added: `legacyforgebridge/rev238-build.json`
- changed existing: main/nested DesktopHelper/ProgressUi, BuildInfo, fabric.mod.json
- manifest unchanged
- nested Energy JAR unchanged

No live Minecraft launch was available in the build environment. No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
