# rev308 — client display and interaction bridge for legacy Twilight Forest

**Target:** Fabric 1.21.11 client, unchanged original Forge 1.7.10 server. Continue only on \`feature/generic-conversion-iyamato-corpus3\`, no Actions or PR.

## Exact base and deliverable

- Input full main: \`legacyforgebridge-0.2.0-alpha.27-rev307-legacy-mod-config-menu-v2.jar\`, SHA-256 \`42c12ff87acd5261935d7d079513b777b2fd8ded4907c73e315e27fa374a909f\`.
- Output **complete installable main candidate**: \`legacyforgebridge-0.2.0-alpha.27-rev308-twilight-sword-arrow-equipment.jar\`, SHA-256 \`40fd1ae599c1a0ca07346ebd4336df3584f9385dc284e14e9d59d55ab7722dbd\`; 5,203,488 bytes.
- JAR remains a local chat delivery; only source and reproducibility records are committed, never original third-party JARs.
- Version \`0.2.0-alpha.27-corpus4-local.61-rev308-twilight-sword-arrow-equipment.1\`.
- Converter semantic revision and cache compatibility identity are unchanged from rev306. These are runtime-only additive hooks, not a silent full regeneration of converted source.

## Changes

1. **Sword inherited right-click**: \`Rev308SwordBridge\` marks only source-classified converted sword items with native \`minecraft:blocks_attacks\` and supplies a BLOCK animation, use duration and \`startUsingItem\` only when existing source handlers returned PASS. Keeps the global LFG Cloth blocking-pose config and player/client-owned visuals. Vanilla weapons and other native Fabric items are untouched. Actual combat and server decisions remain Forge authoritative.
2. **Twilight Forest seeker arrow**: SHA-pinned proof from the exact \`twilightforest-1.7.10-2.3.8-tw.jar\` (SHA-256 \`1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589\`) registers remote FML EntitySeekerArrow as ID 16 with range 150 / frequency 1 in the client projectile catalogue. Admitted as ARROW / ORIENTED_ITEM carrier using vanilla ArrowRenderer presentation, no local projectile motion or damage simulation. Duplicate/conflicting registries fail closed.
3. **Tooltip text**: Existing aliased \`lfb.converted.*.name\` language key is used to look up its adjacent \`.tooltip\` key, only when it exists and isn't already in the tooltip. No machine translation is invented.
4. **Creative inventory enchantments**: Source-confirmed \`getSubItems\` defaults are listed for 21 ironwood, steeleaf, mazebreaker and yeti equipment keys; applies only to newly emitted creative stacks if no enchantment exists. Never mutates remote inventory, crafting, item drops or server-owned stacks. Requires the live client enchantment dynamic registry.

## Source layout and build technique

- \`src/main/java/dev/yinghuang/legacyforgebridge/rev308/\` contains four new source helpers, with explicit **intermediary** \`net.minecraft.class_*\` signatures matching remapped rev307 compiled classes.
- \`tools/TransformRev308.java\` inserts seven guarded source-honoring hook categories into five exact existing class methods using JDK-internal ASM during **offline packaging only**. ASM is not bundled into the JAR.
- \`tools/package_rev308.py\` packages the modified compiled files plus the two SHA-bound \`.properties\` files on top of the exact rev307 binary. The packaging script expects Java 21 compiled helper outputs in \`/mnt/data/lfb308work/rev308classes\`; it is not a standalone standard Gradle build. Original Forge mod JARs are not executed.
- At compilation, temporary API signature stubs stood in for external Minecraft remapped types; none is shipped. The real Minecraft/Fabric API and game runtime were not executed.

## Offline checks and boundaries

- ZIP integrity PASS, duplicates NONE, forbidden bundled ASM NONE.
- Seven existing entries patched/replaced (including BuildInfo and fabric.mod.json), six new entries, **1,979 original rev307 non-target ZIP entry payloads byte-identical**.
- \`fabric.mod.json\` and compiled \`BuildInfo.VERSION\` match.
- No real Minecraft 1.21.11, Mod Menu, Cloth Config, ViaFabricPlus 4.4.15, Forge 1.7.10 server, projectile network spawn, right-click gameplay or creative enchant tests executed. Treat rev308 as an **experimental candidate**, not a confirmed live fix.
- In-game validation should inspect seeker arrow visible after firing, ordinary sword right-click with the user's current blocking pose offsets, item tooltips, creative-mode enchanted gear and existing server-owned equipment. Keep rev307 .2 for rollback. Never install both LFB JARs concurrently.

This checkpoint does not imply complete Twilight Forest entity, GUI, item durability, or RPG mechanics compatibility.
