package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlantSoilProofPassTest {
    @TempDir Path tempDir;

    @Test void defaultForgeSoilAndFertilityAreProvenSeparatelyFromPlacementAndRuntimeMaterialization() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        runtime(staging, "sha", """
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","runtimeProofComplete":true},
                  {"legacyRegistryName":"reed","sourceClass":"p/Reed","family":"reed","modernId":"demo:reed","runtimeProofComplete":true},
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","runtimeProofComplete":true}
                """);
        write(staging, LegacyItemBlockBindingPass.OUTPUT, """
                {"sourceSha256":"sha","bindings":[
                  {"id":"demo:seed","family":"seeds","targetBlock":{"modernId":"demo:crop"},"soilBlock":{"modernId":"minecraft:farmland"}},
                  {"id":"demo:reed_item","family":"reed","targetBlock":{"modernId":"demo:reed"}}
                ]}
                """);
        extensions(staging, "sha", 0, 0);

        new LegacyPlantSoilProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantSoilProofPass.OUTPUT))).getAsJsonObject();
        assertEquals(2, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("sourceProofsAligned").getAsBoolean());
        assertTrue(root.get("runtimeProofSourceAligned").getAsBoolean());
        assertTrue(root.get("placementBindingSourceAligned").getAsBoolean());
        assertTrue(root.get("soilExtensionSourceAligned").getAsBoolean());
        assertEquals(3, root.get("classifiedBlocks").getAsInt());
        assertEquals(2, root.get("placementTargetProofCompleteBlocks").getAsInt());
        assertEquals(1, root.get("cropVanillaPlacementSoilCompleteBlocks").getAsInt());
        assertEquals(3, root.get("survivalSoilProofCompleteBlocks").getAsInt());
        assertEquals(1, root.get("cropFertilityProofCompleteBlocks").getAsInt());
        assertEquals(0, root.get("runtimeCompleteBlocks").getAsInt());

        JsonObject crop = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertTrue(crop.get("placementTargetProofComplete").getAsBoolean());
        assertEquals("minecraft:farmland", crop.get("placementSoilId").getAsString());
        assertTrue(crop.get("vanillaPlacementSoilSemanticsComplete").getAsBoolean());
        assertTrue(crop.get("forgeCanSustainPlantExtensibilityComplete").getAsBoolean());
        assertTrue(crop.get("survivalSoilProofComplete").getAsBoolean());
        assertTrue(crop.get("cropFertilityProofComplete").getAsBoolean());
        assertEquals(3, crop.getAsJsonArray("forgeDefaultSurvivalBlocks").size());
        assertFalse(crop.get("adjacentWaterRequired").getAsBoolean());
        assertFalse(crop.get("runtimeComplete").getAsBoolean());

        JsonObject reed = root.getAsJsonArray("proofs").get(1).getAsJsonObject();
        assertTrue(reed.get("placementTargetProofComplete").getAsBoolean());
        assertTrue(reed.get("survivalSoilProofComplete").getAsBoolean());
        assertTrue(reed.get("adjacentWaterRequired").getAsBoolean());
        assertFalse(reed.get("convertedReedSelfStackingByVanillaIdentity").getAsBoolean());
        assertEquals("minecraft:sand", reed.getAsJsonArray("forgeDefaultSurvivalBlocks").get(2).getAsString());

        JsonObject bush = root.getAsJsonArray("proofs").get(2).getAsJsonObject();
        assertFalse(bush.get("placementRequired").getAsBoolean());
        assertFalse(bush.get("placementTargetProofComplete").getAsBoolean());
        assertTrue(bush.get("survivalSoilProofComplete").getAsBoolean());
        assertFalse(bush.get("runtimeComplete").getAsBoolean());
    }

    @Test void stalePlacementBindingDoesNotEraseIndependentSurvivalProof() throws Exception {
        Path staging = tempDir.resolve("stale");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        runtime(staging, "sha", """
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","runtimeProofComplete":true}
                """);
        write(staging, LegacyItemBlockBindingPass.OUTPUT, """
                {"sourceSha256":"other","bindings":[
                  {"id":"demo:a","family":"seeds","targetBlock":{"modernId":"demo:crop"},"soilBlock":{"modernId":"minecraft:farmland"}},
                  {"id":"demo:b","family":"seed_food","targetBlock":{"modernId":"demo:crop"},"soilBlock":{"modernId":"demo:magic_soil"}}
                ]}
                """);
        extensions(staging, "sha", 0, 0);

        new LegacyPlantSoilProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantSoilProofPass.OUTPUT))).getAsJsonObject();
        assertFalse(root.get("sourceProofsAligned").getAsBoolean());
        assertFalse(root.get("placementBindingSourceAligned").getAsBoolean());
        assertTrue(root.get("soilExtensionSourceAligned").getAsBoolean());
        JsonObject crop = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertFalse(crop.get("placementTargetProofComplete").getAsBoolean());
        assertFalse(crop.get("placementSoilIdentityComplete").getAsBoolean());
        assertFalse(crop.get("vanillaPlacementSoilSemanticsComplete").getAsBoolean());
        assertTrue(crop.get("survivalSoilProofComplete").getAsBoolean());
        assertTrue(crop.get("cropFertilityProofComplete").getAsBoolean());
        assertTrue(crop.getAsJsonArray("reasons").toString().contains("placement-binding-proof-hash-mismatch"));
        assertTrue(crop.getAsJsonArray("reasons").toString().contains("crop-placement-soil-identity-incomplete-or-ambiguous"));
    }

    @Test void sourceSoilHooksBlockDefaultSurvivalAndFertilityProof() throws Exception {
        Path staging = tempDir.resolve("custom");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        runtime(staging, "sha", """
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","runtimeProofComplete":true}
                """);
        write(staging, LegacyItemBlockBindingPass.OUTPUT,
                "{\"sourceSha256\":\"sha\",\"bindings\":[]}\n");
        extensions(staging, "sha", 1, 1);

        new LegacyPlantSoilProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantSoilProofPass.OUTPUT))).getAsJsonObject();
        assertEquals(0, root.get("survivalSoilProofCompleteBlocks").getAsInt());
        assertEquals(0, root.get("cropFertilityProofCompleteBlocks").getAsInt());
        JsonObject crop = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertFalse(crop.get("survivalSoilProofComplete").getAsBoolean());
        assertFalse(crop.get("cropFertilityProofComplete").getAsBoolean());
        assertTrue(crop.getAsJsonArray("reasons").toString().contains("source-can-sustain-plant-overrides-pending"));
        assertTrue(crop.getAsJsonArray("reasons").toString().contains("source-is-fertile-overrides-pending"));
    }

    private static void runtime(Path staging, String hash, String proofs) throws Exception {
        write(staging, LegacyPlantRuntimeProofPass.OUTPUT,
                "{\"sourceSha256\":\"" + hash + "\",\"proofs\":[" + proofs + "]}\n");
    }

    private static void extensions(Path staging, String hash, int sustain, int fertile) throws Exception {
        write(staging, LegacyPlantSoilExtensionPass.OUTPUT,
                "{\"sourceSha256\":\"" + hash + "\",\"sourceCanSustainPlantOverrides\":" + sustain
                        + ",\"sourceFertilityOverrides\":" + fertile + ",\"rules\":[]}\n");
    }

    private ConversionContext context(Path staging, String hash) throws Exception {
        Path source = tempDir.resolve("source-" + staging.getFileName() + ".jar");
        Files.write(source, new byte[]{1});
        LegacyModMetadata metadata = new LegacyModMetadata("plants.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("demo", "Demo", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "plants.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), hash,
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }

    private static void write(Path staging, String relative, String json) throws Exception {
        Path path = staging.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }
}
