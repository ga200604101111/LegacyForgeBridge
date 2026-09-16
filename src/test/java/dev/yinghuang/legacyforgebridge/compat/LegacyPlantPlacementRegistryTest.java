package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlantPlacementRegistryTest {
    private static final Identifier FARMLAND = Identifier.parse("minecraft:farmland");
    private static final Identifier DIRT = Identifier.parse("minecraft:dirt");
    private static final Identifier CROP = Identifier.parse("demo:crop");
    private static final Identifier REED = Identifier.parse("demo:reed");

    @AfterEach void clear() { LegacyPlantPlacementRegistry.clearForTests(); }

    @Test void parserEnablesOnlyRuntimeCompletePlantFamiliesAndPreservesSeedFoodProperties() {
        JsonObject root = JsonParser.parseString("""
                {"schemaVersion":2,"sourceSha256":"sha","sourceProofsAligned":true,"rules":[
                  {"id":"demo:seed","family":"seeds","targetBlockId":"demo:crop","soilBlockId":"minecraft:farmland","topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":true,"placementProofComplete":true,"placementAdapter":"item_seeds_1_7_10","runtimeComplete":true},
                  {"id":"demo:seed_food","family":"seed_food","targetBlockId":"demo:crop","soilBlockId":"minecraft:farmland","nutrition":3,"saturationModifier":0.4,"topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":true,"placementProofComplete":true,"placementAdapter":"item_seed_food_1_7_10","runtimeComplete":true},
                  {"id":"demo:reed_item","family":"reed","targetBlockId":"demo:reed","topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":true,"placementProofComplete":true,"placementAdapter":"item_reed_1_7_10","runtimeComplete":true},
                  {"id":"other:seed","family":"seeds","targetBlockId":"demo:crop","soilBlockId":"minecraft:farmland","topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":true,"placementProofComplete":true,"placementAdapter":"item_seeds_1_7_10","runtimeComplete":true}
                ]}
                """).getAsJsonObject();

        List<LegacyPlantPlacementRegistry.Rule> rules = LegacyPlantPlacementRegistry.parseRules("demo", root, id -> {
            if (CROP.equals(id)) return cropRule();
            if (REED.equals(id)) return reedRule();
            return null;
        });
        assertEquals(3, rules.size());
        rules.forEach(LegacyPlantPlacementRegistry::registerForTests);
        assertTrue(LegacyPlantPlacementRegistry.hasSeedRuntimeRule(Identifier.parse("demo:seed")));
        assertTrue(LegacyPlantPlacementRegistry.hasSeedRuntimeRule(Identifier.parse("demo:seed_food")));
        assertFalse(LegacyPlantPlacementRegistry.hasSeedRuntimeRule(Identifier.parse("demo:reed_item")));
        assertTrue(LegacyPlantPlacementRegistry.hasReedRuntimeRule(Identifier.parse("demo:reed_item")));
        assertTrue(LegacyPlantPlacementRegistry.hasRuntimeRule(Identifier.parse("demo:reed_item")));
        assertTrue(LegacyPlantPlacementRegistry.cropTargetRuntimeReady(CROP));
        assertFalse(LegacyPlantPlacementRegistry.cropTargetRuntimeReady(REED));
        assertTrue(LegacyPlantPlacementRegistry.plantTargetRuntimeReady(CROP));
        assertTrue(LegacyPlantPlacementRegistry.plantTargetRuntimeReady(REED));
        var food = LegacyPlantPlacementRegistry.rule(Identifier.parse("demo:seed_food"));
        assertEquals(LegacyPlantPlacementRegistry.Adapter.SEED_FOOD, food.adapter());
        assertEquals(3, food.nutrition());
        assertEquals(0.4F, food.saturationModifier());
        assertNotNull(LegacyPlantPlacementRegistry.foodProperties(food));
    }

    @Test void parserRejectsSchemaTargetFamilyReedSoilAndIncompleteSeedFoodProperties() {
        JsonObject wrongTarget = JsonParser.parseString("""
                {"schemaVersion":2,"sourceProofsAligned":true,"rules":[
                  {"id":"demo:seed","family":"seeds","targetBlockId":"demo:reed","soilBlockId":"minecraft:farmland","topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":true,"placementProofComplete":true,"placementAdapter":"item_seeds_1_7_10","runtimeComplete":true}
                ]}
                """).getAsJsonObject();
        assertTrue(LegacyPlantPlacementRegistry.parseRules("demo", wrongTarget, id -> reedRule()).isEmpty());
        wrongTarget.addProperty("schemaVersion", 1);
        assertTrue(LegacyPlantPlacementRegistry.parseRules("demo", wrongTarget, id -> cropRule()).isEmpty());

        JsonObject missingFood = JsonParser.parseString("""
                {"schemaVersion":2,"sourceProofsAligned":true,"rules":[
                  {"id":"demo:seed_food","family":"seed_food","targetBlockId":"demo:crop","soilBlockId":"minecraft:farmland","topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":false,"placementProofComplete":true,"placementAdapter":"item_seed_food_1_7_10","runtimeComplete":true}
                ]}
                """).getAsJsonObject();
        assertTrue(LegacyPlantPlacementRegistry.parseRules("demo", missingFood, id -> cropRule()).isEmpty());

        JsonObject inventedReedSoil = JsonParser.parseString("""
                {"schemaVersion":2,"sourceProofsAligned":true,"rules":[
                  {"id":"demo:reed_item","family":"reed","targetBlockId":"demo:reed","soilBlockId":"minecraft:dirt","topologyProofComplete":true,"inheritedVanillaPlacement":true,"targetPlacementCallbacksInherited":true,"targetProofComplete":true,"soilPlacementProofComplete":true,"seedFoodPropertiesProofComplete":true,"placementProofComplete":true,"placementAdapter":"item_reed_1_7_10","runtimeComplete":true}
                ]}
                """).getAsJsonObject();
        assertTrue(LegacyPlantPlacementRegistry.parseRules("demo", inventedReedSoil, id -> reedRule()).isEmpty());
    }

    @Test void seedPreconditionsMatch1710ItemSeedsExactly() {
        var rule = new LegacyPlantPlacementRegistry.Rule(Identifier.parse("demo:seed"),
                LegacyPlantPlacementRegistry.Adapter.SEEDS, CROP, FARMLAND, null, null);
        assertTrue(LegacyPlantPlacementRegistry.canPlantSeed(rule, Direction.UP, FARMLAND, true, true, true));
        assertFalse(LegacyPlantPlacementRegistry.canPlantSeed(rule, Direction.NORTH, FARMLAND, true, true, true));
        assertFalse(LegacyPlantPlacementRegistry.canPlantSeed(rule, Direction.UP, DIRT, true, true, true));
        assertFalse(LegacyPlantPlacementRegistry.canPlantSeed(rule, Direction.UP, FARMLAND, false, true, true));
        assertFalse(LegacyPlantPlacementRegistry.canPlantSeed(rule, Direction.UP, FARMLAND, true, false, true));
        assertFalse(LegacyPlantPlacementRegistry.canPlantSeed(rule, Direction.UP, FARMLAND, true, true, false));
    }

    @Test void reedHitGeometryMatches1710ItemReedExactly() {
        BlockPos clicked = new BlockPos(10, 64, -3);
        assertEquals(clicked, LegacyPlantPlacementRegistry.reedPlacementPos(clicked, Direction.NORTH, true));
        assertEquals(clicked.north(), LegacyPlantPlacementRegistry.reedPlacementPos(clicked, Direction.NORTH, false));
        assertEquals(clicked.below(), LegacyPlantPlacementRegistry.reedPlacementPos(clicked, Direction.DOWN, false));
        assertEquals(Direction.UP, LegacyPlantPlacementRegistry.reedPlacementFace(Direction.NORTH, true));
        assertEquals(Direction.NORTH, LegacyPlantPlacementRegistry.reedPlacementFace(Direction.NORTH, false));
    }

    private static LegacyPlantRuntimeRegistry.Rule cropRule() {
        return new LegacyPlantRuntimeRegistry.Rule(CROP, LegacyPlantRuntimeRegistry.Family.CROPS,
                Set.of(FARMLAND, DIRT), false, true, false,
                new LegacyPlantRuntimeRegistry.VanillaCropsDrop(
                        Identifier.parse("minecraft:wheat_seeds"), Identifier.parse("minecraft:wheat"), 7,
                        Identifier.parse("minecraft:wheat_seeds"), 3, 15, 1, 0));
    }

    private static LegacyPlantRuntimeRegistry.Rule reedRule() {
        return new LegacyPlantRuntimeRegistry.Rule(REED, LegacyPlantRuntimeRegistry.Family.REED,
                Set.of(DIRT), true, false, false,
                new LegacyPlantRuntimeRegistry.FixedItemDrop(Identifier.parse("minecraft:sugar_cane"), 1, 0));
    }
}
