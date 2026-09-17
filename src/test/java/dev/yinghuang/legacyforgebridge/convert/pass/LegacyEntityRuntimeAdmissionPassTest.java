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

    @Test void admitsPlainEntityWithEntityInitAndProvenNoOpNbtStubs() throws Exception {
        Path staging = tempDir.resolve("admit");
        ConversionContext context = context(staging, "admit.jar");
        writeRuntimePlan(staging, true, true);
        writeBehavior(staging, false, false);
        writeConstruction(staging, 0);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject root = readAdmission(staging);
        assertEquals(1, root.get("admittedRegistrations").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty());
    }

    @Test void nonNoopNbtRemainsBlocked() throws Exception {
        Path staging = tempDir.resolve("nbt-state");
        ConversionContext context = context(staging, "nbt-state.jar");
        writeRuntimePlan(staging, true, true);
        writeBehavior(staging, false, true);
        writeConstruction(staging, 0);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = readAdmission(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("unsupported-callback:WRITE_NBT")));
    }

    @Test void tickBehaviorAndUnknownConstructorEffectKeepEntityBlocked() throws Exception {
        Path staging = tempDir.resolve("blocked");
        ConversionContext context = context(staging, "blocked.jar");
        writeRuntimePlan(staging, true, true);
        writeBehavior(staging, true, false);
        writeConstruction(staging, 1);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = readAdmission(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("unsupported-callback:TICK")));
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("unmapped-constructor-effects")));
    }

    @Test void watcherClosureGapIsAnAdmissionBlocker() throws Exception {
        Path staging = tempDir.resolve("watcher-gap");
        ConversionContext context = context(staging, "watcher-gap.jar");
        writeRuntimePlan(staging, false, true);
        writeBehavior(staging, false, false);
        writeConstruction(staging, 0);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = readAdmission(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("source-wide-datawatcher-call-closure-incomplete")));
    }

    @Test void legacyVelocityUpdatesFalseStaysBlockedUntilModernFalseSemanticsAreProven() throws Exception {
        Path staging = tempDir.resolve("velocity-disabled");
        ConversionContext context = context(staging, "velocity-disabled.jar");
        writeRuntimePlan(staging, true, false);
        writeBehavior(staging, false, false);
        writeConstruction(staging, 0);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = readAdmission(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("legacy-velocity-updates-disabled")));
    }

    private JsonObject readAdmission(Path staging) throws Exception {
        return JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
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

    private static void writeRuntimePlan(Path staging, boolean closure, boolean velocityUpdates) throws Exception {
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
                      "velocityUpdates": %s,
                      "sourceWideDataWatcherCallClosureComplete": %s,
                      "synchedDataMappingComplete": true,
                      "synchedDataEntries": [
                        {"sourceIndex":12,"sourceKind":"byte","modernValueKind":"byte","serializer":"BYTE","adapter":"identity","defaultValue":0}
                      ]
                    }
                  ]
                }
                """.formatted(Boolean.toString(velocityUpdates), Boolean.toString(closure)), StandardCharsets.UTF_8);
    }

    private static void writeBehavior(Path staging, boolean tick, boolean statefulWriteNbt) throws Exception {
        String tickCallback = tick
                ? ",{\"kind\":\"TICK\",\"owner\":\"foreign/Orb\",\"method\":\"func_70071_h_\",\"descriptor\":\"()V\"}"
                : "";
        String tickMethod = tick
                ? ",{\"owner\":\"foreign/Orb\",\"method\":\"func_70071_h_\",\"descriptor\":\"()V\",\"callbackKind\":\"TICK\",\"trivialNoOp\":true}"
                : "";
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
                      "callbacks": [
                        {"kind":"ENTITY_INIT","owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V"},
                        {"kind":"READ_NBT","owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},
                        {"kind":"WRITE_NBT","owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"}%s
                      ],
                      "sourceMethods": [
                        {"owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V","callbackKind":"ENTITY_INIT","trivialNoOp":false},
                        {"owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"READ_NBT","trivialNoOp":true},
                        {"owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"WRITE_NBT","trivialNoOp":%s}%s
                      ]
                    }
                  ]
                }
                """.formatted(tickCallback, Boolean.toString(!statefulWriteNbt), tickMethod), StandardCharsets.UTF_8);
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
