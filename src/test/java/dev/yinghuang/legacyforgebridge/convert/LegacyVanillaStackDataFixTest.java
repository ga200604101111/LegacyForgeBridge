package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVanillaStackDataFixTest {
    @Test void productionHelperFlattensResolved1710StringRegistryNames() {
        assertEquals("minecraft:blue_wool", LegacyVanillaStackDataFix.upgrade("wool", 11).id());
        assertEquals("minecraft:charcoal", LegacyVanillaStackDataFix.upgrade("coal", 1).id());
        assertEquals("minecraft:birch_log", LegacyVanillaStackDataFix.upgrade("log", 2).id());
        assertEquals("minecraft:spruce_planks", LegacyVanillaStackDataFix.upgrade("planks", 1).id());
    }

    @Test void productionHelperPreservesVanillaDamageAsModernComponentData() {
        var stack = LegacyVanillaStackDataFix.upgrade("iron_sword", 5);
        assertEquals("minecraft:iron_sword", stack.id());
        assertTrue(stack.hasComponents());
        assertEquals(5, stack.components().get("minecraft:damage").getAsInt());
    }

    @Test void wildcardMetadataIsNotPretendedToBeAConcreteDfuStack() {
        assertThrows(IllegalArgumentException.class,
                () -> LegacyVanillaStackDataFix.upgrade("wool", LegacyRecipeStackResolver.WILDCARD_META));
    }
}
