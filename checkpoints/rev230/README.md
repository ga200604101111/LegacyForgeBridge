# rev230 — preserve base armor when legacy NBT AttributeModifiers override the modern stack component

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev230 is a cumulative local binary overlay on the exact rev229 artifact and retains the earlier Via/Bamboo/iY projectile, particle, armor and Cloth Config fixes.

## Why rev229 was not enough

Minecraft 1.21.11 reads an explicit stack `ATTRIBUTE_MODIFIERS` component in place of the item's default attribute component. Via converts legacy 1.7.10 `AttributeModifiers` NBT into that explicit modern stack component.

In 1.7.10, armor protection belongs to `ItemArmor` separately from the stack NBT attribute list. Therefore adding NBT attributes to an armor item can replace the item's attribute-modifier map while the ordinary armor protection still remains.

rev229 hid the base modern armor modifier correctly for a stack with no explicit attribute component, but did not reinsert that hidden armor carrier when Via produced an explicit component from legacy NBT. It also manually added a visible base-armor tooltip row, which does not match the requested 1.7.10 presentation.

## rev230 behavior

Clientbound at the final Via 1.21.11 item boundary:
- if the converted armor stack has no explicit attribute component, its existing item-default hidden `ARMOR` carrier is left alone;
- if the stack has an explicit attribute component produced from legacy NBT, all existing modifiers are preserved unchanged and one hidden LFB `minecraft:armor` carrier is appended using the source 1.7.10 armor points;
- the carrier is idempotent and has an LFB-owned namespaced modifier ID;
- NBT-added Armor, Knockback Resistance, Movement Speed and other modifiers remain visible according to their original converted display state.

Serverbound before the final modern mapping:
- only modifiers with the LFB compatibility-carrier ID prefix are removed;
- the original legacy/NBT modifiers are left unchanged for Via to downgrade back to the 1.7.10 server;
- no compatibility armor modifier is persisted into the server's original NBT.

The rev229 synthetic visible base-armor tooltip row is removed. Base armor is therefore hidden as it is in the intended 1.7.10 presentation, while extra NBT modifiers stay visible.

With explicit NBT AttributeModifiers, source attribute modifiers such as iY Heavy Damascus movement speed/knockback follow the legacy stack override behavior. The ordinary armor protection is the one special value re-added client-side because it is separate from the legacy attribute list.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev230-corpus4-local.14.jar`
- bytes: `4,436,048`
- SHA-256: `bc5c2be4029b9ff700da56dcebf87fe6146eaff4f02811b1d3f1cea111b95bb1`
- internal version: `0.2.0-alpha.27-corpus4-local.14-rev230-local-test.1`
- converter revision: `2026-10-01.230-legacy-nbt-armor-merge`
- cache compatibility: unchanged: `0.2.0-alpha.27-corpus4-local.11-rev227-cache.1`

## Build and validation

Build method: exact local binary overlay on rev229 using JDK 21 internal ASM for the three changed runtime classes plus a compiled `Rev230ArmorNbtMerge` helper, followed by a ZIP-preserving rebuild that copied the rev229 manifest and all untouched entries byte-for-byte.

Binary audit vs exact rev229 base:
- base entries: 1671
- output entries: 1673
- duplicate entries: 0
- removed entries: 0
- added: `dev/yinghuang/legacyforgebridge/behavior/Rev230ArmorNbtMerge.class`, `legacyforgebridge/rev230-build.json`
- changed existing: `BuildInfo.class`, `LegacyBehaviorClient.class`, `ViaModernModItemBoundaryMixin.class`, `fabric.mod.json`
- `META-INF/MANIFEST.MF`: byte-for-byte unchanged
- nested Energy JAR: byte-for-byte unchanged, SHA-256 `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`
- nested desktop-helper JAR: byte-for-byte unchanged, SHA-256 `7f38776ae4e19f6beca309e59758f2075ded4d8d3464ca3a49a27fb7f9f6d1ed`
- ZIP integrity: pass
- offline explicit-NBT armor merge/strip harness: pass
- ASM BasicVerifier: pass for all four changed/new runtime classes

No Windows/Prism/Minecraft live launch was available in the build environment, so final in-game validation remains user-side.

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
