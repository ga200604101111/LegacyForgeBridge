package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyClassDependencyAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorRetirementReadinessTest {
    @TempDir Path tempDir;

    @Test
    void completeRuntimeAndRegistrationStripsStillKeepIndependentRetirementGates()
            throws Exception {
        byte[] bootstrap = bootstrap();
        byte[] block = block();
        byte[] tile = tile();
        byte[] handler = handler();

        Path source = tempDir.resolve("source.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "foreign/machine/Bootstrap.class", bootstrap);
            put(out, "foreign/machine/MachineBlock.class", block);
            put(out, "foreign/machine/MachineTile.class", tile);
            put(out, "foreign/machine/Handler.class", handler);
        }

        Path staging = tempDir.resolve("staging");
        writeClass(staging, "foreign/machine/Bootstrap", bootstrap);
        writeClass(staging, "foreign/machine/MachineBlock", block);
        writeClass(staging, "foreign/machine/MachineTile", tile);
        writeClass(staging, "foreign/machine/Handler", handler);
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
                    "sourceTileClass": "foreign/machine/MachineTile",
                    "legacyTileId": "processor_tile",
                    "baseRuntimeComplete": true,
                    "sourcePresentationComplete": true,
                    "runtimeComplete": true
                  }]
                }
                """,
                StandardCharsets.UTF_8);

        ConversionContext context = context(source, staging);
        new LegacySingleInputProcessorTileRegistrationStripPass().apply(context);
        new LegacySingleInputProcessorBlockRegistrationStripPass().apply(context);

        LegacyClassDependencyAnalyzer.Analysis dependencies =
                new LegacyClassDependencyAnalyzer().analyze(source, staging);
        LegacySingleInputProcessorRetirementReadiness.materialize(
                context, dependencies);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacySingleInputProcessorRetirementReadiness.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertTrue(root.get("retirementReadinessAnalysisWired").getAsBoolean());
        assertTrue(root.get("runtimeCompleteRequired").getAsBoolean());
        assertTrue(root.get("tileRegistrationRetirementRequired").getAsBoolean());
        assertTrue(root.get("blockRegistrationRetirementRequired").getAsBoolean());
        assertTrue(root.get("blockConstructorReplacementRequired").getAsBoolean());
        assertTrue(root.get("blockSourceAllocationRetirementRequired").getAsBoolean());
        assertTrue(root.get("tileConstructorReplacementRequired").getAsBoolean());
        assertTrue(root.get("guiHandlerRetirementRequired").getAsBoolean());
        assertFalse(root.get("retirementAuthorizationWired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("evaluatedRuntimeCohorts").getAsInt());
        assertEquals(0, root.get("retirementCohortCandidateReadyCount").getAsInt());
        assertEquals(1, root.get("retirementCohortBlockedCount").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("modernRuntimeReplacementComplete").getAsBoolean());
        assertTrue(rule.get("tileRegistrationStripComplete").getAsBoolean(),
                rule.toString());
        assertTrue(rule.get("blockRegistrationStripComplete").getAsBoolean(),
                rule.toString());
        assertFalse(rule.get("retirementCohortCandidateReady").getAsBoolean());
        assertFalse(rule.get("sourceClassDeletionAuthorized").getAsBoolean());

        List<String> blockers = new ArrayList<>();
        for (var blocker : rule.getAsJsonArray("blockers")) {
            blockers.add(blocker.getAsString());
        }
        assertTrue(blockers.contains(
                "processor-block-constructor-replacement-not-wired"), blockers.toString());
        assertTrue(blockers.contains(
                "processor-block-source-allocation-retirement-not-wired"), blockers.toString());
        assertTrue(blockers.contains(
                "processor-tile-constructor-replacement-not-wired"), blockers.toString());
        assertTrue(blockers.contains(
                "processor-gui-handler-retirement-not-wired"), blockers.toString());
        assertTrue(blockers.contains(
                "block-candidate-incoming-reference:foreign/machine/Bootstrap"),
                blockers.toString());
        assertTrue(blockers.contains(
                "tile-candidate-incoming-reference:foreign/machine/Handler"),
                blockers.toString());

        assertTrue(Files.isRegularFile(
                staging.resolve("foreign/machine/MachineBlock.class")));
        assertTrue(Files.isRegularFile(
                staging.resolve("foreign/machine/MachineTile.class")));
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

        method.visitLdcInsn(Type.getObjectType("foreign/machine/MachineTile"));
        method.visitLdcInsn("processor_tile");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                false);

        method.visitFieldInsn(
                Opcodes.GETSTATIC,
                "cpw/mods/fml/common/network/NetworkRegistry",
                "INSTANCE",
                "Lcpw/mods/fml/common/network/NetworkRegistry;");
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitTypeInsn(Opcodes.NEW, "foreign/machine/Handler");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "foreign/machine/Handler",
                "<init>",
                "()V",
                false);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "cpw/mods/fml/common/network/NetworkRegistry",
                "registerGuiHandler",
                "(Ljava/lang/Object;Lcpw/mods/fml/common/network/IGuiHandler;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] block() {
        String name = "foreign/machine/MachineBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                name, null, "net/minecraft/block/BlockContainer", null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitInsn(Opcodes.ACONST_NULL);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/block/BlockContainer",
                "<init>",
                "(Lnet/minecraft/block/material/Material;)V",
                false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor create = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "func_149915_a",
                "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;",
                null,
                null);
        create.visitCode();
        create.visitTypeInsn(Opcodes.NEW, "foreign/machine/MachineTile");
        create.visitInsn(Opcodes.DUP);
        create.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "foreign/machine/MachineTile",
                "<init>",
                "()V",
                false);
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] tile() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/MachineTile", null,
                "net/minecraft/tileentity/TileEntity", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] handler() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/Handler", null, "java/lang/Object",
                new String[]{"cpw/mods/fml/common/network/IGuiHandler"});
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor server = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "getServerGuiElement",
                "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;",
                null,
                null);
        server.visitCode();
        server.visitVarInsn(Opcodes.ALOAD, 3);
        server.visitVarInsn(Opcodes.ILOAD, 4);
        server.visitVarInsn(Opcodes.ILOAD, 5);
        server.visitVarInsn(Opcodes.ILOAD, 6);
        server.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/World",
                "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;",
                false);
        server.visitTypeInsn(
                Opcodes.CHECKCAST,
                "foreign/machine/MachineTile");
        server.visitInsn(Opcodes.POP);
        server.visitInsn(Opcodes.ACONST_NULL);
        server.visitInsn(Opcodes.ARETURN);
        server.visitMaxs(0, 0);
        server.visitEnd();
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
