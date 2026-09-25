package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityCodegenPassTest {
    @TempDir Path tempDir;

    @Test void generatesIsolatedJava21EntityClassWithAllMappedAccessorsAndLegacyBaseHurtSemantics() throws Exception {
        Path staging = tempDir.resolve("generated");
        ConversionContext context = context(staging, "generated.jar");
        writeAdmission(staging, true);

        new LegacyPlainEntityCodegenPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("runtimeClassGenerationWired").getAsBoolean());
        assertFalse(root.get("entityTypeRegistrationWired").getAsBoolean());
        assertEquals(1, root.get("generatedClassCount").getAsInt());

        JsonObject generated = root.getAsJsonArray("generatedClasses").get(0).getAsJsonObject();
        String internalName = generated.get("generatedInternalName").getAsString();
        assertEquals(5, generated.get("synchedDataAccessorCount").getAsInt());
        assertTrue(generated.get("legacyBaseHurtSemanticsMapped").getAsBoolean());
        Path classFile = staging.resolve(internalName + ".class");
        assertTrue(Files.isRegularFile(classFile));

        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(Files.readAllBytes(classFile)).accept(node, 0);
        assertEquals(Opcodes.V21, node.version);
        assertEquals("net/minecraft/world/entity/Entity", node.superName);
        assertEquals(5L, node.fields.stream().filter(field -> field.name.startsWith("DATA_")).count());

        MethodNode clinit = method(node, "<clinit>", "()V");
        assertNotNull(clinit);
        assertEquals(5L, calls(clinit, "net/minecraft/network/syncher/SynchedEntityData", "defineId"));
        assertEquals(2L, serializerReads(clinit, "INT"),
                "source short and int must each receive isolated modern INT accessors");
        assertEquals(1L, serializerReads(clinit, "BYTE"));
        assertEquals(1L, serializerReads(clinit, "FLOAT"));
        assertEquals(1L, serializerReads(clinit, "STRING"));

        MethodNode define = method(node, "defineSynchedData", "(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V");
        assertNotNull(define);
        assertEquals(5L, calls(define, "net/minecraft/network/syncher/SynchedEntityData$Builder", "define"));
        assertNotNull(method(node, "readAdditionalSaveData", "(Lnet/minecraft/world/level/storage/ValueInput;)V"));
        assertNotNull(method(node, "addAdditionalSaveData", "(Lnet/minecraft/world/level/storage/ValueOutput;)V"));

        MethodNode hurt = method(node, "hurtServer",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z");
        assertNotNull(hurt);
        assertEquals(1L, calls(hurt, "net/minecraft/world/entity/Entity", "isInvulnerable"));
        assertEquals(1L, calls(hurt, "net/minecraft/world/entity/Entity", "markHurt"));
    }

    @Test void blockedAdmissionDoesNotGenerateEntityClass() throws Exception {
        Path staging = tempDir.resolve("blocked");
        ConversionContext context = context(staging, "blocked.jar");
        writeAdmission(staging, false);

        new LegacyPlainEntityCodegenPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(0, root.get("generatedClassCount").getAsInt());
        assertTrue(root.getAsJsonArray("generatedClasses").isEmpty());
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

    private static void writeAdmission(Path staging, boolean admitted) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), """
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "rules":[
                    {
                      "id":"foreign:orb",
                      "legacyRegistryName":"orb",
                      "sourceClass":"foreign/Orb",
                      "trackingRange":80,
                      "updateFrequency":2,
                      "velocityUpdates":true,
                      "width":0.5,
                      "height":0.75,
                      "family":"PLAIN_ENTITY_SYNCHED_DATA_ONLY",
                      "admitted":%s,
                      "synchedDataEntries":[
                        {"sourceIndex":12,"modernValueKind":"byte","serializer":"BYTE","adapter":"identity","defaultValue":1},
                        {"sourceIndex":13,"modernValueKind":"int","serializer":"INT","adapter":"signed_short_widen","defaultValue":-4},
                        {"sourceIndex":14,"modernValueKind":"int","serializer":"INT","adapter":"identity","defaultValue":33},
                        {"sourceIndex":15,"modernValueKind":"float","serializer":"FLOAT","adapter":"identity","defaultValue":1.25},
                        {"sourceIndex":16,"modernValueKind":"string","serializer":"STRING","adapter":"identity","defaultValue":"orb"}
                      ]
                    }
                  ]
                }
                """.formatted(Boolean.toString(admitted)), StandardCharsets.UTF_8);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static long serializerReads(MethodNode method, String name) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals("net/minecraft/network/syncher/EntityDataSerializers")
                && field.name.equals(name)) count++;
        return count;
    }
}
