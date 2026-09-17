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

class LegacyPlainEntityRuntimeCandidatePassTest {
    @TempDir Path tempDir;

    @Test void admitsOnlyGeneratedPlainEntitiesWithProvenNoOpPresentation() throws Exception {
        Path staging = tempDir.resolve("staging");
        ConversionContext context = context(staging);
        writeGenerated(staging);
        writePresentation(staging);

        new LegacyPlainEntityRuntimeCandidatePass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyPlainEntityRuntimeCandidatePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("modernNoOpRendererAdapterAvailable").getAsBoolean());
        assertFalse(root.get("entityTypeRegistrationWired").getAsBoolean());
        assertFalse(root.get("clientRendererRegistrationWired").getAsBoolean());
        assertEquals(1, root.get("runtimeCandidateReadyCount").getAsInt());
        assertEquals(1, root.get("blockedRuntimeCandidateCount").getAsInt());

        JsonObject ready = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(ready.get("runtimeCandidateReady").getAsBoolean());
        assertEquals(LegacyPlainEntityRuntimeCandidatePass.PRESENTATION_ADAPTER_NOOP,
                ready.get("presentationAdapter").getAsString());
        assertEquals("third/client/RenderEmpty", ready.get("rendererClass").getAsString());
        assertTrue(ready.getAsJsonArray("blockers").isEmpty());

        JsonObject blocked = root.getAsJsonArray("rules").get(1).getAsJsonObject();
        assertFalse(blocked.get("runtimeCandidateReady").getAsBoolean());
        assertTrue(blocked.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("source-renderer-not-proven-noop")));
    }

    private ConversionContext context(Path staging) throws Exception {
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Path source = tempDir.resolve("candidate.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(source))) { }
        LegacyModMetadata metadata = new LegacyModMetadata("candidate.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("candidate.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve("candidate-lfb.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static void writeGenerated(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT), """
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "generatedClasses":[
                    {
                      "id":"foreign:orb",
                      "legacyRegistryName":"orb",
                      "sourceClass":"third/entity/Orb",
                      "generatedClass":"dev.yinghuang.legacyforgebridge.generated.foreign.entity.PlainEntity_orb_a",
                      "generatedInternalName":"dev/yinghuang/legacyforgebridge/generated/foreign/entity/PlainEntity_orb_a",
                      "classGenerated":true,
                      "synchedDataAccessorCount":1,
                      "legacyBaseHurtSemanticsMapped":true,
                      "trackingRange":80,
                      "updateFrequency":2,
                      "velocityUpdates":true,
                      "width":0.5,
                      "height":0.75
                    },
                    {
                      "id":"foreign:visible",
                      "legacyRegistryName":"visible",
                      "sourceClass":"third/entity/Visible",
                      "generatedClass":"dev.yinghuang.legacyforgebridge.generated.foreign.entity.PlainEntity_visible_b",
                      "generatedInternalName":"dev/yinghuang/legacyforgebridge/generated/foreign/entity/PlainEntity_visible_b",
                      "classGenerated":true,
                      "synchedDataAccessorCount":0,
                      "legacyBaseHurtSemanticsMapped":true,
                      "trackingRange":64,
                      "updateFrequency":3,
                      "velocityUpdates":true,
                      "width":1.0,
                      "height":1.0
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
    }

    private static void writePresentation(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityPresentationPass.OUTPUT), """
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "rules":[
                    {
                      "id":"foreign:orb",
                      "legacyRegistryName":"orb",
                      "sourceClass":"third/entity/Orb",
                      "sourceNoOpRendererProven":true,
                      "presentationBlockers":["modern-noop-renderer-runtime-not-materialized"],
                      "registrations":[
                        {"rendererClass":"third/client/RenderEmpty","renderOwner":"third/client/RenderEmpty","renderMethod":"doRender","renderDescriptor":"(Lthird/entity/Orb;DDDFF)V"}
                      ]
                    },
                    {
                      "id":"foreign:visible",
                      "legacyRegistryName":"visible",
                      "sourceClass":"third/entity/Visible",
                      "sourceNoOpRendererProven":false,
                      "presentationBlockers":["source-renderer-not-proven-noop"],
                      "registrations":[
                        {"rendererClass":"third/client/RenderVisible"}
                      ]
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
    }
}
