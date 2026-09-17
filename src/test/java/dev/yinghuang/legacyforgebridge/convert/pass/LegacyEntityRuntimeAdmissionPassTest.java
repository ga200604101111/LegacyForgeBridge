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

class LegacyEntityRuntimeAdmissionPassTest {
    @TempDir Path tempDir;

    @Test void admitsOnlyPlainEntityWithCompleteWatcherBehaviorAndConstructionEvidence() throws Exception {
        Path staging = tempDir.resolve("admit");
        ConversionContext context = context(staging, "admit.jar");
        writeRuntimePlan(staging, true);
        writeBehavior(staging, false);
        writeConstruction(staging, 0);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("evaluatedRegistrations").getAsInt());
        assertEquals(1, root.get("admittedRegistrations").getAsInt());
        assertEquals(0, root.get("blockedRegistrations").getAsInt());
        assertFalse(root.get("runtimeImplementationWired").getAsBoolean());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:orb", rule.get("id").getAsString());
        assertEquals(LegacyEntityRuntimeAdmissionPass.FAMILY_PLAIN_SYNCHED_DATA_ONLY, rule.get("family").getAsString());
        assertTrue(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty());
        assertEquals(0.5F, rule.get("width").getAsFloat());
        assertEquals(0.75F, rule.get("height").getAsFloat());
        assertEquals(1, rule.getAsJsonArray("synchedDataEntries").size());
    }

    @Test void tickBehaviorAndUnknownConstructorEffectKeepEntityBlocked() throws Exception {
        Path staging = tempDir.resolve("blocked");
        ConversionContext context = context(staging, "blocked.jar");
        writeRuntimePlan(staging, true);
        writeBehavior(staging, true);
        writeConstruction(staging, 1);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("unsupported-callback:TICK")));
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("unmapped-constructor-effects")));
    }

    @Test void watcherClosureGapIsAnAdmissionBlocker() throws Exception {
        Path staging = tempDir.resolve("watcher-gap");
        ConversionContext context = context(staging, "watcher-gap.jar");
        writeRuntimePlan(staging, false);
        writeBehavior(staging, false);
        writeConstruction(staging, 0);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("source-wide-datawatcher-call-closure-incomplete")));
    }

    private ConversionContext context(Path staging, String jarName) throws Exception {
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Path source = tempDir.resolve(jarName);
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(source))) { }
        LegacyModMetadata metadata = new LegacyModMetadata(jarName, "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(jarName, 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve(jarName + ".candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static void writeRuntimePlan(Path staging, boolean closure) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), """
                {
                  "schemaVersion": 1,
                  "sourceSha256": "sha",
                  "rules": [
                    {
                      "id": "foreign:orb",
                      "legacyRegistryName": "orb",
                      "sourceClass": "foreign/Orb",
                      "legacyNumericId": 17,
                      "trackingRange": 80,
                      "updateFrequency": 2,
                      "velocityUpdates": true,
                      "sourceWideDataWatcherCallClosureComplete": %s,
                      "synchedDataMappingComplete": true,
                      "synchedDataEntries": [
                        {"sourceIndex":12,"sourceKind":"byte","modernValueKind":"byte","serializer":"BYTE","adapter":"identity","defaultValue":0}
                      ]
                    }
                  ]
                }
                """.formatted(Boolean.toString(closure)), StandardCharsets.UTF_8);
    }

    private static void writeBehavior(Path staging, boolean tick) throws Exception {
        String callbacks = tick
                ? "[{\"kind\":\"ENTITY_INIT\",\"owner\":\"foreign/Orb\",\"method\":\"func_70088_a\",\"descriptor\":\"()V\"},{\"kind\":\"TICK\",\"owner\":\"foreign/Orb\",\"method\":\"func_70071_h_\",\"descriptor\":\"()V\"}]"
                : "[{\"kind\":\"ENTITY_INIT\",\"owner\":\"foreign/Orb\",\"method\":\"func_70088_a\",\"descriptor\":\"()V\"}]";
        String methods = tick
                ? "[{\"owner\":\"foreign/Orb\",\"method\":\"func_70088_a\",\"descriptor\":\"()V\",\"callbackKind\":\"ENTITY_INIT\"},{\"owner\":\"foreign/Orb\",\"method\":\"func_70071_h_\",\"descriptor\":\"()V\",\"callbackKind\":\"TICK\"}]"
                : "[{\"owner\":\"foreign/Orb\",\"method\":\"func_70088_a\",\"descriptor\":\"()V\",\"callbackKind\":\"ENTITY_INIT\"}]";
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT), """
                {
                  "schemaVersion": 1,
                  "sourceSha256": "sha",
                  "rules": [
                    {
                      "id": "foreign:orb",
                      "legacyRegistryName": "orb",
                      "sourceClass": "foreign/Orb",
                      "externalBaseClass": "net/minecraft/entity/Entity",
                      "sourceOwnedBehaviorInventoryComplete": true,
                      "unclassifiedSourceMethodCount": 0,
                      "callbacks": %s,
                      "sourceMethods": %s
                    }
                  ]
                }
                """.formatted(callbacks, methods), StandardCharsets.UTF_8);
    }

    private static void writeConstruction(Path staging, int unmappedEffects) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityConstructionPass.OUTPUT), """
                {
                  "schemaVersion": 1,
                  "sourceSha256": "sha",
                  "rules": [
                    {
                      "id": "foreign:orb",
                      "legacyRegistryName": "orb",
                      "sourceClass": "foreign/Orb",
                      "externalBaseClass": "net/minecraft/entity/Entity",
                      "worldConstructorPresent": true,
                      "constructorChainComplete": true,
                      "constructorControlFlowSimple": true,
                      "sourceSetSizeOverridePresent": false,
                      "sizeProofComplete": true,
                      "width": 0.5,
                      "height": 0.75,
                      "unmappedConstructorEffectCount": %d
                    }
                  ]
                }
                """.formatted(unmappedEffects), StandardCharsets.UTF_8);
    }
}
