package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFuelHandlerAnalyzerTest {
    @TempDir Path tempDir;

    @Test void itemAndMetadataSpecificBlockFuelRulesAreRecoveredWithoutExecutingSource() throws Exception {
        Path jar = tempDir.resolve("ForeignFuel.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/fuel/Bootstrap.class", bootstrap());
            put(out, "foreign/fuel/Handler.class", handler());
        }

        LegacyFuelHandlerAnalyzer.Analysis analysis = new LegacyFuelHandlerAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(2, analysis.rules().size());

        var straw = analysis.rules().stream()
                .filter(rule -> "straw_bundle".equals(rule.registry().registryName()))
                .findFirst().orElseThrow();
        assertEquals(LegacyRegistryAnalyzer.Kind.ITEM, straw.registry().kind());
        assertTrue(straw.anyMetadata());
        assertEquals(30, straw.burnTicks());

        var decor = analysis.rules().stream()
                .filter(rule -> "decor_tile".equals(rule.registry().registryName()))
                .findFirst().orElseThrow();
        assertEquals(LegacyRegistryAnalyzer.Kind.BLOCK, decor.registry().kind());
        assertFalse(decor.anyMetadata());
        assertEquals(4, decor.metadata());
        assertEquals(270, decor.burnTicks());
    }

    @Test void nonConstantOrUnstructuredFuelLogicFailsClosed() throws Exception {
        Path jar = tempDir.resolve("UnsafeFuel.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/fuel/UnsafeBootstrap.class", unsafeBootstrap());
            put(out, "foreign/fuel/UnsafeHandler.class", unsafeHandler());
        }

        LegacyFuelHandlerAnalyzer.Analysis analysis = new LegacyFuelHandlerAnalyzer().analyze(jar);
        assertTrue(analysis.rules().isEmpty());
        assertFalse(analysis.diagnostics().isEmpty());
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/fuel/Bootstrap", null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "STRAW", "Lnet/minecraft/item/Item;", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "DECOR", "Lnet/minecraft/block/Block;", null, null).visitEnd();

        MethodVisitor helper = w.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "item",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;", null, null);
        helper.visitCode();
        helper.visitVarInsn(Opcodes.ALOAD, 0);
        helper.visitVarInsn(Opcodes.ALOAD, 1);
        helper.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
        helper.visitVarInsn(Opcodes.ALOAD, 0);
        helper.visitInsn(Opcodes.ARETURN);
        helper.visitMaxs(2, 2);
        helper.visitEnd();

        helper = w.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", null, null);
        helper.visitCode();
        helper.visitVarInsn(Opcodes.ALOAD, 0);
        helper.visitVarInsn(Opcodes.ALOAD, 1);
        helper.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        helper.visitVarInsn(Opcodes.ALOAD, 0);
        helper.visitInsn(Opcodes.ARETURN);
        helper.visitMaxs(2, 2);
        helper.visitEnd();

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "init",
                "(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V", null, null);
        AnnotationVisitor event = init.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        event.visitEnd();
        init.visitCode();
        init.visitTypeInsn(Opcodes.NEW, "net/minecraft/item/Item");
        init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/Item", "<init>", "()V", false);
        init.visitLdcInsn("straw_bundle");
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "foreign/fuel/Bootstrap", "item",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;", false);
        init.visitFieldInsn(Opcodes.PUTSTATIC, "foreign/fuel/Bootstrap", "STRAW", "Lnet/minecraft/item/Item;");

        init.visitTypeInsn(Opcodes.NEW, "net/minecraft/block/Block");
        init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>", "()V", false);
        init.visitLdcInsn("decor_tile");
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "foreign/fuel/Bootstrap", "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        init.visitFieldInsn(Opcodes.PUTSTATIC, "foreign/fuel/Bootstrap", "DECOR", "Lnet/minecraft/block/Block;");

        init.visitTypeInsn(Opcodes.NEW, "foreign/fuel/Handler");
        init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/fuel/Handler", "<init>", "()V", false);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerFuelHandler",
                "(Lcpw/mods/fml/common/IFuelHandler;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(3, 2);
        init.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] handler() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/fuel/Handler", null, "java/lang/Object",
                new String[]{"cpw/mods/fml/common/IFuelHandler"});
        constructor(w, "foreign/fuel/Handler");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "getBurnTime", "(Lnet/minecraft/item/ItemStack;)I", null, null);
        m.visitCode();
        Label second = new Label();
        Label fallback = new Label();
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77973_b", "()Lnet/minecraft/item/Item;", false);
        m.visitFieldInsn(Opcodes.GETSTATIC, "foreign/fuel/Bootstrap", "STRAW", "Lnet/minecraft/item/Item;");
        m.visitJumpInsn(Opcodes.IF_ACMPNE, second);
        m.visitIntInsn(Opcodes.BIPUSH, 30);
        m.visitInsn(Opcodes.IRETURN);

        m.visitLabel(second);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "getItem", "()Lnet/minecraft/item/Item;", false);
        m.visitFieldInsn(Opcodes.GETSTATIC, "foreign/fuel/Bootstrap", "DECOR", "Lnet/minecraft/block/Block;");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/Item", "func_150898_a",
                "(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;", false);
        m.visitJumpInsn(Opcodes.IF_ACMPNE, fallback);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77952_i", "()I", false);
        m.visitInsn(Opcodes.ICONST_4);
        m.visitJumpInsn(Opcodes.IF_ICMPNE, fallback);
        m.visitIntInsn(Opcodes.SIPUSH, 270);
        m.visitInsn(Opcodes.IRETURN);

        m.visitLabel(fallback);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 2);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] unsafeBootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/fuel/UnsafeBootstrap", null, "java/lang/Object", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "init",
                "(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V", null, null);
        init.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true).visitEnd();
        init.visitCode();
        init.visitTypeInsn(Opcodes.NEW, "foreign/fuel/UnsafeHandler");
        init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/fuel/UnsafeHandler", "<init>", "()V", false);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerFuelHandler",
                "(Lcpw/mods/fml/common/IFuelHandler;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] unsafeHandler() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/fuel/UnsafeHandler", null, "java/lang/Object",
                new String[]{"cpw/mods/fml/common/IFuelHandler"});
        constructor(w, "foreign/fuel/UnsafeHandler");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "getBurnTime", "(Lnet/minecraft/item/ItemStack;)I", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77952_i", "()I", false);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IADD);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 2);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void constructor(ClassWriter w, String owner) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(1, 1);
        m.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
