package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotVanillaInsertion1710Test {
    @Test
    void tableRetainsExactOneToOneAndLegacyMetaDemultiplexMappings() {
        assertEquals(Set.of("brown_mushroom", "red_mushroom", "yellow_flower", "deadbush", "cactus"),
                LegacyGridPotVanillaInsertion1710.rules().stream()
                        .map(LegacyGridPotVanillaInsertion1710.Rule::legacyRegistryName)
                        .collect(java.util.stream.Collectors.toSet()));

        assertEquals(Map.of(
                0, "minecraft:oak_sapling", 1, "minecraft:spruce_sapling", 2, "minecraft:birch_sapling",
                3, "minecraft:jungle_sapling", 4, "minecraft:acacia_sapling", 5, "minecraft:dark_oak_sapling"),
                LegacyGridPotVanillaInsertion1710.variantFamily("sapling").orElseThrow().modernIdsByMeta());
        assertEquals("minecraft:azure_bluet",
                LegacyGridPotVanillaInsertion1710.variantFamily("red_flower").orElseThrow().modernIdsByMeta().get(3));
        assertEquals("minecraft:tall_grass",
                LegacyGridPotVanillaInsertion1710.variantFamily("double_plant").orElseThrow().modernIdsByMeta().get(2));
        assertEquals("minecraft:large_fern",
                LegacyGridPotVanillaInsertion1710.variantFamily("double_plant").orElseThrow().modernIdsByMeta().get(3));
        assertEquals(Map.of(0, "minecraft:dead_bush", 1, "minecraft:short_grass", 2, "minecraft:fern"),
                LegacyGridPotVanillaInsertion1710.variantFamily("tallgrass").orElseThrow().modernIdsByMeta());
        assertTrue(LegacyGridPotVanillaInsertion1710.variantFamily("wool").isEmpty());
    }
}
