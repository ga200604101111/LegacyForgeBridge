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

class LegacyPlantDropProofPassTest {
    @TempDir Path tempDir;

    @Test void inheritedPlantFamiliesReceiveExactUnsupportedRemovalDropModels() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyPlantLifecyclePass.OUTPUT), """
                {"sourceSha256":"sha","proofs":[
                  {"legacyRegistryName":"crop","sourceClass":"p/Crop","family":"crops","modernId":"demo:crop","modernIdentityComplete":true,"dropsInheritedVanilla":true},
                  {"legacyRegistryName":"reed","sourceClass":"p/Reed","family":"reed","modernId":"demo:reed","modernIdentityComplete":true,"dropsInheritedVanilla":true},
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","modernIdentityComplete":true,"dropsInheritedVanilla":true},
                  {"legacyRegistryName":"custom","sourceClass":"p/Custom","family":"crops","modernId":"demo:custom","modernIdentityComplete":true,"dropsInheritedVanilla":false}
                ]}
                """, StandardCharsets.UTF_8);

        new LegacyPlantDropProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantDropProofPass.OUTPUT))).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("sourceLifecycleAligned").getAsBoolean());
        assertEquals(4, root.get("classifiedBlocks").getAsInt());
        assertEquals(3, root.get("unsupportedRemovalDropProofCompleteBlocks").getAsInt());

        JsonObject crop = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        JsonObject cropDrop = crop.getAsJsonObject("unsupportedRemovalDrop");
        assertTrue(crop.get("unsupportedRemovalDropProofComplete").getAsBoolean());
        assertEquals("vanilla_crops_1_7_10", cropDrop.get("kind").getAsString());
        assertEquals("minecraft:wheat_seeds", cropDrop.get("immatureItemId").getAsString());
        assertEquals("minecraft:wheat", cropDrop.get("matureItemId").getAsString());
        assertEquals(7, cropDrop.get("matureMetadata").getAsInt());
        assertEquals(3, cropDrop.get("bonusSeedTrials").getAsInt());
        assertEquals(15, cropDrop.get("bonusRandomBound").getAsInt());
        assertEquals(0, cropDrop.get("fortune").getAsInt());

        JsonObject reedDrop = root.getAsJsonArray("rules").get(1).getAsJsonObject().getAsJsonObject("unsupportedRemovalDrop");
        assertEquals("fixed_item_1_7_10", reedDrop.get("kind").getAsString());
        assertEquals("minecraft:sugar_cane", reedDrop.get("itemId").getAsString());

        JsonObject bushDrop = root.getAsJsonArray("rules").get(2).getAsJsonObject().getAsJsonObject("unsupportedRemovalDrop");
        assertEquals("self_block_1_7_10", bushDrop.get("kind").getAsString());
        assertEquals("demo:bush", bushDrop.get("itemId").getAsString());

        JsonObject custom = root.getAsJsonArray("rules").get(3).getAsJsonObject();
        assertFalse(custom.get("unsupportedRemovalDropProofComplete").getAsBoolean());
        assertEquals("source-drop-hooks-present", custom.getAsJsonArray("reasons").get(0).getAsString());
    }

    @Test void staleLifecycleHashFailsClosed() throws Exception {
        Path staging = tempDir.resolve("stale");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyPlantLifecyclePass.OUTPUT), """
                {"sourceSha256":"other","proofs":[
                  {"legacyRegistryName":"bush","sourceClass":"p/Bush","family":"bush","modernId":"demo:bush","modernIdentityComplete":true,"dropsInheritedVanilla":true}
                ]}
                """, StandardCharsets.UTF_8);
        new LegacyPlantDropProofPass().apply(context(staging, "sha"));
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantDropProofPass.OUTPUT))).getAsJsonObject();
        assertFalse(root.get("sourceLifecycleAligned").getAsBoolean());
        assertEquals(0, root.get("unsupportedRemovalDropProofCompleteBlocks").getAsInt());
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
}
