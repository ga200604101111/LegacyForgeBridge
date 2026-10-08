# rev293 genericity audit — item localization, armor and weapon properties (2026-10-08)

This is an **audit of the actual rev293 preview JAR and Twilight Forest source/staging**, not a new gameplay or mod release.

## Findings

The generic conversion code does **not** include a hardcoded Twilight Forest name/ID/material list. However it only handles selected **source bytecode families**, so the wording “all Forge 1.7.10 mods are supported” is not justified.

| Feature | Source rule currently implemented | Evidence in the tested Twilight Forest JAR | Uncovered family / acceptance gap |
| --- | --- | --- | --- |
| Item/block localized name | Repair exact duplicated `item.item.` / `tile.tile.` `descriptionKey` prefix, then resolve names from staged `lang/*.json`; add missing locale keys using English/name fallback | 110 item keys and 35 block keys present across five staged locales | Source unlocalized name not matching registry name, per-metadata/per-NBT display name, custom `getItemStackDisplayName`, arbitrary localization keys, lack of actual translations. Fall back to English is not proof of localization. |
| Armor slots | Exact source `ItemArmor$ArmorMaterial;II` constructor plus numeric third argument `armorType` 0–3; forward into `armor_slot_N` and `LegacySourceArmorSlotFallback` | 28 / 28 identified in the local Twilight corpus | Other/extended constructor shapes, indirect constructor args and dynamic slot selection unproved; no live inventory/equip test |
| Armor protection and durability | Source-owner `EnumHelper.addArmorMaterial(String,int,int[],int)` canonical `<clinit>` adjacent `PUTSTATIC` pattern, 4 fixed values, vanilla part durability multipliers | 8 source armor materials, 28 armor item values published in staging | Material declared through wrappers, alternate bytecode shape, item-level overrides, repair/enchant, toughness, knockback resistance, set effects and any full original gameplay semantics |
| Sword basic attack | Standard source `ItemSword(Item.ToolMaterial)` and `EnumHelper.addToolMaterial(String,int,int,float,float,int)` canonical static assignment | 7 source tool material declarations; 7 `attackDamage` fields emitted, e.g. 8.0 for fiery sword | Source `onHit`/override damage, bows, staffs, axes/picks/other tools, custom combat, 1.7 combat vs modern speed differences; -2.4 is a modern adapter, not a source attribute |
| 1.21.11 equippability | Packaged `GeneratedModSupport.registerItem` selects `EquipmentSlot` and applies an equippable component and armor attribute to ordinary converted items | Bytecode instruction path exists in packaged rev293 | **Never tested in actual Minecraft 1.21.11**, durability/combat parity unknown |
| Worn armor visuals | Requires 1.21.11 `assets/<namespace>/equipment/*.json`, equipment asset ID on equippable, converted humanoid/humanoid_leggings textures | Twilight source has 1.7.10 `assets/twilightforest/textures/armor/*_1.png/*_2.png`, while the uploaded converted `-lfb.jar` has **zero** modern equipment JSONs | **Not implemented:** armors may have a slot and attribute but still render incorrectly/invisibly on entities |

The pack includes 110 registered items and 35 blocks. Within staged output, all 145 expected canonical translation keys have `en_us` and `zh_tw` values, **but that is not evidence that every translation is native, semantically correct or works in a live loader**. The local transform filled 129 absent locale entries across five languages; some of those use English fallback.

## Critical provenance safeguards to implement before describing broader compatibility

1. Follow the **actual item unlocalized-name producer** and source localization keys, not only `GameRegistry` name. Distinguish source-backed locale strings from inserted English fallback. Include metadata/NBT translation variants.
2. Resolve `ArmorMaterial`, `ItemArmor.armorType`, durability, enchantability, texture layers and any overrides through source-dataflow; do not overwrite stronger existing proof. Report skipped reasons on unknown methods.
3. Convert old `*_1.png` and `*_2.png` armor materials into 1.21.11 humanoid / humanoid_leggings assets and supply matching `minecraft:equippable.asset_id`. Prove material assignment for the item rather than match by mod name.
4. Generalize tool families beyond the specific direct `ItemSword` constructor and prove attribute modifiers and use behavior separately. **Never client-replay legacy server combat**.
5. Add unrelated synthetic/non-Twilight mods with inherited constructors, alternate material initialization, custom display name and overriden armor/item attributes; retain fail-closed behavior.
6. Require a full original-JAR conversion followed by real Fabric 1.21.11 client inventory/equipment/render screenshots and unchanged 1.7.10 Forge server comparisons before promoting an item to **playable**.

## Safety/status

This audit did **not** modify the prior released rev293 complete main JAR, translated Twilight Forest JAR or original server. No new installation artifact, Gradle/Loom run or live-game validation is claimed. The rev293 preview is still a **partial candidate**.

See `src/main/java/dev/yinghuang/legacyforgebridge/convert/LegacyMaterial1710Analyzer.java`, `convert/pass/LegacyItemSemanticsRecoveryPass.java` (as included in rev293 compiled main and local staging), and the existing `GeneratedModSupport` / `Corpus3ItemRuntimeSupport` bytecode in the rev293 binary. The root repository source may lag behind the latest compiled main overlays; this audit is about the actual rev293 JAR, not a presumed full current Gradle build.
