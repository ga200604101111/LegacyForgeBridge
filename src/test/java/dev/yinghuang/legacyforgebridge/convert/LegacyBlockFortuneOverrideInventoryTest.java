package dev.yinghuang.legacyforgebridge.convert;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockFortuneOverrideInventoryTest {
    @TempDir Path tempDir;

    @Test void sourceOwnedSrgFortuneQuantityOverrideIsVisibleToLaterDropSafetyChecks() throws Exception {
        Path jar = tempDir.resolve("ForeignFortune.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/fortune/BaseOre.class", baseOre());
            put(out, "foreign/fortune/RichOre.class", richOre());
            put(out, "foreign/fortune/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockBehaviorAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.blocks().size());
        var block = analysis.blocks().getFirst();
        assertEquals("rich_ore", block.registryName());
        var callback = block.callbacks().stream()
                .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.QUANTITY_DROPPED_WITH_BONUS)
                .findFirst().orElseThrow();
        assertEquals("foreign/fortune/BaseOre", callback.owner());
        assertEquals("func_149679_a", callback.method());
        assertEquals("(ILjava/util/Random;)I", callback.descriptor());
    }

    private static byte[] baseOre() {
        String owner = "foreign/fortune/BaseOre";
        ClassWriter writer = blockClass(owner, "net/minecraft/block/Block");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149679_a",
                "(ILjava/util/Random;)I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 3);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] richOre() {
        ClassWriter writer = blockClass("foreign/fortune/RichOre", "foreign/fortune/BaseOre");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter blockClass(String owner, String parent) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, parent, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        if ("net/minecraft/block/Block".equals(parent)) {
            init.visitInsn(Opcodes.ACONST_NULL);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>",
                    "(Lnet/minecraft/block/material/Material;)V", false);
        } else {
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        }
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        return writer;
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/fortune/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/fortune/RichOre");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/fortune/RichOre", "<init>", "()V", false);
        method.visitLdcInsn("rich_ore");
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
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

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
