package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballLaunchFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballRuntimeFixture;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVariantSnowballRuntimePassTest {
    @TempDir Path tempDir;

    @Test
    void sourceCompleteCandidateJoinsRegistrationAndOpensImpactWithoutItemLaunch() throws Exception {
        Path source = VariantSnowballRuntimeFixture.write(tempDir.resolve("variant.jar"));
        Path staging = tempDir.resolve("staging");
        JsonObject root = run(source, staging);

        assertEquals(2, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("runtimeRuleRegistryWired").getAsBoolean());
        assertTrue(root.get("preRegistrationRuleLoadWired").getAsBoolean());
        assertTrue(root.get("projectileEntityTypeRegistrationWired").getAsBoolean());
        assertTrue(root.get("projectileItemStackCarrierWired").getAsBoolean());
        assertTrue(root.get("legacyMetadataSyncWired").getAsBoolean());
        assertTrue(root.get("itemRuntimeWired").getAsBoolean());
        assertTrue(root.get("projectileRuntimeWired").getAsBoolean());
        assertTrue(root.get("projectileImpactRuntimeWired").getAsBoolean());
        assertTrue(root.get("rendererRuntimeWired").getAsBoolean());
        assertTrue(root.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, root.get("runtimeRuleCount").getAsInt());
        assertEquals(1, root.get("projectileEntityTypeRuleCount").getAsInt());
        assertEquals(0, root.get("skippedRuntimeRuleCount").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:variant_ball", rule.get("id").getAsString());
        assertEquals("foreign:variant_ball_projectile", rule.get("projectileId").getAsString());
        assertEquals("variant_projectile", rule.get("legacyProjectileRegistryName").getAsString());
        assertEquals(41, rule.get("legacyProjectileNumericId").getAsInt());
        assertEquals(64, rule.get("legacyTrackingRangeBlocks").getAsInt());
        assertEquals(4, rule.get("modernClientTrackingRangeChunks").getAsInt());
        assertEquals(10, rule.get("updateFrequency").getAsInt());
        assertTrue(rule.get("velocityUpdates").getAsBoolean());
        assertEquals(0.25F, rule.get("width").getAsFloat());
        assertEquals(0.25F, rule.get("height").getAsFloat());
        assertTrue(rule.get("inheritedVanillaSnowballDimensions").getAsBoolean());
        assertTrue(rule.get("legacyProjectileRegistrationProven").getAsBoolean());
        assertTrue(rule.get("runtimeRuleReady").getAsBoolean());
        assertTrue(rule.get("projectileEntityTypeRegistrationWired").getAsBoolean());
        assertTrue(rule.get("projectileItemStackCarrierWired").getAsBoolean());
        assertTrue(rule.get("legacyMetadataSyncWired").getAsBoolean());
        assertTrue(rule.get("itemRuntimeWired").getAsBoolean());
        assertTrue(rule.get("projectileRuntimeWired").getAsBoolean());
        assertTrue(rule.get("projectileImpactRuntimeWired").getAsBoolean());
        assertTrue(rule.get("rendererRuntimeWired").getAsBoolean());
        assertTrue(rule.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(2, rule.getAsJsonArray("variants").size());
    }

    @Test
    void unexpectedCandidateRuntimeClaimFailsClosed() throws Exception {
        Path source = VariantSnowballRuntimeFixture.write(tempDir.resolve("claimed.jar"));
        Path staging = tempDir.resolve("claimed-staging");
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);
        runInputs(context);

        Path candidates = staging.resolve(LegacyVariantSnowballRuntimeCandidatePass.OUTPUT);
        JsonObject root = JsonParser.parseString(Files.readString(candidates)).getAsJsonObject();
        root.addProperty("runtimeImplementationWired", true);
        Files.writeString(candidates, root.toString());

        new LegacyVariantSnowballRuntimePass().apply(context);
        JsonObject runtime = JsonParser.parseString(
                Files.readString(staging.resolve(LegacyVariantSnowballRuntimePass.OUTPUT))).getAsJsonObject();
        assertEquals(0, runtime.get("runtimeRuleCount").getAsInt());
        assertEquals(1, runtime.get("skippedRuntimeRuleCount").getAsInt());
        assertEquals("runtime-candidate-sidecar-schema-source-or-runtime-claim-invalid",
                runtime.getAsJsonArray("skipped").get(0).getAsJsonObject().get("reason").getAsString());
    }

    @Test
    void missingProjectileEntityRegistrationFailsClosed() throws Exception {
        Path source = VariantSnowballLaunchFixture.write(tempDir.resolve("missing-registration.jar"));
        Path staging = tempDir.resolve("missing-registration-staging");
        JsonObject root = run(source, staging);
        assertEquals(0, root.get("runtimeRuleCount").getAsInt());
        assertEquals(1, root.get("skippedRuntimeRuleCount").getAsInt());
        assertEquals("legacy-projectile-registration-sidecar-missing-or-stale",
                root.getAsJsonArray("skipped").get(0).getAsJsonObject().get("reason").getAsString());
    }

    @Test
    void trackingRangeUsesCeilingChunks() {
        assertEquals(1, LegacyVariantSnowballRuntimePass.blocksToTrackingChunks(1));
        assertEquals(1, LegacyVariantSnowballRuntimePass.blocksToTrackingChunks(16));
        assertEquals(2, LegacyVariantSnowballRuntimePass.blocksToTrackingChunks(17));
        assertEquals(4, LegacyVariantSnowballRuntimePass.blocksToTrackingChunks(64));
    }

    private JsonObject run(Path source, Path staging) throws Exception {
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);
        runInputs(context);
        new LegacyVariantSnowballRuntimePass().apply(context);
        return JsonParser.parseString(
                Files.readString(staging.resolve(LegacyVariantSnowballRuntimePass.OUTPUT))).getAsJsonObject();
    }

    private static void runInputs(ConversionContext context) throws Exception {
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        new LegacyEntityDataWatcherPass().apply(context);
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                source.getFileName().toString(),
                "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                source.getFileName().toString(), 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source, staging, tempDir.resolve("candidate-" + source.getFileName()),
                "sha", Files.size(source), metadata, jarAnalysis,
                new DiagnosticCollector(), "generic-test");
    }
}
