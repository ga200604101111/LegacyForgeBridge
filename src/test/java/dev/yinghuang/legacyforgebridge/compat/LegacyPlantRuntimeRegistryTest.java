package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlantRuntimeRegistryTest {
    private static final Identifier FARMLAND = Identifier.parse("minecraft:farmland");
    private static final Identifier DIRT = Identifier.parse("minecraft:dirt");
    private static final Identifier GRASS = Identifier.parse("minecraft:grass_block");
    private static final Identifier SAND = Identifier.parse("minecraft:sand");
    private static final Identifier WHEAT_SEEDS = Identifier.parse("minecraft:wheat_seeds");
    private static final Identifier WHEAT = Identifier.parse("minecraft:wheat");
    private static final Identifier SUGAR_CANE = Identifier.parse("minecraft:sugar_cane");

    @AfterEach void clear() { LegacyPlantRuntimeRegistry.clearForTests(); }

    @Test void parserAcceptsOnlyHashAlignedFullyProvenRuntimeRules() {
        JsonObject runtime = JsonParser.parseString("""
                {"schemaVersion":3,"sourceSha256":"sha","proofs":[
                  {"modernId":"demo:crop","family":"crops","runtimeProofComplete":true,"unsupportedRemovalDropProofComplete":true},
                  {"modernId":"demo:reed","family":"reed","runtimeProofComplete":true,"unsupportedRemovalDropProofComplete":true},
                  {"modernId":"demo:pending","family":"bush","runtimeProofComplete":false,"unsupportedRemovalDropProofComplete":true},
                  {"modernId":"other:bush","family":"bush","runtimeProofComplete":true,"unsupportedRemovalDropProofComplete":true}
                ]}
                """).getAsJsonObject();
        JsonObject soil = JsonParser.parseString("""
                {"schemaVersion":2,"sourceSha256":"sha","proofs":[
                  {"modernId":"demo:crop","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":true,"forgeDefaultSurvivalBlocks":["minecraft:grass_block","minecraft:dirt","minecraft:farmland"],"adjacentWaterRequired":false,"convertedReedSelfStackingByVanillaIdentity":false},
                  {"modernId":"demo:reed","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":false,"forgeDefaultSurvivalBlocks":["minecraft:grass_block","minecraft:dirt","minecraft:sand"],"adjacentWaterRequired":true,"convertedReedSelfStackingByVanillaIdentity":false},
                  {"modernId":"other:bush","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":false,"forgeDefaultSurvivalBlocks":["minecraft:grass_block","minecraft:dirt","minecraft:farmland"],"adjacentWaterRequired":false,"convertedReedSelfStackingByVanillaIdentity":false}
                ]}
                """).getAsJsonObject();
        JsonObject drops = JsonParser.parseString("""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[
                  {"modernId":"demo:crop","unsupportedRemovalDropProofComplete":true,"unsupportedRemovalDrop":{"kind":"vanilla_crops_1_7_10","quantity":1,"legacyDamage":0,"immatureItemId":"minecraft:wheat_seeds","matureItemId":"minecraft:wheat","matureMetadata":7,"bonusSeedItemId":"minecraft:wheat_seeds","bonusSeedTrials":3,"bonusRandomBound":15,"fortune":0}},
                  {"modernId":"demo:reed","unsupportedRemovalDropProofComplete":true,"unsupportedRemovalDrop":{"kind":"fixed_item_1_7_10","quantity":1,"legacyDamage":0,"itemId":"minecraft:sugar_cane"}},
                  {"modernId":"other:bush","unsupportedRemovalDropProofComplete":true,"unsupportedRemovalDrop":{"kind":"self_block_1_7_10","quantity":1,"legacyDamage":0,"itemId":"other:bush"}}
                ]}
                """).getAsJsonObject();

        List<LegacyPlantRuntimeRegistry.Rule> rules = LegacyPlantRuntimeRegistry.parseRules("demo", runtime, soil, drops);
        assertEquals(2, rules.size());
        assertEquals(LegacyPlantRuntimeRegistry.Family.CROPS, rules.get(0).family());
        assertEquals(LegacyPlantRuntimeRegistry.Family.REED, rules.get(1).family());
        assertTrue(rules.get(0).cropFertilityComplete());
        assertTrue(rules.get(0).unsupportedDrop() instanceof LegacyPlantRuntimeRegistry.VanillaCropsDrop);
        assertTrue(rules.get(1).adjacentWaterRequired());
        assertFalse(rules.get(1).reedSelfStacking());
        assertTrue(rules.get(1).unsupportedDrop() instanceof LegacyPlantRuntimeRegistry.FixedItemDrop);

        soil.addProperty("sourceSha256", "other");
        assertTrue(LegacyPlantRuntimeRegistry.parseRules("demo", runtime, soil, drops).isEmpty());
    }

    @Test void parserRejectsTamperedLegacyCropDropConstants() {
        JsonObject runtime = JsonParser.parseString("""
                {"schemaVersion":3,"sourceSha256":"sha","proofs":[{"modernId":"demo:crop","family":"crops","runtimeProofComplete":true,"unsupportedRemovalDropProofComplete":true}]}
                """).getAsJsonObject();
        JsonObject soil = JsonParser.parseString("""
                {"schemaVersion":2,"sourceSha256":"sha","proofs":[{"modernId":"demo:crop","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":true,"forgeDefaultSurvivalBlocks":["minecraft:farmland"],"adjacentWaterRequired":false,"convertedReedSelfStackingByVanillaIdentity":false}]}
                """).getAsJsonObject();
        JsonObject drops = JsonParser.parseString("""
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"modernId":"demo:crop","unsupportedRemovalDropProofComplete":true,"unsupportedRemovalDrop":{"kind":"vanilla_crops_1_7_10","quantity":1,"legacyDamage":0,"immatureItemId":"minecraft:wheat_seeds","matureItemId":"minecraft:wheat","matureMetadata":7,"bonusSeedItemId":"minecraft:wheat_seeds","bonusSeedTrials":4,"bonusRandomBound":15,"fortune":0}}]}
                """).getAsJsonObject();
        assertTrue(LegacyPlantRuntimeRegistry.parseRules("demo", runtime, soil, drops).isEmpty());
    }

    @Test void forgeDefaultSurvivalAndFertilitySemanticsStayExact() {
        var crop = cropRule();
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(crop, GRASS, false, false));
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(crop, DIRT, false, false));
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(crop, FARMLAND, false, false));
        assertFalse(LegacyPlantRuntimeRegistry.canSurvive(crop, SAND, false, false));
        assertFalse(LegacyPlantRuntimeRegistry.isFertile(crop, FARMLAND, 0));
        assertTrue(LegacyPlantRuntimeRegistry.isFertile(crop, FARMLAND, 1));
        assertTrue(LegacyPlantRuntimeRegistry.isFertile(crop, FARMLAND, 7));
        assertFalse(LegacyPlantRuntimeRegistry.isFertile(crop, DIRT, 7));

        var reed = reedRule();
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(reed, SAND, true, false));
        assertFalse(LegacyPlantRuntimeRegistry.canSurvive(reed, SAND, false, false));
        assertFalse(LegacyPlantRuntimeRegistry.canSurvive(reed, Identifier.parse("demo:reed"), true, true));
    }

    @Test void cropGrowthRateMatchesForgePatched1710Formula() {
        var crop = cropRule();
        List<LegacyPlantRuntimeRegistry.SoilSample> wet = java.util.Collections.nCopies(9,
                new LegacyPlantRuntimeRegistry.SoilSample(FARMLAND, 7));
        assertEquals(10.0F, LegacyPlantRuntimeRegistry.cropGrowthRate(crop, wet, false, false, false));
        assertEquals(5.0F, LegacyPlantRuntimeRegistry.cropGrowthRate(crop, wet, false, false, true));
        assertEquals(5.0F, LegacyPlantRuntimeRegistry.cropGrowthRate(crop, wet, true, true, false));

        List<LegacyPlantRuntimeRegistry.SoilSample> dry = java.util.Collections.nCopies(9,
                new LegacyPlantRuntimeRegistry.SoilSample(FARMLAND, 0));
        assertEquals(4.0F, LegacyPlantRuntimeRegistry.cropGrowthRate(crop, dry, false, false, false));
    }

    @Test void reedMetadataTimerMatches1710Transition() {
        assertEquals(new LegacyPlantRuntimeRegistry.ReedTick(1, false),
                LegacyPlantRuntimeRegistry.reedRandomTick(0, 1, true));
        assertEquals(new LegacyPlantRuntimeRegistry.ReedTick(0, true),
                LegacyPlantRuntimeRegistry.reedRandomTick(15, 1, true));
        assertEquals(new LegacyPlantRuntimeRegistry.ReedTick(15, false),
                LegacyPlantRuntimeRegistry.reedRandomTick(15, 3, true));
        assertEquals(new LegacyPlantRuntimeRegistry.ReedTick(9, false),
                LegacyPlantRuntimeRegistry.reedRandomTick(9, 1, false));
    }

    @Test void cropBonemealMatches1710IgrowableIncludingOvershootMetadata() {
        var crop = cropRule();
        assertTrue(LegacyPlantRuntimeRegistry.isCropBonemealTarget(crop, 0));
        assertFalse(LegacyPlantRuntimeRegistry.isCropBonemealTarget(crop, 7));
        assertTrue(LegacyPlantRuntimeRegistry.isCropBonemealTarget(crop, 8));
        assertFalse(LegacyPlantRuntimeRegistry.isCropBonemealTarget(reedRule(), 0));

        assertEquals(2, LegacyPlantRuntimeRegistry.cropBonemealMetadata(crop, 0, bound -> {
            assertEquals(4, bound); return 0;
        }));
        assertEquals(7, LegacyPlantRuntimeRegistry.cropBonemealMetadata(crop, 4, bound -> 3));
        assertEquals(7, LegacyPlantRuntimeRegistry.cropBonemealMetadata(crop, 7, bound -> 0));
        assertEquals(7, LegacyPlantRuntimeRegistry.cropBonemealMetadata(crop, 8, bound -> 3));
        assertEquals(7, LegacyPlantRuntimeRegistry.cropBonemealMetadata(crop, 15, bound -> 3));
    }

    @Test void unsupportedRemovalDropsMatchInherited1710PlantBasesExactly() {
        var bush = new LegacyPlantRuntimeRegistry.Rule(Identifier.parse("demo:bush"),
                LegacyPlantRuntimeRegistry.Family.BUSH, Set.of(GRASS, DIRT, FARMLAND), false, false, false,
                new LegacyPlantRuntimeRegistry.SelfBlockDrop(1, 0));
        assertEquals(List.of(new LegacyPlantRuntimeRegistry.DropStack(Identifier.parse("demo:bush"), 1, 0)),
                LegacyPlantRuntimeRegistry.unsupportedRemovalDrops(bush, 0, bound -> 0));
        assertEquals(List.of(new LegacyPlantRuntimeRegistry.DropStack(SUGAR_CANE, 1, 0)),
                LegacyPlantRuntimeRegistry.unsupportedRemovalDrops(reedRule(), 12, bound -> 0));

        assertEquals(List.of(new LegacyPlantRuntimeRegistry.DropStack(WHEAT_SEEDS, 1, 0)),
                LegacyPlantRuntimeRegistry.unsupportedRemovalDrops(cropRule(), 6, bound -> {
                    throw new AssertionError("immature crop must not roll bonus seeds");
                }));

        AtomicInteger matureIndex = new AtomicInteger();
        int[] matureRolls = {7, 8, 14};
        assertEquals(List.of(
                        new LegacyPlantRuntimeRegistry.DropStack(WHEAT, 1, 0),
                        new LegacyPlantRuntimeRegistry.DropStack(WHEAT_SEEDS, 1, 0)),
                LegacyPlantRuntimeRegistry.unsupportedRemovalDrops(cropRule(), 7,
                        bound -> matureRolls[matureIndex.getAndIncrement()]));

        AtomicInteger overshootIndex = new AtomicInteger();
        int[] overshootRolls = {8, 9, 0};
        assertEquals(List.of(
                        new LegacyPlantRuntimeRegistry.DropStack(WHEAT_SEEDS, 1, 0),
                        new LegacyPlantRuntimeRegistry.DropStack(WHEAT_SEEDS, 1, 0),
                        new LegacyPlantRuntimeRegistry.DropStack(WHEAT_SEEDS, 1, 0)),
                LegacyPlantRuntimeRegistry.unsupportedRemovalDrops(cropRule(), 8,
                        bound -> overshootRolls[overshootIndex.getAndIncrement()]));
    }

    private static LegacyPlantRuntimeRegistry.Rule cropRule() {
        return new LegacyPlantRuntimeRegistry.Rule(Identifier.parse("demo:crop"),
                LegacyPlantRuntimeRegistry.Family.CROPS, Set.of(GRASS, DIRT, FARMLAND), false, true, false,
                new LegacyPlantRuntimeRegistry.VanillaCropsDrop(WHEAT_SEEDS, WHEAT, 7,
                        WHEAT_SEEDS, 3, 15, 1, 0));
    }

    private static LegacyPlantRuntimeRegistry.Rule reedRule() {
        return new LegacyPlantRuntimeRegistry.Rule(Identifier.parse("demo:reed"),
                LegacyPlantRuntimeRegistry.Family.REED, Set.of(GRASS, DIRT, SAND), true, false, false,
                new LegacyPlantRuntimeRegistry.FixedItemDrop(SUGAR_CANE, 1, 0));
    }
}
