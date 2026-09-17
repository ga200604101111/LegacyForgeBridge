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
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityRuntimePassTest {
    @TempDir Path tempDir;

    @Test void promotesOnlyReadyCandidatesAndMaterializesRemoteSpawnIdentity() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        ConversionContext context = context(staging);
        Files.writeString(staging.resolve(LegacyPlainEntityRuntimeCandidatePass.OUTPUT), """
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "rules":[
                    {
                      "id":"foreign:orb",
                      "legacyRegistryName":"orb",
                      "sourceClass":"third/entity/Orb",
                      "legacyNumericId":23,
                      "generatedClass":"dev.yinghuang.legacyforgebridge.generated.foreign.entity.PlainEntity_orb_a",
                      "generatedInternalName":"dev/yinghuang/legacyforgebridge/generated/foreign/entity/PlainEntity_orb_a",
                      "trackingRange":80,
                      "updateFrequency":2,
                      "velocityUpdates":true,
                      "width":0.5,
                      "height":0.75,
                      "synchedDataAccessorCount":1,
                      "legacyWatcherBridgeWired":true,
                      "presentationAdapter":"NOOP_RENDERER",
                      "runtimeCandidateReady":true,
                      "rendererClass":"third/client/RenderEmpty"
                    },
                    {
                      "id":"foreign:blocked",
                      "sourceClass":"third/entity/Blocked",
                      "runtimeCandidateReady":false,
                      "blockers":["source-renderer-not-proven-noop"]
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        new LegacyPlainEntityRuntimePass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyPlainEntityRuntimePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("entityTypeRegistrationWired").getAsBoolean());
        assertTrue(root.get("clientRendererRegistrationWired").getAsBoolean());
        assertTrue(root.get("legacyWatcherBridgeWired").getAsBoolean());
        assertTrue(root.get("remoteEntitySpawnRuntimeWired").getAsBoolean());
        assertTrue(root.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, root.get("runtimeRuleCount").getAsInt());
        assertEquals(1, root.get("remoteEntitySpawnRuntimeCompleteRules").getAsInt());
        assertEquals(0, root.get("skippedRuntimeRuleCount").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:orb", rule.get("id").getAsString());
        assertEquals("foreign", rule.get("legacyModId").getAsString());
        assertEquals(23, rule.get("legacyModEntityTypeId").getAsInt());
        assertEquals(80, rule.get("legacyTrackingRangeBlocks").getAsInt());
        assertEquals(5, rule.get("modernClientTrackingRangeChunks").getAsInt());
        assertEquals("MISC", rule.get("mobCategory").getAsString());
        assertTrue(rule.get("legacyWatcherBridgeWired").getAsBoolean());
        assertTrue(rule.get("remoteEntitySpawnRuntimeComplete").getAsBoolean());
        assertTrue(rule.get("runtimeComplete").getAsBoolean());
    }

    @Test void trackingRangeConversionRoundsOutwardByChunk() {
        assertEquals(1, LegacyPlainEntityRuntimePass.blocksToTrackingChunks(1));
        assertEquals(1, LegacyPlainEntityRuntimePass.blocksToTrackingChunks(16));
        assertEquals(2, LegacyPlainEntityRuntimePass.blocksToTrackingChunks(17));
        assertEquals(5, LegacyPlainEntityRuntimePass.blocksToTrackingChunks(80));
        assertThrows(IllegalArgumentException.class, () -> LegacyPlainEntityRuntimePass.blocksToTrackingChunks(0));
    }

    private ConversionContext context(Path staging) throws Exception {
        Path source = tempDir.resolve("runtime.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(source))) { }
        LegacyModMetadata metadata = new LegacyModMetadata("runtime.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("runtime.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve("runtime-lfb.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }
}
