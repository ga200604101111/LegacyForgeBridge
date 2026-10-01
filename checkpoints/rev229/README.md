# rev229 — separate legacy armor display from the HUD carrier

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev229 is a cumulative local binary overlay on the exact rev228 artifact. It retains the rev227 projectile-particle, Via/Bamboo, armor reconversion and Cloth Config ABI fixes.

## Armor split

The user-visible legacy armor value and the modern HUD carrier are now intentionally separate.

- **Legacy Armor / 盔甲**: the original 1.7.10 `armorPoints` value is emitted as a presentation-only tooltip row. It does not contribute a second attribute modifier.
- **HUD armor strength carrier / 盔甲強度**: the same source `armorPoints` remains as one hidden modern `ARMOR` modifier. This is the only compatibility modifier that feeds the 1.21.11 armor HUD.
- Therefore the HUD is not double-counted.

The exact iY source values remain:
- Tamahagane: 4 / 8 / 6 / 4
- Damascus: 4 / 9 / 7 / 4
- Heavy Damascus: 5 / 10 / 8 / 5

## iY source attributes

rev228 accidentally removed the armor-item source movement-speed modifier and hid every source modifier on armor.

rev229 corrects that:
- iY/source `generic.movementSpeed`: effect is restored and active; only its intrinsic equipment tooltip row is hidden.
- iY/source `generic.knockbackResistance`: effect is active and the row stays visible.
- other source-proven modifiers: active and visible by default.
- later stack/NBT-added AttributeModifiers: no global filtering is performed; their rows remain visible.

The old name-based global tooltip filtering remains absent.

## Cache

The converted candidate format did not change, so the cache identity stays:
`0.2.0-alpha.27-corpus4-local.11-rev227-cache.1`

The rev228 delivered BuildInfo class declared this field but did not initialize it; rev229 restores the intended runtime initialization without forcing a new conversion schema.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev229-corpus4-local.13.jar`
- bytes: `4,430,919`
- SHA-256: `fdc42e33da55687a088e50b6e59e773ec374d0ee4c85890e003d1d436397494f`
- internal version: `0.2.0-alpha.27-corpus4-local.13-rev229-local-test.1`
- converter revision: `2026-10-01.229-armor-display-hud-carrier-source-attributes`

## Binary audit vs exact rev228 base

- base ZIP entries: 1669
- output ZIP entries: 1671
- duplicate entries: 0
- removed entries: 0
- added:
  - `dev/yinghuang/legacyforgebridge/behavior/Rev229ArmorCompat.class`
  - `legacyforgebridge/rev229-build.json`
- changed existing:
  - `dev/yinghuang/legacyforgebridge/BuildInfo.class`
  - `dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorClient.class`
  - `dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class`
  - `fabric.mod.json`
- `META-INF/MANIFEST.MF`: byte-for-byte unchanged
- nested Energy JAR: byte-for-byte unchanged
- nested desktop-helper JAR: byte-for-byte unchanged
- ZIP integrity: pass
- selective attribute visibility harness: pass
- ASM BasicVerifier: pass for every changed/new runtime class

No Windows/Prism/Minecraft live launch was available in the build environment. Final in-game behavior is therefore still user-side validation.

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
