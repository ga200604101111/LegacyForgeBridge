package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorBlockRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test
    void neutralizesUniqueRuntimeCompleteProcessorBlockRegistrationButKeepsAllocation()
            throws Exception {
        byte[] bootstrap = bootstrap();
        byte[] block = block();
        Path source = tempDir.resolve("source.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "foreign/machine/Bootstrap.class", bootstrap);
            put(out, "foreign/machine/MachineBlock.class", block);
        }

        Path staging = tempDir.resolve("staging");
        writeClass(staging, "foreign/machine/Bootstrap", bootstrap);
        writeClass(staging, "foreign/machine/MachineBlock", block);
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(
                staging.resolve(LegacySingleInputProcessorPass.OUTPUT),
                """
                {
                  "schemaVersion": 4,
                  "sourceSha256": "sha",
                  "machines": [{
                    "id": "foreign:processor",
                    "sourceBlockClass": "foreign/machine/MachineBlock",
                    "baseRuntimeComplete": true,
                    "sourcePresentationComplete": true,
                    "runtimeComplete": true
                  }]
                }
                """,
                StandardCharsets.UTF_8);

        ConversionContext context = context(source, staging);
        new LegacySingleInputProcessorBlockRegistrationStripPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(
                        LegacySingleInputProcessorBlockRegistrationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertTrue(root.get("blockRegistrationStripWired").getAsBoolean());
        assertTrue(root.get("runtimeCompleteRequired").getAsBoolean());
        assertTrue(root.get("argumentEvaluationPreserved").getAsBoolean());
        assertTrue(root.get("constructorSideEffectsPreserved").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("blockRegistrationStripCompleteRules").getAsInt());
        assertEquals(1, root.get("strippedBlockRegistrationSites").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("blockRegistrationStripComplete").getAsBoolean(),
                rule.toString());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty(), rule.toString());
        assertEquals("processor", rule.get("legacyRegistryName").getAsString());
        assertEquals("foreign/machine/Bootstrap",
                rule.get("sourceOwner").getAsString());

        byte[] rewritten = Files.readAllBytes(
                staging.resolve("foreign/machine/Bootstrap.class"));
        boolean[] registerBlock = {false};
        boolean[] allocation = {false};
        new ClassReader(rewritten).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (owner.equals("cpw/mods/fml/common/registry/GameRegistry")
                                && methodName.equals("registerBlock")) {
                            registerBlock[0] = true;
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals("foreign/machine/MachineBlock")) {
                            allocation[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertFalse(registerBlock[0]);
        assertTrue(allocation[0]);
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "source.jar",
                "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "source.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis,
                new DiagnosticCollector(), "generic-test");
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "boot",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",
                null,
                null);
        AnnotationVisitor annotation = method.visitAnnotation(
                "Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/machine/MachineBlock");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "foreign/machine/MachineBlock",
                "<init>",
                "()V",
                false);
        method.visitLdcInsn("processor");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] block() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/MachineBlock", null,
                "net/minecraft/block/Block", null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitInsn(Opcodes.ACONST_NULL);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/block/Block",
                "<init>",
                "(Lnet/minecraft/block/material/Material;)V",
                false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeClass(Path root, String name, byte[] bytes)
            throws Exception {
        Path path = root.resolve(name + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes)
            throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
