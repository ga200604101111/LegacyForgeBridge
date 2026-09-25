package dev.yinghuang.legacyforgebridge.convert;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minecraft 1.7.10 vanilla Block identities admitted by the GridPot positive insertion runtime.
 * Variant families retain the exact legacy item-meta to modern block-id demultiplexing instead of
 * treating a legacy render type or bare registry name as a modern identity.
 */
public final class LegacyGridPotVanillaInsertion1710 {
    public record Rule(String legacyRegistryName, int legacyRenderType, String modernId) {
        public Rule { validate(legacyRegistryName, legacyRenderType, modernId); }
    }

    public record VariantFamily(String legacyRegistryName, int legacyRenderType, Map<Integer,String> modernIdsByMeta) {
        public VariantFamily {
            if (legacyRegistryName == null || legacyRegistryName.isBlank() || !supportedRenderType(legacyRenderType)
                    || modernIdsByMeta == null || modernIdsByMeta.isEmpty())
                throw new IllegalArgumentException("Invalid 1.7.10 GridPot vanilla variant family");
            LinkedHashMap<Integer,String> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer,String> entry : modernIdsByMeta.entrySet()) {
                if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null
                        || !entry.getValue().startsWith("minecraft:") || copy.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new IllegalArgumentException("Invalid 1.7.10 GridPot vanilla variant mapping");
            }
            modernIdsByMeta = Map.copyOf(copy);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("brown_mushroom", 1, "minecraft:brown_mushroom"),
            new Rule("red_mushroom", 1, "minecraft:red_mushroom"),
            new Rule("yellow_flower", 1, "minecraft:dandelion"),
            new Rule("deadbush", 1, "minecraft:dead_bush"),
            new Rule("cactus", 13, "minecraft:cactus")
    );

    private static final List<VariantFamily> VARIANT_FAMILIES = List.of(
            new VariantFamily("sapling", 1, Map.of(
                    0, "minecraft:oak_sapling",
                    1, "minecraft:spruce_sapling",
                    2, "minecraft:birch_sapling",
                    3, "minecraft:jungle_sapling",
                    4, "minecraft:acacia_sapling",
                    5, "minecraft:dark_oak_sapling")),
            new VariantFamily("tallgrass", 1, Map.of(
                    0, "minecraft:dead_bush",
                    1, "minecraft:short_grass",
                    2, "minecraft:fern")),
            new VariantFamily("red_flower", 1, Map.ofEntries(
                    Map.entry(0, "minecraft:poppy"),
                    Map.entry(1, "minecraft:blue_orchid"),
                    Map.entry(2, "minecraft:allium"),
                    Map.entry(3, "minecraft:azure_bluet"),
                    Map.entry(4, "minecraft:red_tulip"),
                    Map.entry(5, "minecraft:orange_tulip"),
                    Map.entry(6, "minecraft:white_tulip"),
                    Map.entry(7, "minecraft:pink_tulip"),
                    Map.entry(8, "minecraft:oxeye_daisy"))),
            new VariantFamily("double_plant", 40, Map.of(
                    0, "minecraft:sunflower",
                    1, "minecraft:lilac",
                    2, "minecraft:tall_grass",
                    3, "minecraft:large_fern",
                    4, "minecraft:rose_bush",
                    5, "minecraft:peony"))
    );

    private LegacyGridPotVanillaInsertion1710() { }

    public static List<Rule> rules() { return RULES; }
    public static List<VariantFamily> variantFamilies() { return VARIANT_FAMILIES; }

    public static Optional<Rule> byLegacyRegistryName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return RULES.stream().filter(rule -> rule.legacyRegistryName().equals(name)).findFirst();
    }

    public static Optional<VariantFamily> variantFamily(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return VARIANT_FAMILIES.stream().filter(rule -> rule.legacyRegistryName().equals(name)).findFirst();
    }

    public static List<String> modernIdsForRenderTypes(Set<Integer> renderTypes) {
        Set<Integer> accepted = renderTypes == null ? Set.of() : Set.copyOf(renderTypes);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Rule rule : RULES) if (accepted.contains(rule.legacyRenderType())) ids.add(rule.modernId());
        for (VariantFamily family : VARIANT_FAMILIES) if (accepted.contains(family.legacyRenderType())) ids.addAll(family.modernIdsByMeta().values());
        return ids.stream().sorted().toList();
    }

    private static void validate(String name, int renderType, String modernId) {
        if (name == null || name.isBlank() || !supportedRenderType(renderType)
                || modernId == null || !modernId.startsWith("minecraft:"))
            throw new IllegalArgumentException("Invalid 1.7.10 GridPot vanilla insertion rule");
    }
    private static boolean supportedRenderType(int value) { return value == 1 || value == 13 || value == 40; }
}
