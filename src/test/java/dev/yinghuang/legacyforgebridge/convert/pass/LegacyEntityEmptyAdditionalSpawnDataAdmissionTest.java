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

class LegacyEntityEmptyAdditionalSpawnDataAdmissionTest {
    @TempDir Path tempDir;

    @Test void pairedTrivialReadWriteSpawnDataIsAdmittedAsEmptyPayload() throws Exception {
        Path staging = tempDir.resolve("paired");
        ConversionContext context = context(staging, "paired.jar");
        writeCommon(staging);
        writeBehavior(staging, true, true, true);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject root = read(staging);
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(root.get("emptyAdditionalSpawnDataAdmissionWired").getAsBoolean());
        assertEquals(1, root.get("admittedEmptyAdditionalSpawnDataPairs").getAsInt());
        assertTrue(rule.get("admitted").getAsBoolean());
        assertTrue(rule.get("emptyAdditionalSpawnDataPairProven").getAsBoolean());
        assertEquals("EMPTY", rule.get("additionalSpawnDataMode").getAsString());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty());
    }

    @Test void oneSidedSpawnDataCallbackRemainsBlocked() throws Exception {
        Path staging = tempDir.resolve("onesided");
        ConversionContext context = context(staging, "onesided.jar");
        writeCommon(staging);
        writeBehavior(staging, true, false, true);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertFalse(rule.get("emptyAdditionalSpawnDataPairProven").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("additional-spawn-data-not-proven-empty")));
    }

    @Test void nonTrivialSpawnDataMethodRemainsBlockedEvenWhenPairExists() throws Exception {
        Path staging = tempDir.resolve("nontrivial");
        ConversionContext context = context(staging, "nontrivial.jar");
        writeCommon(staging);
        writeBehavior(staging, true, true, false);

        new LegacyEntityRuntimeAdmissionPass().apply(context);

        JsonObject rule = read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertFalse(rule.get("emptyAdditionalSpawnDataPairProven").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().contains("writeSpawnData")));
    }

    private ConversionContext context(Path staging, String jarName) throws Exception {
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Path source = tempDir.resolve(jarName);
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(source))) { }
        LegacyModMetadata metadata = new LegacyModMetadata(jarName, "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(jarName, 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve(jarName + ".candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }

    private static JsonObject read(Path staging) throws Exception {
        return JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void writeCommon(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","legacyNumericId":17,"trackingRange":80,"updateFrequency":2,"velocityUpdates":true,"synchedDataMappingComplete":true,"sourceWideDataWatcherCallClosureComplete":true,"sourceOwnedDataWatcherReadCount":0,"sourceOwnedDataWatcherWriteCount":0,"postInitSourceDataWatcherMutationFree":true,"synchedDataEntries":[]}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityConstructionPass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity","worldConstructorPresent":true,"constructorChainComplete":true,"constructorControlFlowSimple":true,"sourceSetSizeOverridePresent":false,"sizeProofComplete":true,"width":0.5,"height":0.75,"unmappedConstructorEffectCount":0}]}
                """, StandardCharsets.UTF_8);
    }

    private static void writeBehavior(Path staging, boolean writePresent, boolean readPresent, boolean allTrivial) throws Exception {
        StringBuilder callbacks = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        append(callbacks, "{\"kind\":\"ENTITY_INIT\",\"owner\":\"foreign/Orb\",\"method\":\"func_70088_a\",\"descriptor\":\"()V\"}");
        append(callbacks, "{\"kind\":\"READ_NBT\",\"owner\":\"foreign/Orb\",\"method\":\"func_70037_a\",\"descriptor\":\"(Lnet/minecraft/nbt/NBTTagCompound;)V\"}");
        append(callbacks, "{\"kind\":\"WRITE_NBT\",\"owner\":\"foreign/Orb\",\"method\":\"func_70014_b\",\"descriptor\":\"(Lnet/minecraft/nbt/NBTTagCompound;)V\"}");
        append(methods, "{\"owner\":\"foreign/Orb\",\"method\":\"func_70088_a\",\"descriptor\":\"()V\",\"callbackKind\":\"ENTITY_INIT\",\"trivialNoOp\":false}");
        append(methods, "{\"owner\":\"foreign/Orb\",\"method\":\"func_70037_a\",\"descriptor\":\"(Lnet/minecraft/nbt/NBTTagCompound;)V\",\"callbackKind\":\"READ_NBT\",\"trivialNoOp\":true}");
        append(methods, "{\"owner\":\"foreign/Orb\",\"method\":\"func_70014_b\",\"descriptor\":\"(Lnet/minecraft/nbt/NBTTagCompound;)V\",\"callbackKind\":\"WRITE_NBT\",\"trivialNoOp\":true}");
        if (writePresent) {
            append(callbacks, "{\"kind\":\"WRITE_SPAWN_DATA\",\"owner\":\"foreign/Orb\",\"method\":\"writeSpawnData\",\"descriptor\":\"(Lio/netty/buffer/ByteBuf;)V\"}");
            append(methods, "{\"owner\":\"foreign/Orb\",\"method\":\"writeSpawnData\",\"descriptor\":\"(Lio/netty/buffer/ByteBuf;)V\",\"callbackKind\":\"WRITE_SPAWN_DATA\",\"trivialNoOp\":" + allTrivial + "}");
        }
        if (readPresent) {
            append(callbacks, "{\"kind\":\"READ_SPAWN_DATA\",\"owner\":\"foreign/Orb\",\"method\":\"readSpawnData\",\"descriptor\":\"(Lio/netty/buffer/ByteBuf;)V\"}");
            append(methods, "{\"owner\":\"foreign/Orb\",\"method\":\"readSpawnData\",\"descriptor\":\"(Lio/netty/buffer/ByteBuf;)V\",\"callbackKind\":\"READ_SPAWN_DATA\",\"trivialNoOp\":true}");
        }
        String json = "{\"schemaVersion\":1,\"sourceSha256\":\"sha\",\"rules\":[{\"id\":\"foreign:orb\",\"legacyRegistryName\":\"orb\",\"sourceClass\":\"foreign/Orb\",\"externalBaseClass\":\"net/minecraft/entity/Entity\",\"sourceOwnedBehaviorInventoryComplete\":true,\"unclassifiedSourceMethodCount\":0,\"callbacks\":[" + callbacks + "],\"sourceMethods\":[" + methods + "]}]}";
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT), json + "\n", StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder builder, String value) {
        if (!builder.isEmpty()) builder.append(',');
        builder.append(value);
    }
}
