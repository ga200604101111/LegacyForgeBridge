# rev234 — native ARMOR supplement and generic source-attribute visibility

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev234 is a cumulative local binary overlay on the exact rev233 artifact. The rev233 Bamboo spa-water fixes are retained byte-for-byte.

## Live finding: rev233 mixed armor was wrong

The rev233 client HUD compatibility replaced the return value of `LivingEntity#getArmorValue()`. A mixed set such as vanilla diamond armor plus converted iY armor therefore displayed only the recomputed converted contribution and hid the already-correct modern/vanilla contribution.

rev234 removes that return-value replacement.

Instead, immediately before vanilla `getArmorValue()` runs, LFB reconciles **temporary modifiers on the local player's real 1.21.11 `Attributes.ARMOR` instance**.

- existing vanilla/high-version armor remains untouched;
- source-proven 1.7.10 armor points are added only for converted legacy equipment;
- explicit stack/NBT ARMOR modifiers on converted equipment are mirrored with their original operation;
- LFB item-level compatibility carriers are excluded from the mirror;
- if another modern/Via path already applied the original base or NBT modifier ID to the entity, LFB does not duplicate it;
- removing the converted equipment removes the temporary LFB runtime modifiers;
- modifiers are temporary/client-only and are never serialized back to the unchanged 1.7.10 server.

The HUD now remains fully vanilla: it simply reads the real high-version ARMOR attribute.

## Generic movement-speed visibility

rev229 had one policy that hid `generic.movementSpeed` whenever it came from source-proven armor attributes. That was too broad.

rev234 changes the rule to be source-driven and mod-agnostic:

- every modifier present in the source-item contract with `attributesProven=true` is added with the normal visible modern display;
- no mod id, item class name, iY name, RPGTool name or armor/non-armor special case decides visibility;
- if the source analyzer did not prove a modifier, the runtime never invents it;
- later stack/NBT modifiers remain independent and visible.

Exact corpus spot-check:
- iY `ItemIYHeavyDamascusSteelArmor#func_111205_h()` returns both SharedMonsterAttributes knockback resistance and movement speed modifiers;
- RPGTool `CircleBase` and `WingBase` do not override `getItemAttributeModifiers/func_111205_h`.
This difference now falls out of the generic source proof instead of a per-mod exception.

## Validation

A dedicated mixed-armor harness passed:
- existing modern armor = 8;
- converted legacy base armor = 3;
- explicit stack/NBT armor = 2;
- synchronized ARMOR value = 13;
- after removing converted gear = 8;
- if the base/NBT IDs are already present on the modern entity, no LFB runtime duplicate is added.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev234-corpus4-local.18.jar`
- bytes: `4,454,229`
- SHA-256: `cc79a54f2360978c66135bcdfd07df3220439b07863ca73ec0c7d1415c8e2d97`
- internal version: `0.2.0-alpha.27-corpus4-local.18-rev234-local-test.1`
- converter revision: `2026-10-01.234-native-armor-attribute-source-modifier-visibility`
- cache compatibility retained from rev233: `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`

## Binary audit vs rev233

- base entries: 1679
- output entries: 1682
- duplicate entries: 0
- removed entries: 0
- added:
  - `Rev234ArmorAttributeSync.class`
  - `Rev234ArmorAttributeSync$Wanted.class`
  - `legacyforgebridge/rev234-build.json`
- changed existing:
  - `BuildInfo.class`
  - `Rev229ArmorCompat.class`
  - `LegacyLivingBehaviorMixin.class`
  - `fabric.mod.json`
- manifest byte-for-byte unchanged
- nested Energy JAR byte-for-byte unchanged, SHA-256 `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`
- rev233 liquid runtime/helper/mixin classes byte-for-byte unchanged
- ZIP integrity: pass
- javap parse for all changed/new runtime classes: pass
- live Minecraft launch: not available in the build environment

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
