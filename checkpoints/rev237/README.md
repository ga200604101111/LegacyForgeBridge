# rev237 — translucent legacy WATER and conversion progress bar

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev237 is cumulative with rev236. The local final JAR was rebuilt from the exact rev235 binary and the documented rev236 jump overlay was re-applied before the rev237 changes.

## Source-proven liquid translucency

rev233 already proved Bamboo `spa_water` as the generic legacy WATER family:
- `BlockLiquid`
- `Material.water`
- render type 4
- non-opaque / non-normal
- empty collision
- water textures and tint-indexed generated faces
- translucent block render layer

Live feedback showed that the model-rendered path could still appear as an opaque blue surface. rev237 keeps the generic family and adds an actual alpha component to WATER block tint values.

- applies only to source-proven WATER rules;
- no Bamboo registry-name special case;
- biome water RGB is retained;
- tint alpha is `0xA0`;
- `-1` / untinted fallback remains unchanged;
- lava behavior is untouched.

## Desktop conversion progress UI

The conversion session already publishes real progress fields:
- `percent`
- `index` / `total`
- `pass`
- `passProgress`
- `conversionFinished`

rev237 adds one Swing `JProgressBar` to the existing desktop helper and uses those real values.

On successful conversion completion:
- bar reaches 100%;
- text reports that the conversion is complete;
- after 3000 ms the status window is hidden;
- the helper process remains alive so Prism restart orchestration can continue headlessly.

On `ERROR`, `WARN` or `MANUAL`:
- auto-hide is canceled;
- an already hidden window is shown again so diagnostic information is not lost.

## Validation

- Xvfb Swing harness: 67% / pass progress rendered — pass
- successful conversion hides window after 3 seconds — pass
- ERROR after hide reopens window — pass
- nested helper JAR contains and calls `DesktopProgressUi` — pass
- Java 21 `-Xverify:all` loads nested `DesktopHelper` and `DesktopProgressUi` — pass
- WATER alpha helper: `0x3F76E4 -> 0xA03F76E4` — pass
- ZIP integrity — pass
- duplicate entries — 0
- manifest unchanged
- nested Energy JAR unchanged

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev237-corpus4-local.21.jar`
- bytes: `4465395`
- SHA-256: `61b82a38e3abd1df7b666dee604821410de395b010c4e47122c2b6bb8bdc1268`
- internal version: `0.2.0-alpha.27-corpus4-local.21-rev237-local-test.1`
- converter revision: `2026-10-01.237-liquid-alpha-desktop-progress`
- cache compatibility remains `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`

No live Minecraft launch was available in the build environment. No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
