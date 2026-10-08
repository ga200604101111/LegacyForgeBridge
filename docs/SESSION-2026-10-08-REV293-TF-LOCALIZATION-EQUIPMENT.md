# 2026-10-08 — rev293: actual converted Twilight Forest item names, armor slots/protection, sword baseline

Branch: `feature/generic-conversion-iyamato-corpus3`. The user supplied **both** original `twilightforest-1.7.10-2.3.8-tw.jar` and their actual post-rev292 `twilightforest-1.7.10-2.3.8-tw-lfb.jar`, plus screenshot evidence of literal `lfb.converted.twilightforest.item.item.nagaScale.name` / `plateNaga.name` names and unusable equipment.

## Real defect, not a missing texture

- Converted item `descriptionKey` had `lfb.converted.<namespace>.item.item.<registryName>.name` while shipped translations actually contained `lfb.converted.<namespace>.item.<registryName>.name`. **110/110 converted items** had this mismatch.
- Similarly, source converted block keys redundantly contained `tile.tile.`, for **35/35 registered blocks**.
- `kind:armor` was present for 28 Twilight Forest pieces, but the original armor slot field in their constructor `(ItemArmor$ArmorMaterial;II)V` had never entered modern runtime equipment assignment. A generic `Item` / no proven slot cannot equip as armor and does not receive correct source protection.
- Source material declarations in the real JAR contain Forge `EnumHelper.addArmorMaterial` and `EnumHelper.addToolMaterial` constructor constants. Do not guess armor/weapon attributes from display name, texture, or item ID.

## Production changes committed

`LegacyMaterial1710Analyzer`: source-wide exact ASM extraction for the two Forge source material registration shapes. Source-owner and primitive constructor constants are required; unproved/dynamic declarations are not admitted. The exact uploaded original JAR yielded **8 unique armor materials** with source protection points and **7 unique tool materials** with source attack bonus and durability.

`LegacyItemSemanticsRecoveryPass`: runs after earlier source content and bulk visual recovery stages, before generated semantic code registration. It:

1. normalizes source-generated `item.item.` and `tile.tile.` translation keys to the existing `item.` and `tile.` entries, with language fallbacks derived from source registry identity (no mod-specific dispatch);
2. restores missing localized entries into the actual 5 shipped locale JSON files when the original source key or metadata variant supports the translation;
3. for ItemArmor constructor descriptor and proven source materials, publishes exact armor slot `armor_slot_0..3`, source protection, material durability and metadata;
4. for seven proved ordinary ItemSword materials, publishes a **modern adapter baseline** attack (4 + original tool material damage bonus) and explicit attack speed `-2.4`. This is not equivalent to every original 1.7.10 mod damage path or special weapon behavior;
5. writes source-only audit `legacyforgebridge/item-semantics-recovery.json`.

`LegacySourceArmorSlotFallback.choose(kind,oldSlot)`: runtime equipment fallback prefers a valid previously source-proven slot, otherwise parses exactly `armor_slot_[0..3]`; invalid/unproved slots are never guessed.

The cumulative main binary was generated using exact rev292 as its base and verified Java21 compiled classes, not a root source rebuild. `GenericLegacyModProfile.configure` injects this new Pass after the shipped batch visual recovery Pass; `GeneratedModSupport.registerItem` invokes the slot fallback before equippable item construction. `BuildInfo`, `fabric.mod.json` and `META-INF/MANIFEST.MF` contain truthful new revision, version/cache invalidation and local assembly provenance; existing rev292 Prism watchdog, nested desktop helper, pre-rev293 feature set and original resources remain byte preserved.

The source checkpoint also restores missing cumulative imports/ordering for `LegacyBatchVisualRecoveryPass` and `LegacyItemSemanticsRecoveryPass` in root `GenericLegacyModProfile.java`, and updates root `GeneratedModSupport.java` to match its tested equipment slot fallback. **Do not claim a fresh whole-repo Gradle/Loom build** because legacy rev256–260 overlay source gaps remain.

