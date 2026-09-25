package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballRuntimeFixture;
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

class LegacyVariantSnowballConstructionReplacementReadinessTest {
    @TempDir Path tempDir;

    @Test
    void generatedBehaviorConstructorProvesReplacementButDoesNotAuthorizeAllocationRemoval()
            throws Exception {
        Path source = VariantSnowballRuntimeFixture.write(tempDir.resolve("variant.jar"));
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);

        new CopyLegacyJarPass().apply(context);
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        new LegacyEntityDataWatcherPass().apply(context);
        new LegacyVariantSnowballRuntimePass().apply(context);
        new LegacyVariantSnowballRegistrationStripPass().apply(context);
        new LegacyVariantSnowballItemRegistrationStripPass().apply(context);
        new LegacyBehaviorPass().apply(context);

        LegacyVariantSnowballConstructionReplacementReadiness.materialize(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballConstructionReplacementReadiness.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertTrue(root.get("constructionReplacementAnalysisWired").getAsBoolean());
        assertTrue(root.get("generatedBehaviorConstructorReplacementRequired").getAsBoolean());
        assertTrue(root.get("behaviorBootstrapPresent").getAsBoolean());
        assertFalse(root.get("sourceAllocationStripWired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("evaluatedRuntimeRules").getAsInt());
        assertEquals(1, root.get("constructorReplacementProvenRules").getAsInt());
        assertEquals(0, root.get("blockedConstructorReplacementRules").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:variant_ball", rule.get("id").getAsString());
        assertEquals("foreign/item/VariantBall", rule.get("sourceItemClass").getAsString());
        assertEquals("()V", rule.get("sourceConstructor").getAsString());
        assertTrue(rule.get("generatedBehaviorClassPresent").getAsBoolean(), rule.toString());
        assertTrue(rule.get("itemRegistrationStripComplete").getAsBoolean(), rule.toString());
        assertTrue(rule.get("constructorReplacementProven").getAsBoolean(), rule.toString());
        assertTrue(rule.get("sourceAllocationRetirementRequired").getAsBoolean());
        assertFalse(rule.get("sourceAllocationStripWired").getAsBoolean());
        assertFalse(rule.get("sourceClassDeletionAuthorized").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty(), rule.toString());

        JsonObject behavior = JsonParser.parseString(Files.readString(
                staging.resolve("legacyforgebridge/behavior-analysis.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject item = behavior.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("foreign:variant_ball", item.get("id").getAsString());
        assertEquals("foreign/item/VariantBall", item.get("sourceClass").getAsString());
        JsonObject allocation = item.getAsJsonObject("allocation");
        assertEquals("foreign/item/VariantBall", allocation.get("itemClass").getAsString());
        assertEquals("()V", allocation.get("constructorDescriptor").getAsString());
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                source.getFileName().toString(),
                "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                source.getFileName().toString(),
                0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis,
                new DiagnosticCollector(), "generic-test");
    }
}
