package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockActivationMetadataCompilerTest {
    @TempDir Path tempDir;

    @Test
    void exactCurrentPositionMetadataReadCompilesToTypedInput() throws Exception {
        Path jar = tempDir.resolve("MetadataGate.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/MetadataGate.class", metadataGate(false));
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/MetadataGate", "metadata_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.programs().size());
        var program = analysis.programs().getFirst();
        assertTrue(program.instructions().stream().anyMatch(value -> value.op() == LegacyBlockActivationCompiler.Op.LOAD_META));
        assertFalse(program.evaluate(1, 2));
        assertTrue(program.evaluate(1, 3));
        assertThrows(IllegalStateException.class, () -> program.evaluate(1));
    }

    @Test
    void coordinateAdjustedMetadataReadFailsClosed() throws Exception {
        Path jar = tempDir.resolve("NeighborMetadataGate.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/MetadataGate.class", metadataGate(true));
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/MetadataGate", "metadata_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertEquals(1, analysis.activationCallbacks());
        assertTrue(analysis.programs().isEmpty());
        assertTrue(analysis.diagnostics().stream().anyMatch(value -> value.contains("Unsupported pure activation callback")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] metadataGate(boolean neighborRead) {
        ClassWriter writer = blockClass("foreign/use/MetadataGate");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        if (neighborRead) {
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IADD);
        }
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72805_g", "(III)I", false);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IAND);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(4, 10);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter blockClass(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        return writer;
    }

    private static byte[] bootstrap(String blockClass, String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/use/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, blockClass);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
