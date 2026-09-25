package dev.yinghuang.legacyforgebridge.convert.pass;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotVanillaInsertionEligibilityPassTest {
    @Test
    void metadataFamiliesExpandOnlyInsideTheirProvenLegacyRenderTypes() {
        var type1 = LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(1));
        assertEquals(21, type1.size());
        assertTrue(type1.contains("minecraft:oak_sapling"));
        assertTrue(type1.contains("minecraft:dark_oak_sapling"));
        assertTrue(type1.contains("minecraft:poppy"));
        assertTrue(type1.contains("minecraft:azure_bluet"));
        assertTrue(type1.contains("minecraft:short_grass"));
        assertTrue(type1.contains("minecraft:dandelion"));
        assertFalse(type1.contains("minecraft:sunflower"));
        assertFalse(type1.contains("minecraft:cactus"));

        assertEquals(java.util.List.of("minecraft:cactus"), LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(13)));

        var type40 = LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(40));
        assertEquals(Set.of("minecraft:sunflower", "minecraft:lilac", "minecraft:tall_grass",
                        "minecraft:large_fern", "minecraft:rose_bush", "minecraft:peony"),
                Set.copyOf(type40));

        var all = LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(1, 13, 40));
        assertEquals(28, all.size());
        assertFalse(all.contains("minecraft:oak_log"));
    }
}
