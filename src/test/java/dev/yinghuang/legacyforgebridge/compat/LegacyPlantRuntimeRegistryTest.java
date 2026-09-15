package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlantRuntimeRegistryTest {
    private static final Identifier FARMLAND = Identifier.parse("minecraft:farmland");
    private static final Identifier DIRT = Identifier.parse("minecraft:dirt");
    private static final Identifier GRASS = Identifier.parse("minecraft:grass_block");
    private static final Identifier SAND = Identifier.parse("minecraft:sand");

    @AfterEach void clear() { LegacyPlantRuntimeRegistry.clearForTests(); }

    @Test void parserAcceptsOnlyHashAlignedFullyProvenRuntimeRules() {
        JsonObject runtime = JsonParser.parseString("""
                {"sourceSha256":"sha","proofs":[
                  {"modernId":"demo:crop","family":"crops","runtimeProofComplete":true},
                  {"modernId":"demo:reed","family":"reed","runtimeProofComplete":true},
                  {"modernId":"demo:pending","family":"bush","runtimeProofComplete":false},
                  {"modernId":"other:bush","family":"bush","runtimeProofComplete":true}
                ]}
                """).getAsJsonObject();
        JsonObject soil = JsonParser.parseString("""
                {"sourceSha256":"sha","proofs":[
                  {"modernId":"demo:crop","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":true,"forgeDefaultSurvivalBlocks":["minecraft:grass_block","minecraft:dirt","minecraft:farmland"],"adjacentWaterRequired":false,"convertedReedSelfStackingByVanillaIdentity":false},
                  {"modernId":"demo:reed","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":false,"forgeDefaultSurvivalBlocks":["minecraft:grass_block","minecraft:dirt","minecraft:sand"],"adjacentWaterRequired":true,"convertedReedSelfStackingByVanillaIdentity":false},
                  {"modernId":"other:bush","plantRuntimeProofComplete":true,"survivalSoilProofComplete":true,"cropFertilityProofComplete":false,"forgeDefaultSurvivalBlocks":["minecraft:grass_block","minecraft:dirt","minecraft:farmland"],"adjacentWaterRequired":false,"convertedReedSelfStackingByVanillaIdentity":false}
                ]}
                """).getAsJsonObject();

        List<LegacyPlantRuntimeRegistry.Rule> rules = LegacyPlantRuntimeRegistry.parseRules("demo", runtime, soil);
        assertEquals(2, rules.size());
        assertEquals(LegacyPlantRuntimeRegistry.Family.CROPS, rules.get(0).family());
        assertEquals(LegacyPlantRuntimeRegistry.Family.REED, rules.get(1).family());
        assertTrue(rules.get(0).cropFertilityComplete());
        assertTrue(rules.get(1).adjacentWaterRequired());
        assertFalse(rules.get(1).reedSelfStacking());

        soil.addProperty("sourceSha256", "other");
        assertTrue(LegacyPlantRuntimeRegistry.parseRules("demo", runtime, soil).isEmpty());
    }

    @Test void forgeDefaultSurvivalAndFertilitySemanticsStayExact() {
        var crop = new LegacyPlantRuntimeRegistry.Rule(Identifier.parse("demo:crop"),
                LegacyPlantRuntimeRegistry.Family.CROPS, Set.of(GRASS, DIRT, FARMLAND), false, true, false);
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(crop, GRASS, false, false));
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(crop, DIRT, false, false));
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(crop, FARMLAND, false, false));
        assertFalse(LegacyPlantRuntimeRegistry.canSurvive(crop, SAND, false, false));
        assertFalse(LegacyPlantRuntimeRegistry.isFertile(crop, FARMLAND, 0));
        assertTrue(LegacyPlantRuntimeRegistry.isFertile(crop, FARMLAND, 1));
        assertTrue(LegacyPlantRuntimeRegistry.isFertile(crop, FARMLAND, 7));
        assertFalse(LegacyPlantRuntimeRegistry.isFertile(crop, DIRT, 7));

        var reed = new LegacyPlantRuntimeRegistry.Rule(Identifier.parse("demo:reed"),
                LegacyPlantRuntimeRegistry.Family.REED, Set.of(GRASS, DIRT, SAND), true, false, false);
        assertTrue(LegacyPlantRuntimeRegistry.canSurvive(reed, SAND, true, false));
        assertFalse(LegacyPlantRuntimeRegistry.canSurvive(reed, SAND, false, false));
        assertFalse(LegacyPlantRuntimeRegistry.canSurvive(reed, Identifier.parse("demo:reed"), true, true));
    }

    @Test void cropGrowthRateMatchesForgePatched1710Formula() {
        var crop = new LegacyPlantRuntimeRegistry.Rule(Identifier.parse("demo:crop"),
                LegacyPlantRuntimeRegistry.Family.CROPS, Set.of(GRASS, DIRT, FARMLAND), false, true, false);
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
}
