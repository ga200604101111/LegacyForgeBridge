# 2026-09-15 — alpha.27 generic legacy food bridge

## Completed

- Added a non-executing, fail-closed `ItemFood` constructor analyzer.
- Proves straight-line source constructor paths into vanilla 1.7.x `ItemFood(int,float,boolean)` and `ItemFood(int,boolean)`.
- Preserves proven nutrition, saturation and `setAlwaysEdible()` semantics through modern `FoodProperties`.
- Supports registration-time constant propagation into source constructors.
- Rejects custom consume callbacks and legacy potion-effect food until those semantics have their own adapter.
- Emits `legacyforgebridge/food-item-rules.json` and loads it before generated item registration.
- Runtime registration remains generic and mod-name independent.

## Validation

GitHub Actions run #250 (`34974322488`) passed checkout, diff hygiene, Java/Gradle setup, full build/tests and artifact upload on head `a9db32a1acba54dc1f3971ad24719b414a114bca`.

Artifact: `LegacyForgeBridge-corpus2-p0`

SHA-256 digest: `7a3313a9e68ff0a5ddca0282349a4a043be0a49a66146d110b1c73394623e78e`

The exact Bamboo corpus JAR was not available in the connected file library during this continuation, so the checksum-pinned exact-corpus task was not falsely reported as executed.

## Next food semantic

Minecraft 1.21.11 exposes the native `minecraft:wolf_food` item tag. The next bounded extension can therefore translate a proven legacy `isWolfsFavoriteMeat=true` flag into that native tag instead of keeping those foods blocked. Legacy `setPotionEffect(...)` remains a separate consumable-effect problem.