## Source-derived test results

On the **exact user-uploaded Twilight Forest JAR and converted candidate**, local staging processing recovered:

| Category | Count |
| --- | ---: |
| Corrected item translation keys | 110 |
| Corrected block translation keys | 35 |
| Additional missing locale entries filled | 129 |
| Armor equipment slots from source constructors | 28 |
| Armor protection from registered material arrays | 28 |
| Base ordinary sword attack from registered tool materials | 7 |

Examples: Naga chestplate source armor protection `7`; Fiery Sword modern baseline attack `8` (from source tool bonus `4`, plus ItemSword baseline `4`). Source registered armor protections and tools were read from the actual original `-tw.jar`, not hand-entered Twilight Forest tables.

The actual **assembled final JAR** was inspected with ZIP CRC and class diff. A standalone Java21 runner calling its packaged Generic profile, newly compiled source pass and packaged semantic code generator, using **temporary external Gson and ASM-compatible JDK test facilities**, verified source output, Pass ordering and the actual newly generated item registration bytecode. E.g. generated bytecode embeds the expected `lfb.converted.twilightforest.item.nagaScale.name` and `armor_slot_1`. A separate test verified the runtime fallback bytecode is called before the equipment slot switch. Original rev292 DesktopHelper and packaged Prism restart patch remain identical.

**Crucial qualification:** The final JAR has not been started inside a real Minecraft 1.21.11 / Fabric Loader / ViaFabricPlus client, nor tested for live wearable armor/tooltip behavior or original Forge 1.7.10 multiplayer. ASM/JUnit/Gson test doubles are not the actual loader API. Thus this is a **test preview**, not production acceptance.

## Complete main preview binary

`legacyforgebridge-0.2.0-alpha.27-rev293-localization-armor-sword-preview.jar`

- SHA-256: `20cbb158a1dace616521a487aa4e072eaeef685bd41b9baefb3d2339a463c570`
- 4,991,404 bytes; **1,842 class entries**.
- Compared to rev292: **7 compiled classes added**, one provenance JSON added, **5 files replaced**, **0 files removed**.
- Original rev292 SHA-256: `3dee8cd19bcfe82daaab23c8ae9e2f0b8041f6aa2f73bbd5bda1b3f224190574`.
- Archive CRC passed. No original Forge or user source JARs, server or cached mod files were rewritten.

## Player acceptance checklist

1. Back up PrismLauncher instance. Place **only rev293 full main** in Fabric 1.21.11 client `mods`, move rev292 and earlier main JARs out; maintain required client dependencies.
2. Keep original Twilight Forest `twilightforest-1.7.10-2.3.8-tw.jar` **only** in `old-mods`, and avoid installing a manual old converted `-lfb.jar` into `mods`.
3. Restart/reconvert when prompted; cache fingerprint/BuildInfo changed for rev293. Open newly converted JAR and inspect `legacyforgebridge/item-semantics-recovery.json`.
4. Confirm translated Naga Scale/plate names; Naga chestplate is equippable and shows actual 7 armor points; ordinary Fiery Sword displays appropriate modern baseline; re-check Prism restart.
5. On any failure submit **rev293** `logs/latest.log` plus freshly generated Twilight Forest `-lfb.jar`, and screenshots of item tooltips/equipment behavior, not an old rev292 cache file.

## Still NOT completed

The user's other failures are genuine and remain **unfixed in rev293**: bows’ actual firing/pulling, thrown entities, 77 living/entity render families, complete entity AI/bosses, many special blocks’ per-metadata appearance, correct held weapon 3D poses, animated equipment armor models, special item skills, equipment set effects, worldgen/progression, runtime packet synchronization. A static icon or source attribute does not prove game behavior. Continue prioritizing source-driven common runtime families on the exact real mod corpus and test in-game rather than declaring whole-mod compatibility.

All commits only on the feature branch with `[skip ci] [skip actions]`. No GitHub Actions, CI, PR, release, tag, default/Bamboo branch modifications or server edits.
