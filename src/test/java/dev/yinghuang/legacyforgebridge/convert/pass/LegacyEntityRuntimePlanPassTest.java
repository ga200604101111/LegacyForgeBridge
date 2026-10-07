package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
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

class LegacyEntityRuntimePlanPassTest {
    @TempDir Path tempDir;

    @Test void mapsPrimitiveStringWatcherSchemaWithoutOpeningRuntimeAdmission() throws Exception {
        Path staging = tempDir.resolve("mapping-staging");
        ConversionContext context = context(staging, "mapping.jar");
        writeDefinitions(staging);
        writeAccesses(staging, false, true);
        writeGlobalClosure(staging, true);

        new LegacyEntityRuntimePlanPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("sourceWideDataWatcherCallClosureComplete").getAsBoolean());
        assertTrue(root.get("postInitWatcherMutationGateWired").getAsBoolean());
        assertFalse(root.get("runtimeAdmissionReady").getAsBoolean());
        assertFalse(root.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, root.get("mappedRegistrations").getAsInt());
        assertEquals(0, root.get("skippedRegistrations").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:orb", rule.get("id").getAsString());
        assertTrue(rule.get("synchedDataMappingComplete").getAsBoolean());
        assertTrue(rule.get("initialFmlWatcherEnvelopeComplete").getAsBoolean());
        assertTrue(rule.get("entityBaseWatchersHandledExternally").getAsBoolean());
        assertEquals(0, rule.get("platformWatcherEntryCount").getAsInt());
        assertEquals(5, rule.get("sourceWatcherEntryCount").getAsInt());
        assertEquals(5, rule.get("nonBaseWatcherBridgeEntryCount").getAsInt());
        assertTrue(rule.get("reachableExactDispatchHelperClosureComplete").getAsBoolean());
        assertFalse(rule.get("reachableHelperClosureComplete").getAsBoolean());
        assertTrue(rule.get("sourceWideDataWatcherCallClosureComplete").getAsBoolean());
        assertEquals(2, rule.get("sourceOwnedDataWatcherReadCount").getAsInt());
        assertEquals(1, rule.get("sourceOwnedDataWatcherWriteCount").getAsInt());
        assertFalse(rule.get("postInitSourceDataWatcherMutationFree").getAsBoolean());
        assertFalse(rule.get("runtimeAdmissionReady").getAsBoolean());
        assertFalse(rule.getAsJsonArray("runtimeBlockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("source-wide-datawatcher-call-closure-incomplete")));
        assertTrue(rule.getAsJsonArray("runtimeBlockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("post-init-datawatcher-writes-require-runtime-sync")));
        assertTrue(rule.getAsJsonArray("runtimeBlockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("entitytype-syncheddata-runtime-not-materialized")));
        assertEquals(5, rule.get("synchedDataEntryCount").getAsInt());

        JsonArray entries = rule.getAsJsonArray("synchedDataEntries");
        assertFalse(entries.get(0).getAsJsonObject().get("platformOwned").getAsBoolean());
        assertEquals("source", entries.get(0).getAsJsonObject().get("ownership").getAsString());
        assertMapping(entries, 10, "byte", "byte", "BYTE", "identity", 1, 1);
        assertMapping(entries, 11, "short", "int", "INT", "signed_short_widen", 1, 0);
        assertMapping(entries, 12, "int", "int", "INT", "identity", 0, 0);
        assertMapping(entries, 13, "float", "float", "FLOAT", "identity", 0, 0);
        assertMapping(entries, 14, "string", "string", "STRING", "identity", 0, 0);
    }

    @Test void zeroWriteAccessSurfaceIsMarkedPostInitMutationFree() throws Exception {
        Path staging = tempDir.resolve("write-free-staging");
        ConversionContext context = context(staging, "write-free.jar");
        writeDefinitions(staging);
        writeAccesses(staging, false, false);
        writeGlobalClosure(staging, true);

        new LegacyEntityRuntimePlanPass().apply(context);

        JsonObject rule = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals(2, rule.get("sourceOwnedDataWatcherReadCount").getAsInt());
        assertEquals(0, rule.get("sourceOwnedDataWatcherWriteCount").getAsInt());
        assertTrue(rule.get("postInitSourceDataWatcherMutationFree").getAsBoolean());
        assertFalse(rule.getAsJsonArray("runtimeBlockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("post-init-datawatcher-writes-require-runtime-sync")));
    }

    @Test void sourceWideClosureGapStaysAnExplicitRuntimeBlocker() throws Exception {
        Path staging = tempDir.resolve("closure-gap-staging");
        ConversionContext context = context(staging, "closure-gap.jar");
        writeDefinitions(staging);
        writeAccesses(staging, false, false);
        writeGlobalClosure(staging, false);

        new LegacyEntityRuntimePlanPass().apply(context);

        JsonObject rule = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("sourceWideDataWatcherCallClosureComplete").getAsBoolean());
        assertTrue(rule.get("postInitSourceDataWatcherMutationFree").getAsBoolean());
        assertTrue(rule.getAsJsonArray("runtimeBlockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("source-wide-datawatcher-call-closure-incomplete")));
        assertFalse(rule.get("runtimeAdmissionReady").getAsBoolean());
    }

    @Test void watcherEnvelopeCountMismatchFailsClosedBeforeRuntimePlanAdmission() throws Exception {
        Path staging = tempDir.resolve("envelope-count-mismatch");
        ConversionContext context = context(staging, "envelope-count-mismatch.jar");
        writeDefinitions(staging);
        JsonObject definitions = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityDataWatcherPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        definitions.getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("sourceWatcherEntryCount", 4);
        Files.writeString(staging.resolve(LegacyEntityDataWatcherPass.OUTPUT),
                definitions.toString(), StandardCharsets.UTF_8);
        writeAccesses(staging, false, false);
        writeGlobalClosure(staging, true);

        new LegacyEntityRuntimePlanPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(0, root.get("mappedRegistrations").getAsInt());
        assertEquals(1, root.get("skippedRegistrations").getAsInt());
        String reason = root.getAsJsonArray("skipped").get(0).getAsJsonObject().get("reason").getAsString();
        assertTrue(reason.contains("watcher-envelope counts"), reason);
    }

    @Test void accessKindMismatchFailsClosedBeforeRuntimePlanAdmission() throws Exception {
        Path staging = tempDir.resolve("mismatch-staging");
        ConversionContext context = context(staging, "mismatch.jar");
        writeDefinitions(staging);
        writeAccesses(staging, true, true);
        writeGlobalClosure(staging, true);

        new LegacyEntityRuntimePlanPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(0, root.get("mappedRegistrations").getAsInt());
        assertEquals(1, root.get("skippedRegistrations").getAsInt());
        String reason = root.getAsJsonArray("skipped").get(0).getAsJsonObject().get("reason").getAsString();
        assertTrue(reason.contains("Access type mismatch at DataWatcher index 10"), reason);
    }

    private ConversionContext context(Path staging, String jarName) throws Exception {
        Files.createDirectories(staging);
        Path source = tempDir.resolve(jarName);
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(source))) { }
        LegacyModMetadata metadata = new LegacyModMetadata(jarName, "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(jarName, 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve(jarName + ".candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static void writeDefinitions(Path staging) throws Exception {
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyEntityDataWatcherPass.OUTPUT), """
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
                      "sourceDataWatcherDefinitionComplete": true,
                      "entityBaseWatchersHandledExternally": true,
                      "initialFmlWatcherEnvelopeComplete": true,
                      "platformWatcherEntryCount": 0,
                      "sourceWatcherEntryCount": 5,
                      "nonBaseWatcherBridgeEntryCount": 5,
                      "dataWatcherEntries": [
                        {"index":10,"valueKind":"byte","defaultValue":0,"declaredBy":"foreign/Orb","platformOwned":false,"ownership":"source"},
                        {"index":11,"valueKind":"short","defaultValue":-2,"declaredBy":"foreign/Orb","platformOwned":false,"ownership":"source"},
                        {"index":12,"valueKind":"int","defaultValue":7,"declaredBy":"foreign/Orb","platformOwned":false,"ownership":"source"},
                        {"index":13,"valueKind":"float","defaultValue":1.5,"declaredBy":"foreign/Orb","platformOwned":false,"ownership":"source"},
                        {"index":14,"valueKind":"string","defaultValue":"idle","declaredBy":"foreign/Orb","platformOwned":false,"ownership":"source"}
                      ]
                    }
                  ],
                  "skipped": []
                }
                """, StandardCharsets.UTF_8);
    }

    private static void writeAccesses(Path staging, boolean mismatch, boolean includeWrite) throws Exception {
        String byteKind = mismatch ? "int" : "byte";
        String writeAccess = includeWrite
                ? ",\n                        {\"index\":10,\"operation\":\"write\",\"valueKind\":\"" + byteKind + "\",\"sourceOwner\":\"foreign/Orb\",\"sourceMethod\":\"setByte\",\"sourceDescriptor\":\"(B)V\"}"
                : "";
        Files.writeString(staging.resolve(LegacyEntityDataWatcherAccessPass.OUTPUT), """
                {
                  "schemaVersion": 1,
                  "sourceSha256": "sha",
                  "runtimeImplementationWired": false,
                  "rules": [
                    {
                      "id": "foreign:orb",
                      "legacyRegistryName": "orb",
                      "sourceClass": "foreign/Orb",
                      "sourceLineageAccessSurfaceComplete": true,
                      "reachableStaticHelperClosureComplete": true,
                      "reachableExactDispatchHelperClosureComplete": true,
                      "reachableHelperClosureComplete": false,
                      "runtimeImplementationWired": false,
                      "accesses": [
                        {"index":10,"operation":"read","valueKind":"%s","sourceOwner":"foreign/Orb","sourceMethod":"getByte","sourceDescriptor":"()B"}%s,
                        {"index":11,"operation":"read","valueKind":"short","sourceOwner":"foreign/Orb","sourceMethod":"getShort","sourceDescriptor":"()S"}
                      ]
                    }
                  ],
                  "skipped": []
                }
                """.formatted(byteKind, writeAccess), StandardCharsets.UTF_8);
    }

    private static void writeGlobalClosure(Path staging, boolean complete) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityDataWatcherGlobalClosurePass.OUTPUT), """
                {
                  "schemaVersion": 1,
                  "sourceSha256": "sha",
                  "sourceWideDataWatcherCallClosureComplete": %s,
                  "runtimeImplementationWired": false
                }
                """.formatted(Boolean.toString(complete)), StandardCharsets.UTF_8);
    }

    private static void assertMapping(JsonArray entries, int index, String sourceKind, String modernKind,
                                      String serializer, String adapter, int reads, int writes) {
        JsonObject entry = entries.asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.get("sourceIndex").getAsInt() == index).findFirst().orElseThrow();
        assertEquals(sourceKind, entry.get("sourceKind").getAsString());
        assertEquals(modernKind, entry.get("modernValueKind").getAsString());
        assertEquals(serializer, entry.get("serializer").getAsString());
        assertEquals(adapter, entry.get("adapter").getAsString());
        assertEquals(reads, entry.get("readCount").getAsInt());
        assertEquals(writes, entry.get("writeCount").getAsInt());
    }
}
