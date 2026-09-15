package dev.yinghuang.legacyforgebridge.convert;

import java.util.Map;

/**
 * Exact legacy {@code net.minecraft.init.Blocks} static-field identities that have stable vanilla
 * equivalents in modern Minecraft. The table is intentionally tiny and evidence-driven; unknown
 * fields remain unresolved instead of being inferred from type or position.
 */
public final class LegacyVanillaBlockIdentity {
    private static final String BLOCKS = "net/minecraft/init/Blocks";
    private static final Map<String,String> FIELD_IDS = Map.ofEntries(
            Map.entry("field_150458_ak", "minecraft:farmland"),
            Map.entry("farmland", "minecraft:farmland"),
            Map.entry("field_150349_c", "minecraft:grass_block"),
            Map.entry("grass", "minecraft:grass_block"),
            Map.entry("field_150346_d", "minecraft:dirt"),
            Map.entry("dirt", "minecraft:dirt"),
            Map.entry("field_150354_m", "minecraft:sand"),
            Map.entry("sand", "minecraft:sand"),
            Map.entry("field_150355_j", "minecraft:water"),
            Map.entry("water", "minecraft:water"),
            Map.entry("field_150358_i", "minecraft:water"),
            Map.entry("flowing_water", "minecraft:water")
    );

    private LegacyVanillaBlockIdentity() { }

    public static String modernBlockId(String owner, String fieldName, String descriptor) {
        if (!BLOCKS.equals(owner) || fieldName == null || descriptor == null) return null;
        if (!descriptor.startsWith("Lnet/minecraft/block/") || !descriptor.endsWith(";")) return null;
        return FIELD_IDS.get(fieldName);
    }
}
