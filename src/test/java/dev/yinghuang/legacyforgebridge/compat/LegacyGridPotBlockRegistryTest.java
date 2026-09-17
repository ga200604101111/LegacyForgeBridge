package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry.InsertionRoute.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotBlockRegistryTest {
    @Test
    void parserAdmitsPositiveAndSourceProvenNegativeSubsetsWithoutClaimingFullClosure() {
        JsonObject value=valid();var rule=LegacyGridPotBlockRegistry.parseForTests(value);assertNotNull(rule);
        Identifier flower=Identifier.parse("fixture:flower"),stone=Identifier.parse("fixture:stone"),unknown=Identifier.parse("fixture:unknown");
        assertEquals("fixture:grid",rule.id().toString());assertTrue(rule.insertionEligible(flower));
        assertTrue(rule.negativeInsertionEligible(stone));
        assertTrue(rule.negativeNonBlockItemRuntimeWired());
        assertFalse(rule.insertionEligible(unknown));
        assertFalse(rule.negativeInsertionEligible(unknown));
        assertEquals(POSITIVE,rule.insertionRoute(flower,true));
        assertEquals(NEGATIVE,rule.insertionRoute(stone,true));
        assertEquals(FAIL_CLOSED,rule.insertionRoute(unknown,true));
        assertEquals(FAIL_CLOSED,rule.insertionRoute(null,true));
        assertEquals(NEGATIVE,rule.insertionRoute(null,false));
        assertFalse(rule.contentInsertionRuntimeComplete());assertFalse(rule.presentationRuntimeComplete());
    }

    @Test
    void parserKeepsLegacyPositiveOnlySidecarCompatible() {
        JsonObject value=valid();
        value.remove("sourceNegativeInsertionBranchProven");value.remove("negativeContentInsertionRuntimeWired");
        value.remove("negativeNonBlockItemRuntimeWired");value.remove("sourceProvenNegativeBlockIds");
        var rule=LegacyGridPotBlockRegistry.parseForTests(value);assertNotNull(rule);assertFalse(rule.negativeNonBlockItemRuntimeWired());
        assertEquals(POSITIVE,rule.insertionRoute(Identifier.parse("fixture:flower"),true));
        assertEquals(FAIL_CLOSED,rule.insertionRoute(Identifier.parse("fixture:unknown"),true));
        assertEquals(FAIL_CLOSED,rule.insertionRoute(null,false));
    }

    @Test
    void negativeRuntimeRequiresSourceProofAndDisjointIdentities() {
        JsonObject missingProof=valid();missingProof.addProperty("sourceNegativeInsertionBranchProven",false);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(missingProof));

        JsonObject overlap=valid();JsonArray negative=new JsonArray();negative.add("fixture:flower");overlap.add("sourceProvenNegativeBlockIds",negative);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(overlap));

        JsonObject missingShape=valid();missingShape.addProperty("dynamicCellShapeProven",false);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(missingShape));

        JsonObject prematureFull=valid();prematureFull.addProperty("contentInsertionRuntimeComplete",true);
        assertNull(LegacyGridPotBlockRegistry.parseForTests(prematureFull));
    }

    @Test
    void ruleRejectsNonNineCellOrUnprovenCoreShapes() {
        var id=Identifier.parse("fixture:grid");
        assertThrows(IllegalArgumentException.class,()->new LegacyGridPotBlockRegistry.Rule(
                id,8,3,0.01F,0.375F,true,true,true,true,true,true,true,true,true,
                true,Set.of(Identifier.parse("fixture:flower")),true,true,true,Set.of(Identifier.parse("fixture:stone")),false,false));
        assertThrows(IllegalArgumentException.class,()->new LegacyGridPotBlockRegistry.Rule(
                id,9,3,0.01F,0.375F,true,true,true,true,true,true,false,true,true,
                true,Set.of(Identifier.parse("fixture:flower")),true,true,true,Set.of(Identifier.parse("fixture:stone")),false,false));
    }

    private static JsonObject valid(){
        JsonObject value=new JsonObject();value.addProperty("id","fixture:grid");value.addProperty("cells",9);value.addProperty("gridWidth",3);
        value.addProperty("baseHeight",0.01F);value.addProperty("cellHeight",0.375F);value.addProperty("placementCreatesCell",true);
        value.addProperty("emptyHandRemovalProven",true);value.addProperty("selfItemAddsCellProven",true);value.addProperty("breakDropsEveryEnabledCell",true);
        value.addProperty("normalBlockDropDisabled",true);value.addProperty("persistenceProven",true);value.addProperty("dynamicCellShapeProven",true);
        value.addProperty("nonOpaqueProven",true);value.addProperty("legacyInsertionPredicateProven",true);value.addProperty("sourceProvenModContentInsertionWired",true);
        JsonArray positive=new JsonArray();positive.add("fixture:flower");value.add("sourceProvenInsertionBlockIds",positive);
        value.addProperty("sourceNegativeInsertionBranchProven",true);value.addProperty("negativeContentInsertionRuntimeWired",true);value.addProperty("negativeNonBlockItemRuntimeWired",true);
        JsonArray negative=new JsonArray();negative.add("fixture:stone");value.add("sourceProvenNegativeBlockIds",negative);
        value.addProperty("contentInsertionRuntimeComplete",false);value.addProperty("presentationRuntimeComplete",false);return value;
    }
}
