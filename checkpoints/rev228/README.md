# rev228 — armor HUD semantics and intrinsic-vs-NBT attribute visibility

Target branch: `feature/generic-conversion-iyamato-corpus3`.

This checkpoint is a cumulative binary overlay on the exact rev227 artifact and retains every rev227 projectile-particle, reconversion and Cloth Config ABI repair.

## Armor HUD

The 1.21.11 armor bar is driven by the modern `ARMOR` attribute. rev228 therefore keeps the exact legacy 1.7.10 protection points as the modern `ARMOR` modifier used by the client HUD; it does **not** substitute `ARMOR_TOUGHNESS`, because toughness does not drive the armor HUD.

Retained exact iY values:
- Tamahagane: 4 / 8 / 6 / 4
- Damascus: 4 / 9 / 7 / 4
- Heavy Damascus: 5 / 10 / 8 / 5

The bridge-created armor modifier is now marked with `ItemAttributeModifiers.Display.hidden()`. The modifier remains active for HUD calculation but its intrinsic compatibility row is not shown in the item tooltip.

## Intrinsic attributes versus NBT/stack attributes

rev226/rev227 used a late tooltip text filter that removed rows by generic attribute name. That could also erase a later NBT/stack-added modifier with the same attribute type.

rev228 removes that text filter completely.

For armor-slot legacy equipment:
- bridge-created/base Armor is hidden at the base modifier entry;
- source-proven intrinsic equipment modifiers are hidden at their base modifier entries;
- source-proven `generic.movementSpeed` is no longer added to the converted armor-item client defaults;
- stack/NBT-added AttributeModifiers are not filtered and remain visible.

This means a plugin/command/other system can add extra Armor, Knockback Resistance, Movement Speed, or another attribute through NBT/components and those extra rows remain visible.

The old global **Hide modern compatibility attributes** ModMenu toggle is removed because visibility is now attached to the intrinsic modifier itself rather than implemented as a global text deletion pass.

Server gameplay remains authoritative on the unchanged Forge 1.7.10 server.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev228-corpus4-local.12.jar`
- bytes: `4,427,376`
- SHA-256: `18267cdbfa0cb54440fec940fdf9e4fccf4760fb16311595db694940996795ad`
- internal version: `0.2.0-alpha.27-corpus4-local.12-rev228-local-test.1`
- converter revision: `2026-10-01.228-iy-armor-hud-base-attribute-visibility`
- cache compatibility: unchanged from rev227: `0.2.0-alpha.27-corpus4-local.11-rev227-cache.1`

## Binary audit vs exact rev227 base

- base entries: 1668
- output entries: 1669
- duplicate entries: 0
- removed entries: 0
- added: `legacyforgebridge/rev228-build.json`
- changed existing entries: 6
  - `BuildInfo.class`
  - `GeneratedModSupport.class`
  - `LegacySourceItemRuntime.class`
  - `LegacyBehaviorClient.class`
  - `LegacyBlockingPoseConfigScreen.class`
  - `fabric.mod.json`
- manifest: byte-for-byte unchanged
- nested Energy JAR: byte-for-byte unchanged
- ZIP integrity: pass
- structural bytecode audit: pass
- ASM BasicVerifier: pass for every changed runtime class

No Windows/Prism/Minecraft live launch was available in the build environment; live behavior remains user-side validation.

No Actions dispatch, PR, tag, release, workflow modification, force push, main branch modification, or Bamboo branch modification was performed.
