# rev226 — iY projectile motion, Damascus cube, armor HUD attributes, combined tooltip setting

Target branch: `feature/generic-conversion-iyamato-corpus3`.

This checkpoint supersedes the earlier rev226 candidate. The final artifact is a complete cumulative main-JAR overlay on the exact rev224 corpus4-local.8 binary while retaining the rev225 Via/foxfire fixes.

## iY projectile presentation

The rev224 live log loads 19 source projectile runtime rules but registers 24 iY remote projectile renderers. The five presentation identities without a source-runtime program are:

- 4804 ExtendedReach
- 4805 MagicBullet
- 4818 Bullet
- 4819 ShotShell
- 4823 ContractDocuments

Only those five receive fallback visual motion. The existing 19 source-runtime programs are not double-ticked.

Exact fallback motion:
- 4804 ExtendedReach: dry drag 1.0, gravity 0, water drag 0.8
- 4805 MagicBullet: dry drag 0.99, gravity 0, water drag 0.8
- 4818 Bullet: dry drag 0.99, gravity 0.04, water drag 0.8
- 4819 ShotShell: dry drag 0.99, gravity 0.04, water drag 0.8
- 4823 ContractDocuments: dry drag 0.99, gravity 0.03, water drag 0.8

Forge 1.7.10 only serializes exact spawn velocity for FML `IThrowableEntity`. Non-FML-throwable presentation carriers reconstruct their initial vector from packet yaw/pitch:
- 4800..4803 dagger family: speed 1.5
- 4804 ExtendedReach: speed 1.0
- 4823 ContractDocuments: speed 1.5

Client-only modern block collision is used for dagger / Contract presentation carriers so they stop at the block surface instead of integrating below terrain. Server damage, impact, drop, summon and entity-removal results remain authoritative.

## Damascus Steel Block

The exact iY source is a normal six-face cube:
- UP / DOWN: `damascus_steel_block_top`
- NORTH / SOUTH / WEST / EAST: `damascus_steel_block_side`

The exact iY source SHA is gated and the final model + all 16 `legacy_meta` blockstates are rewritten after simple block presentation. The later texture-atlas pass then materializes legacy `blocks/` resources into the modern atlas and rewrites the model references.

The branch also contains the generic external-enum ordinal source repair for ForgeDirection selectors.

## Armor HUD and source attributes

The vanilla armor HUD is driven by modern `Attributes.ARMOR`, not Armor Toughness. rev226 maps the exact iY 1.7.10 ArmorMaterial values into Armor:

- Tamahagane: 4 / 8 / 6 / 4
- Damascus: 4 / 9 / 7 / 4
- Heavy Damascus: 5 / 10 / 8 / 5

No synthetic Armor Toughness is added.

Heavy Damascus source bytecode already declares:
- Knockback Resistance: amount 0.25, operation 1
- Movement Speed: amount -0.01, operation 0

Those remain handled by the existing source-attribute runtime.

A development candidate patched the source-item Armor fallback too, which could double-apply Armor. The final rev226 intentionally leaves `LegacySourceItemRuntime.class` byte-for-byte identical to rev224 and adjusts only the generated item registration Armor input.

## ModMenu

One combined setting is exposed:

**Hide modern compatibility attributes**

It simultaneously hides the modern Armor / Movement Speed / Knockback Resistance tooltip rows while keeping all modifiers active. Saving applies immediately and requires no restart. The small compatibility preference file also migrates the temporary two-key development format if present.

## Retained earlier fixes

- rev225 ViaFabricPlus automatic 1.7.6-1.7.10 target selection
- rev225 Bamboo foxfire exact-own-main-hand visibility and picking
- rev224 Via block-carrier correction
- rev223 Bamboo shoot / iY presentation fixes

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev226-corpus4-local.10.jar`
- bytes: `4,413,224`
- SHA-256: `16df8e965bc429f9b66c14211e121fceed14c29fd3d1f2655e115916a6f12605`
- internal version: `0.2.0-alpha.27-corpus4-local.10-rev226-local-test.2`
- converter revision: `2026-09-30.226-projectile-motion-damascus-armor-ui`
- cache compatibility constant: `0.2.0-alpha.27-corpus3-local.3-rev220-local-test.1`

## Binary audit vs exact rev224 base

- base entries: 1664
- output entries: 1666
- duplicates: 0
- removed entries: 0
- added:
  - `dev/yinghuang/legacyforgebridge/convert/Rev226Compat.class`
  - `legacyforgebridge/rev226-build.json`
- changed existing entries: 12
- nested Energy JAR: byte-for-byte unchanged
- `LegacySourceItemRuntime.class`: byte-for-byte unchanged
- ZIP integrity: pass
- pure projectile / armor / Damascus compatibility tests: pass
- combined preference save/apply test: pass
- ASM BasicVerifier: pass for every changed/new runtime class plus the unchanged source-item runtime

No Windows/Prism/Minecraft live launch was available in the build environment. Live validation is therefore still user-side.

No Actions dispatch, PR, tag, release, workflow modification, force push, or main/Bamboo branch modification was performed.
