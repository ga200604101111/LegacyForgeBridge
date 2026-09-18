package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballLaunchFixture;
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
    void sourceCompleteCandidateBecomesPreRegistrationRuleWithoutGameplayRuntimeClaim() throws Exception {
        Path source = VariantSnowballLaunchFixture.write(tempDir.resolve("variant.jar"));
        Path staging = tempDir.resolve("staging");
        JsonObject root = run(source, staging);

        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("runtimeRuleRegistryWired").getAsBoolean());
        assertTrue(root.get("preRegistrationRuleLoadWired").getAsBoolean());
        assertFalse(root.get("itemRuntimeWired").getAsBoolean());
        assertFalse(root.get("projectileRuntimeWired").getAsBoolean());
        assertFalse(root.get("rendererRuntimeWired").getAsBoolean());
        assertFalse(root.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, root.get("runtimeRuleCount").getAsInt());
        assertEquals(0, root.get("skippedRuntimeRuleCount").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:variant_ball", rule.get("id").getAsString());
        assertEquals("foreign:variant_ball_projectile", rule.get("projectileId").getAsString());
        assertTrue(rule.get("runtimeRuleReady").getAsBoolean());
        assertTrue(rule.get("preRegistrationRuleLoadWired").getAsBoolean());
        assertFalse(rule.get("itemRuntimeWired").getAsBoolean());
        assertFalse(rule.get("projectileRuntimeWired").getAsBoolean());
        assertFalse(rule.get("rendererRuntimeWired").getAsBoolean());
        assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(2, rule.getAsJsonArray("variants").size());
    }

    @Test
    void unexpectedCandidateRuntimeClaimFailsClosed() throws Exception {
        Path source = VariantSnowballLaunchFixture.write(tempDir.resolve("claimed.jar"));
        Path staging = tempDir.resolve("claimed-staging");
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);
        runCandidatePipeline(context);

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

    private JsonObject run(Path source, Path staging) throws Exception {
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);
        runCandidatePipeline(context);
        new LegacyVariantSnowballRuntimePass().apply(context);
        return JsonParser.parseString(
                Files.readString(staging.resolve(LegacyVariantSnowballRuntimePass.OUTPUT))).getAsJsonObject();
    }

    private static void runCandidatePipeline(ConversionContext context) throws Exception {
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
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
