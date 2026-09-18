package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyClassDependencyAnalyzer;
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

class LegacyVariantSnowballRetirementReadinessTest {
    @TempDir Path tempDir;

    @Test
    void completeReplacementBecomesRetirementCandidateBeforeDeletionAuthorization()
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
        new LegacyVariantSnowballSourceAllocationStripPass().apply(context);

        LegacyClassDependencyAnalyzer.Analysis dependencies =
                new LegacyClassDependencyAnalyzer().analyze(source, staging);
        LegacyVariantSnowballRetirementReadiness.materialize(context, dependencies);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRetirementReadiness.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertTrue(root.get("retirementReadinessAnalysisWired").getAsBoolean());
        assertTrue(root.get("itemRegistrationRetirementRequired").getAsBoolean());
        assertFalse(root.get("retirementAuthorizationWired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("evaluatedRuntimeCohorts").getAsInt());
        assertEquals(1, root.get("retirementCohortCandidateReadyCount").getAsInt(),
                root.toString());
        assertEquals(0, root.get("retirementCohortBlockedCount").getAsInt(),
                root.toString());
        assertEquals(0, root.get("sourceClassDeletionAuthorizedCount").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("modernRuntimeReplacementComplete").getAsBoolean());
        assertTrue(rule.get("projectileRegistrationStripComplete").getAsBoolean());
        assertTrue(rule.get("itemRegistrationStripComplete").getAsBoolean());
        assertTrue(rule.get("constructorReplacementProven").getAsBoolean(), rule.toString());
        assertTrue(rule.get("sourceAllocationStripComplete").getAsBoolean(), rule.toString());
        assertTrue(rule.get("retirementCohortCandidateReady").getAsBoolean(), rule.toString());
        assertFalse(rule.get("sourceClassDeletionAuthorized").getAsBoolean());
        assertEquals(0, rule.get("deletedSourceClassCount").getAsInt());

        java.util.List<String> blockers = new java.util.ArrayList<>();
        for (var element : rule.getAsJsonArray("blockers")) {
            blockers.add(element.getAsString());
        }
        assertFalse(blockers.contains("item-registration-strip-incomplete"), blockers.toString());
        assertFalse(blockers.contains("item-constructor-replacement-incomplete"), blockers.toString());
        assertFalse(blockers.contains("item-source-allocation-strip-incomplete"), blockers.toString());
        assertTrue(blockers.isEmpty(), blockers.toString());

        assertTrue(Files.isRegularFile(staging.resolve("foreign/item/VariantBall.class")));
        assertTrue(Files.isRegularFile(staging.resolve("foreign/entity/VariantProjectile.class")));
        assertTrue(Files.isRegularFile(staging.resolve("foreign/entity/VariantKind.class")));
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
                source,
                staging,
                tempDir.resolve("candidate.jar"),
                "sha",
                Files.size(source),
                metadata,
                analysis,
                new DiagnosticCollector(),
                "generic-test");
    }
}
