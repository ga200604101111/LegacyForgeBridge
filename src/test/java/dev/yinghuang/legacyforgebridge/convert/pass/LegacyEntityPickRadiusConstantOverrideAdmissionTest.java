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

class LegacyEntityPickRadiusConstantOverrideAdmissionTest {
    @TempDir Path tempDir;

    @Test void exactTypedFloatPickRadiusIsAdmitted() throws Exception {
        Path staging = tempDir.resolve("accepted");
        ConversionContext context = context(staging, "accepted.jar");
        writeBase(staging);
        writeConstants(staging, true, true);
        new LegacyEntityRuntimeAdmissionPass().apply(context);
        JsonObject root = read(staging);
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(root.get("typedConstantBehaviorOverrideAdmissionWired").getAsBoolean());
        assertTrue(rule.get("admitted").getAsBoolean());
        JsonObject mapped = rule.getAsJsonArray("constantBehaviorOverrides").get(0).getAsJsonObject();
        assertEquals("float", mapped.get("constantKind").getAsString());
        assertEquals(0.25F, mapped.get("constantFloat").getAsFloat());
        assertEquals("getPickRadius", mapped.get("targetMethod").getAsString());
    }

    @Test void floatMappingWithoutExplicitTypedValueRemainsBlocked() throws Exception {
        Path staging = tempDir.resolve("blocked");
        ConversionContext context = context(staging, "blocked.jar");
        writeBase(staging);
        writeConstants(staging, true, false);
        new LegacyEntityRuntimeAdmissionPass().apply(context);
        JsonObject rule = read(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("admitted").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").asList().stream()
                .anyMatch(value -> value.getAsString().equals("constant-override-proof-missing:COLLISION_BORDER_SIZE")));
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

    private static void writeBase(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","legacyNumericId":17,"trackingRange":80,"updateFrequency":2,"velocityUpdates":true,"synchedDataMappingComplete":true,"sourceWideDataWatcherCallClosureComplete":true,"sourceOwnedDataWatcherReadCount":0,"sourceOwnedDataWatcherWriteCount":0,"postInitSourceDataWatcherMutationFree":true,"synchedDataEntries":[]}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity","sourceOwnedBehaviorInventoryComplete":true,"unclassifiedSourceMethodCount":0,
                  "callbacks":[{"kind":"ENTITY_INIT","owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V"},{"kind":"READ_NBT","owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},{"kind":"WRITE_NBT","owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},{"kind":"COLLISION_BORDER_SIZE","owner":"foreign/Orb","method":"getCollisionBorderSize","descriptor":"()F"}],
                  "sourceMethods":[{"owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V","callbackKind":"ENTITY_INIT","trivialNoOp":false},{"owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"READ_NBT","trivialNoOp":true},{"owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"WRITE_NBT","trivialNoOp":true},{"owner":"foreign/Orb","method":"getCollisionBorderSize","descriptor":"()F","callbackKind":"COLLISION_BORDER_SIZE","trivialNoOp":false}]}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyEntityConstructionPass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity","worldConstructorPresent":true,"constructorChainComplete":true,"constructorControlFlowSimple":true,"sourceSetSizeOverridePresent":false,"sizeProofComplete":true,"width":0.5,"height":0.75,"unmappedConstructorEffectCount":0}]}
                """, StandardCharsets.UTF_8);
    }

    private static void writeConstants(Path staging, boolean proven, boolean typed) throws Exception {
        String value = proven
                ? "{\"sourceKind\":\"COLLISION_BORDER_SIZE\",\"sourceOwner\":\"foreign/Orb\",\"sourceMethod\":\"getCollisionBorderSize\",\"sourceDescriptor\":\"()F\",\"targetOwner\":\"net/minecraft/world/entity/Entity\",\"targetMethod\":\"getPickRadius\",\"targetDescriptor\":\"()F\",\"mappingSemantics\":\"PICK_RADIUS_FLOAT_IDENTITY\",\"sourceConstantProofComplete\":true,\"runtimeCodegenReady\":true"
                        + (typed ? ",\"constantKind\":\"float\",\"constantFloat\":0.25" : ",\"constantBoolean\":true") + "}"
                : "";
        String array = proven ? "[" + value + "]" : "[]";
        Files.writeString(staging.resolve(LegacyEntityConstantOverridePass.OUTPUT),
                "{\"schemaVersion\":1,\"sourceSha256\":\"sha\",\"rules\":[{\"id\":\"foreign:orb\",\"legacyRegistryName\":\"orb\",\"sourceClass\":\"foreign/Orb\",\"constantOverrides\":" + array + ",\"blockedOverrides\":[]}]}\n",
                StandardCharsets.UTF_8);
    }
}
