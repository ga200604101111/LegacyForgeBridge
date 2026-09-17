package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotVanillaInsertionRegistryTest {
    @Test
    void parserAcceptsMixedConvertedAndSafeVanillaPositiveEligibilityIds() {
        JsonObject value = new JsonObject();
        value.addProperty("id", "fixture:grid");
        value.addProperty("cells", 9); value.addProperty("gridWidth", 3);
        value.addProperty("baseHeight", 0.01F); value.addProperty("cellHeight", 0.375F);
        value.addProperty("placementCreatesCell", true); value.addProperty("emptyHandRemovalProven", true);
        value.addProperty("selfItemAddsCellProven", true); value.addProperty("breakDropsEveryEnabledCell", true);
        value.addProperty("normalBlockDropDisabled", true); value.addProperty("persistenceProven", true);
        value.addProperty("dynamicCellShapeProven", true); value.addProperty("nonOpaqueProven", true);
        value.addProperty("legacyInsertionPredicateProven", true); value.addProperty("sourceProvenModContentInsertionWired", true);
        JsonArray ids = new JsonArray(); ids.add("fixture:cross"); ids.add("minecraft:cactus"); ids.add("minecraft:brown_mushroom");
        value.add("sourceProvenInsertionBlockIds", ids);
        value.addProperty("contentInsertionRuntimeComplete", false); value.addProperty("presentationRuntimeComplete", false);
        var rule = LegacyGridPotBlockRegistry.parseForTests(value);
        assertNotNull(rule);
        assertTrue(rule.insertionEligible(Identifier.parse("fixture:cross")));
        assertTrue(rule.insertionEligible(Identifier.parse("minecraft:cactus")));
        assertTrue(rule.insertionEligible(Identifier.parse("minecraft:brown_mushroom")));
        assertFalse(rule.insertionEligible(Identifier.parse("minecraft:oak_sapling")));
    }
}
