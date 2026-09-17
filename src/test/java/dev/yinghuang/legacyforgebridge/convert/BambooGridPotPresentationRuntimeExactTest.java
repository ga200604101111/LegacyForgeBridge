package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyGridPotBlockPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyGridPotPresentationProofPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyGridPotPresentationRuntimePass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooGridPotPresentationRuntimeExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @TempDir Path tempDir;

    @Test
    void exactBambooPromotesProvenMultiPotPresentationIntoModernItemModelRuntime() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = LegacyModMetadata.read(source);
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                source.getFileName().toString(), 0, 0, true, true, 0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"),
                BAMBOO_SHA256, Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new GenericContentPass().apply(context);
        new LegacyGridPotPresentationProofPass().apply(context);
        new LegacyGridPotBlockPass().apply(context);
        new LegacyGridPotPresentationRuntimePass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyGridPotPresentationRuntimePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("MODERN_ITEM_MODEL_RENDER_STATE", root.get("adaptation").getAsString());
        assertTrue(root.get("storedContentPresentationRuntimeWired").getAsBoolean());
        JsonObject rule = root.getAsJsonArray("rules").asList().stream()
                .map(JsonElement -> JsonElement.getAsJsonObject())
                .filter(value -> "ruby/bamboo/block/BlockMultiPot".equals(value.get("sourceBlockClass").getAsString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Exact Bamboo MultiPot presentation runtime was not admitted: " + root));
        assertTrue(rule.get("storedContentPresentationProven").getAsBoolean());
        assertTrue(rule.get("storedContentPresentationRuntimeWired").getAsBoolean());
        assertFalse(rule.get("exactLegacyGeometry").getAsBoolean());
        assertEquals(9, root.get("runtimeRules").getAsInt() >= 1 ? 9 : 0); // explicit guard that at least one runtime rule exists
    }
}
