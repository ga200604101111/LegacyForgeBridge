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

    @Test void farmlandSeedPlacementIsProvenWithoutClaimingForgeSurvivalCompleteness() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        write(staging, LegacyPlantRuntimeProofPass.OUTPUT, """
                {"sourceSha256":"sha","proofs":[
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","runtimeProofComplete":true},
                  {"legacyRegistryName":"reed","sourceClass":"p/Reed","family":"reed","modernId":"demo:reed","runtimeProofComplete":true},
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","runtimeProofComplete":true}
                ]}
                """);
        write(staging, LegacyItemBlockBindingPass.OUTPUT, """
                {"sourceSha256":"sha","bindings":[
                  {"id":"demo:seed","family":"seeds","targetBlock":{"modernId":"demo:crop"},"soilBlock":{"modernId":"minecraft:farmland"}},
                  {"id":"demo:reed_item","family":"reed","targetBlock":{"modernId":"demo:reed"}}
                ]}
                """);

        new LegacyPlantSoilProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantSoilProofPass.OUTPUT))).getAsJsonObject();
        assertTrue(root.get("sourceProofsAligned").getAsBoolean());
        assertEquals(3, root.get("classifiedBlocks").getAsInt());
        assertEquals(2, root.get("placementTargetProofCompleteBlocks").getAsInt());
        assertEquals(1, root.get("cropVanillaPlacementSoilCompleteBlocks").getAsInt());
        assertEquals(0, root.get("survivalSoilProofCompleteBlocks").getAsInt());

        JsonObject crop = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertTrue(crop.get("placementTargetProofComplete").getAsBoolean());
        assertEquals("minecraft:farmland", crop.get("placementSoilId").getAsString());
        assertTrue(crop.get("placementSoilIdentityComplete").getAsBoolean());
        assertTrue(crop.get("vanillaPlacementSoilSemanticsComplete").getAsBoolean());
        assertFalse(crop.get("forgeCanSustainPlantExtensibilityComplete").getAsBoolean());
        assertFalse(crop.get("survivalSoilProofComplete").getAsBoolean());
        assertFalse(crop.get("runtimeComplete").getAsBoolean());

        JsonObject reed = root.getAsJsonArray("proofs").get(1).getAsJsonObject();
        assertTrue(reed.get("placementTargetProofComplete").getAsBoolean());
        assertFalse(reed.get("placementSoilIdentityComplete").getAsBoolean());
        assertFalse(reed.get("survivalSoilProofComplete").getAsBoolean());

        JsonObject bush = root.getAsJsonArray("proofs").get(2).getAsJsonObject();
        assertFalse(bush.get("placementTargetProofComplete").getAsBoolean());
        assertFalse(bush.get("runtimeComplete").getAsBoolean());
    }

    @Test void ambiguousCropSoilsAndStaleSourcesFailClosed() throws Exception {
        Path staging = tempDir.resolve("stale");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        write(staging, LegacyPlantRuntimeProofPass.OUTPUT, """
                {"sourceSha256":"sha","proofs":[{"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","runtimeProofComplete":true}]}
                """);
        write(staging, LegacyItemBlockBindingPass.OUTPUT, """
                {"sourceSha256":"other","bindings":[
                  {"id":"demo:a","family":"seeds","targetBlock":{"modernId":"demo:crop"},"soilBlock":{"modernId":"minecraft:farmland"}},
                  {"id":"demo:b","family":"seed_food","targetBlock":{"modernId":"demo:crop"},"soilBlock":{"modernId":"demo:magic_soil"}}
                ]}
                """);

        new LegacyPlantSoilProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantSoilProofPass.OUTPUT))).getAsJsonObject();
        assertFalse(root.get("sourceProofsAligned").getAsBoolean());
        JsonObject crop = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertFalse(crop.get("placementTargetProofComplete").getAsBoolean());
        assertFalse(crop.get("placementSoilIdentityComplete").getAsBoolean());
        assertFalse(crop.get("vanillaPlacementSoilSemanticsComplete").getAsBoolean());
        assertTrue(crop.getAsJsonArray("reasons").toString().contains("source-proof-hash-mismatch"));
        assertTrue(crop.getAsJsonArray("reasons").toString().contains("crop-placement-soil-identity-incomplete-or-ambiguous"));
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
