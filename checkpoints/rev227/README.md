# rev227 — iY projectile particles, armor reconversion, Cloth ABI

Target branch: `feature/generic-conversion-iyamato-corpus3`.

This checkpoint is a cumulative binary overlay on the exact rev226 artifact and retains every rev226 corpus4/Via/Bamboo/iY repair.

## Source-pinned iY projectile visuals

Exact source corpus:

- `iYAMATOs-Mod-1.7.10.jar`
- SHA-256: `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`

The original iY bytecode proves:

- `ItemIYKrisKnife` spawns `EntityIYMagicBullet`.
- `EntityIYMagicBullet.onUpdate` emits legacy `spell` every tick.
- While the magic bullet is in water it emits four legacy `bubble` particles using its current velocity.
- Iron, Golden, Diamond and Damascus thrown dagger impact methods each emit eight legacy `crit` particles with Y velocity `0.3`.

rev227 restores those effects only on the modern client presentation carriers. Server damage, hit, drop, potion, removal and explosion results remain authoritative.

For Minecraft 1.21.11 the legacy `spell` presentation uses modern `ParticleTypes.EFFECT` with an `EffectParticleEffect`; `crit` and `bubble` use their direct modern particle equivalents.

## iY armor HUD

rev226 already contains the exact source ArmorMaterial values:

- Tamahagane: 4 / 8 / 6 / 4
- Damascus: 4 / 9 / 7 / 4
- Heavy Damascus: 5 / 10 / 8 / 5

The live rev226 binary still advertised the rev220 cache-compatibility value. A previously generated `iYAMATOs-Mod-1.7.10-lfb` candidate could therefore be reused instead of being regenerated with the rev226 armor registration repair.

rev227 changes the cache compatibility identity to:

`0.2.0-alpha.27-corpus4-local.11-rev227-cache.1`

This intentionally forces legacy candidates through conversion again. No synthetic Armor Toughness is added.

## Cloth Config save crash

The supplied live log proves the rev226 screen called:

`BooleanListEntry.getValue(): boolean`

and crashed with `NoSuchMethodError` under Cloth Config 21.11.153.

rev227 routes Boolean, String and IntegerSlider values through an erased/reflection-safe compatibility reader, covering all value reads in the delivered rev226 screen rather than only the first failing Boolean call.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev227-corpus4-local.11.jar`
- bytes: `4,426,210`
- SHA-256: `b0695d57a67e2512e03f36ce776bcf0386f82b5e74b08b4177bf4272073b8912`
- internal version: `0.2.0-alpha.27-corpus4-local.11-rev227-local-test.1`
- converter revision: `2026-10-01.227-iy-projectile-particles-armor-cache-cloth`

## Binary audit vs exact rev226 base

- base entries: 1666
- output entries: 1668
- duplicate entries: 0
- removed entries: 0
- added:
  - `dev/yinghuang/legacyforgebridge/convert/Rev227Compat.class`
  - `legacyforgebridge/rev227-build.json`
- changed existing entries: 6
  - `BuildInfo.class`
  - `SourceProjectileRuntime.class`
  - `LegacyBlockingPoseConfigScreen.class`
  - `LegacyBlockingPoseConfigScreen$SliderSet.class`
  - `Rev226Compat.class`
  - `fabric.mod.json`
- nested Energy JAR: byte-for-byte unchanged
- ZIP integrity: pass
- offline particle harness: pass
- offline Cloth boxed-value harness: pass
- ASM BasicVerifier: pass for every changed/new runtime class

No Windows/Prism/Minecraft live launch was available in the build environment, so final live behavior remains user-side validation.

No Actions dispatch, PR, tag, release, workflow modification, force push, main branch modification, or Bamboo branch modification was performed.
