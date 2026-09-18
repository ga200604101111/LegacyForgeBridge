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

class LegacyVariantSnowballRetirementPassTest {
    @TempDir Path tempDir;

    @Test
    void deletesThreeClassCohortOnlyAfterFreshReferenceClosure() throws Exception {
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

        JsonObject readiness = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRetirementReadiness.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, readiness.get("retirementCohortCandidateReadyCount").getAsInt(),
                readiness.toString());

        new LegacyVariantSnowballRetirementPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRetirementPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("retirementAuthorizationWired").getAsBoolean());
        assertTrue(root.get("sourceClassDeletionWired").getAsBoolean());
        assertTrue(root.get("freshPreDeleteReferenceCheckWired").getAsBoolean());
        assertTrue(root.get("freshPostDeleteReferenceCheckWired").getAsBoolean());
        assertTrue(root.get("restoreOnPostDeleteFailureWired").getAsBoolean());
        assertEquals(1, root.get("retirementAuthorizedCohorts").getAsInt(), root.toString());
        assertEquals(3, root.get("deletedSourceClasses").getAsInt(), root.toString());
        assertEquals(0, root.get("blockedRetirementCohorts").getAsInt(), root.toString());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("retirementComplete").getAsBoolean(), rule.toString());
        assertTrue(rule.get("sourceClassDeletionAuthorized").getAsBoolean());
        assertEquals(3, rule.get("deletedSourceClassCount").getAsInt());
        assertFalse(rule.get("restoredAfterFailedRetirement").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty(), rule.toString());

        assertFalse(Files.exists(staging.resolve("foreign/item/VariantBall.class")));
        assertFalse(Files.exists(staging.resolve("foreign/entity/VariantProjectile.class")));
        assertFalse(Files.exists(staging.resolve("foreign/entity/VariantKind.class")));
        assertTrue(Files.isRegularFile(staging.resolve("foreign/Bootstrap.class")));
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
