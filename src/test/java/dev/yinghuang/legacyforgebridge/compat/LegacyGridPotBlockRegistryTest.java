package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyGridPotBlockRegistryTest {
    @Test
    void parserAdmitsOnlyProofCompleteCoreAndKeepsInsertionPresentationClosed() {
        JsonObject value = valid();
        var rule = LegacyGridPotBlockRegistry.parseForTests(value);
        assertEquals("fixture:grid", rule.id().toString());
        assertEquals(9, rule.cells());
        assertEquals(3, rule.gridWidth());
        assertTrue(rule.legacyInsertionPredicateProven());
        assertFalse(rule.contentInsertionRuntimeComplete());
        assertFalse(rule.presentationRuntimeComplete());

        JsonObject missingShape = valid();
        missingShape.addProperty("dynamicCellShapeProven", false);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(missingShape));

        JsonObject prematureInsertion = valid();
        prematureInsertion.addProperty("contentInsertionRuntimeComplete", true);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(prematureInsertion));
    }

    @Test
    void ruleRejectsNonNineCellOrUnprovenCoreShapes() {
        var id = net.minecraft.resources.Identifier.parse("fixture:grid");
        assertThrows(IllegalArgumentException.class, () -> new LegacyGridPotBlockRegistry.Rule(
                id, 8, 3, 0.01F, 0.375F,
                true, true, true, true, true, true, true, true, true, false, false));
        assertThrows(IllegalArgumentException.class, () -> new LegacyGridPotBlockRegistry.Rule(
                id, 9, 3, 0.01F, 0.375F,
                true, true, true, true, true, true, false, true, true, false, false));
    }

    private static JsonObject valid() {
        JsonObject value = new JsonObject();
        value.addProperty("id", "fixture:grid");
        value.addProperty("cells", 9);
        value.addProperty("gridWidth", 3);
        value.addProperty("baseHeight", 0.01F);
        value.addProperty("cellHeight", 0.375F);
        value.addProperty("placementCreatesCell", true);
        value.addProperty("emptyHandRemovalProven", true);
        value.addProperty("selfItemAddsCellProven", true);
        value.addProperty("breakDropsEveryEnabledCell", true);
        value.addProperty("normalBlockDropDisabled", true);
        value.addProperty("persistenceProven", true);
        value.addProperty("dynamicCellShapeProven", true);
        value.addProperty("nonOpaqueProven", true);
        value.addProperty("legacyInsertionPredicateProven", true);
        value.addProperty("contentInsertionRuntimeComplete", false);
        value.addProperty("presentationRuntimeComplete", false);
        return value;
    }
}
