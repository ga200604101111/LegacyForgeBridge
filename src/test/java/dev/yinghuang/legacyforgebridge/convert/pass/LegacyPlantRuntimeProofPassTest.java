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

class LegacyPlantRuntimeProofPassTest {
    @TempDir Path tempDir;

    @Test void onlyCompleteConsistentPlantProofsBecomeRuntimeEligible() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        write(staging, LegacyPlantBlockPass.OUTPUT, """
                {"sourceSha256":"sha","rules":[
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","modernIdentityComplete":true},
                  {"legacyRegistryName":"reed","sourceClass":"p/Reed","family":"reed","modernId":"demo:reed","modernIdentityComplete":true},
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","modernIdentityComplete":true},
                  {"legacyRegistryName":"custom","sourceClass":"p/Custom","family":"crops","modernId":"demo:custom","modernIdentityComplete":true}
                ]}
                """);
        write(staging, LegacyPlantLifecyclePass.OUTPUT, """
                {"sourceSha256":"sha","proofs":[
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","modernIdentityComplete":true,"survivalInheritedVanilla":true,"growthInheritedVanilla":true,"bonemealInheritedVanilla":true,"dropsInheritedVanilla":true,"ageModel":"legacy_meta_0_7","survivalModel":"vanilla_crops_farmland"},
                  {"legacyRegistryName":"reed","sourceClass":"p/Reed","family":"reed","modernId":"demo:reed","modernIdentityComplete":true,"survivalInheritedVanilla":true,"growthInheritedVanilla":true,"bonemealInheritedVanilla":true,"dropsInheritedVanilla":true,"ageModel":"legacy_meta_timer_0_15","survivalModel":"vanilla_reed"},
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","modernIdentityComplete":true,"survivalInheritedVanilla":true,"growthInheritedVanilla":true,"bonemealInheritedVanilla":true,"dropsInheritedVanilla":true,"ageModel":"none","survivalModel":"vanilla_bush"},
                  {"legacyRegistryName":"custom","sourceClass":"p/Custom","family":"crops","modernId":"demo:custom","modernIdentityComplete":true,"survivalInheritedVanilla":true,"growthInheritedVanilla":true,"bonemealInheritedVanilla":true,"dropsInheritedVanilla":false,"ageModel":"legacy_meta_0_7","survivalModel":"vanilla_crops_farmland"}
                ]}
                """);
        write(staging, LegacyPlantPresentationPass.OUTPUT, """
                {"sourceSha256":"sha","rules":[
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","modernIdentityComplete":true,"presentationComplete":true,"cutoutRuntimeComplete":true},
                  {"legacyRegistryName":"reed","sourceClass":"p/Reed","family":"reed","modernId":"demo:reed","modernIdentityComplete":true,"presentationComplete":true,"cutoutRuntimeComplete":true},
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","modernIdentityComplete":true,"presentationComplete":true,"cutoutRuntimeComplete":true},
                  {"legacyRegistryName":"custom","sourceClass":"p/Custom","family":"crops","modernId":"demo:custom","modernIdentityComplete":true,"presentationComplete":true,"cutoutRuntimeComplete":true}
                ]}
                """);

        ConversionContext context = context(staging, "sha");
        new LegacyPlantRuntimeProofPass().apply(context);
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantRuntimeProofPass.OUTPUT))).getAsJsonObject();
        assertTrue(root.get("sourceProofsAligned").getAsBoolean());
        assertEquals(4, root.get("classifiedBlocks").getAsInt());
        assertEquals(4, root.get("modernIdentityCompleteBlocks").getAsInt());
        assertEquals(3, root.get("lifecycleProofCompleteBlocks").getAsInt());
        assertEquals(4, root.get("presentationProofCompleteBlocks").getAsInt());
        assertEquals(3, root.get("runtimeProofCompleteBlocks").getAsInt());
        assertEquals(0, root.get("runtimeCompleteBlocks").getAsInt());

        JsonObject crop = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertTrue(crop.get("runtimeProofComplete").getAsBoolean());
        assertEquals("legacy_crops_1_7_10", crop.get("runtimeAdapter").getAsString());
        assertFalse(crop.get("runtimeComplete").getAsBoolean());

        JsonObject custom = root.getAsJsonArray("proofs").get(3).getAsJsonObject();
        assertFalse(custom.get("runtimeProofComplete").getAsBoolean());
        assertEquals("lifecycle-proof-incomplete", custom.getAsJsonArray("reasons").get(0).getAsString());
    }

    @Test void staleOrInconsistentProofsFailClosed() throws Exception {
        Path staging = tempDir.resolve("stale");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        write(staging, LegacyPlantBlockPass.OUTPUT, """
                {"sourceSha256":"sha","rules":[{"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","modernIdentityComplete":true}]}
                """);
        write(staging, LegacyPlantLifecyclePass.OUTPUT, """
                {"sourceSha256":"other","proofs":[{"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","modernIdentityComplete":true,"survivalInheritedVanilla":true,"growthInheritedVanilla":true,"bonemealInheritedVanilla":true,"dropsInheritedVanilla":true,"ageModel":"legacy_meta_0_7","survivalModel":"vanilla_crops_farmland"}]}
                """);
        write(staging, LegacyPlantPresentationPass.OUTPUT, """
                {"sourceSha256":"sha","rules":[{"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"reed","modernId":"demo:other","modernIdentityComplete":true,"presentationComplete":true,"cutoutRuntimeComplete":true}]}
                """);

        new LegacyPlantRuntimeProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantRuntimeProofPass.OUTPUT))).getAsJsonObject();
        assertFalse(root.get("sourceProofsAligned").getAsBoolean());
        assertEquals(0, root.get("runtimeProofCompleteBlocks").getAsInt());
        JsonObject proof = root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertFalse(proof.get("familyProofComplete").getAsBoolean());
        assertFalse(proof.get("modernIdentityComplete").getAsBoolean());
        assertFalse(proof.get("runtimeProofComplete").getAsBoolean());
        assertTrue(proof.getAsJsonArray("reasons").size() >= 3);
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
