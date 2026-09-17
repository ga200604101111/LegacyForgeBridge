package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotBlockRegistryTest {
    @Test
    void parserAdmitsProofCompleteCoreAndPositiveSourceProvenInsertionSubset() {
        JsonObject value = valid();
        var rule = LegacyGridPotBlockRegistry.parseForTests(value);
        assertNotNull(rule);
        assertEquals("fixture:grid", rule.id().toString());
        assertEquals(9, rule.cells());
        assertEquals(3, rule.gridWidth());
        assertTrue(rule.legacyInsertionPredicateProven());
        assertTrue(rule.sourceProvenModContentInsertionWired());
        assertTrue(rule.insertionEligible(Identifier.parse("fixture:flower")));
        assertFalse(rule.insertionEligible(Identifier.parse("fixture:stone")));
        assertFalse(rule.contentInsertionRuntimeComplete());
        assertFalse(rule.presentationRuntimeComplete());

        JsonObject missingShape = valid();
        missingShape.addProperty("dynamicCellShapeProven", false);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(missingShape));

        JsonObject prematureFullInsertion = valid();
        prematureFullInsertion.addProperty("contentInsertionRuntimeComplete", true);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(prematureFullInsertion));

        JsonObject identitiesWithoutWiring = valid();
        identitiesWithoutWiring.addProperty("sourceProvenModContentInsertionWired", false);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(identitiesWithoutWiring));
    }

    @Test
    void ruleRejectsNonNineCellOrUnprovenCoreShapes() {
        var id = Identifier.parse("fixture:grid");
        assertThrows(IllegalArgumentException.class, () -> new LegacyGridPotBlockRegistry.Rule(
                id, 8, 3, 0.01F, 0.375F,
                true, true, true, true, true, true, true, true, true,
                true, Set.of(Identifier.parse("fixture:flower")), false, false));
        assertThrows(IllegalArgumentException.class, () -> new LegacyGridPotBlockRegistry.Rule(
                id, 9, 3, 0.01F, 0.375F,
                true, true, true, true, true, true, false, true, true,
                true, Set.of(Identifier.parse("fixture:flower")), false, false));
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
        value.addProperty("sourceProvenModContentInsertionWired", true);
        JsonArray eligible = new JsonArray();
        eligible.add("fixture:flower");
        value.add("sourceProvenInsertionBlockIds", eligible);
        value.addProperty("contentInsertionRuntimeComplete", false);
        value.addProperty("presentationRuntimeComplete", false);
        return value;
    }
}
