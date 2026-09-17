package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyGridPotVanillaInsertion1710Test {
    @Test
    void tableContainsOnlyMetadataIndependentOneToOneVanillaBlocks() {
        var rules = LegacyGridPotVanillaInsertion1710.rules();
        assertEquals(Set.of("brown_mushroom", "red_mushroom", "cactus"),
                rules.stream().map(LegacyGridPotVanillaInsertion1710.Rule::legacyRegistryName).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:cactus"),
                rules.stream().map(LegacyGridPotVanillaInsertion1710.Rule::modernId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(1, 13),
                rules.stream().map(LegacyGridPotVanillaInsertion1710.Rule::legacyRenderType).collect(java.util.stream.Collectors.toSet()));
        for (String ambiguous : new String[]{"sapling", "tallgrass", "red_flower", "double_plant"})
            assertTrue(LegacyGridPotVanillaInsertion1710.byLegacyRegistryName(ambiguous).isEmpty(), ambiguous);
    }
}
