package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyItemBlockPlacementAnalyzerTest {
    @TempDir Path tempDir;

    @Test void defaultMaskingAndCustomPlaceBlockAtStayDistinctAcrossUnrelatedNamespace() throws Exception {
        Path jar = tempDir.resolve("ForeignItemBlocks.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/world/PlainBlock.class", block("foreign/world/PlainBlock"));
            put(out, "foreign/world/MaskedBlock.class", block("foreign/world/MaskedBlock"));
            put(out, "foreign/world/ExtendedBlock.class", block("foreign/world/ExtendedBlock"));
            put(out, "foreign/item/MaskingItemBlock.class", maskingItemBlock());
            put(out, "foreign/item/ExtendedItemBlock.class", extendedItemBlock());
            put(out, "foreign/world/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyItemBlockPlacementAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(3, analysis.behaviors().size());

        var plain = analysis.behaviors().stream()
                .filter(value -> value.registryName().equals("plain"))
                .findFirst().orElseThrow();
        assertEquals("net/minecraft/item/ItemBlock", plain.itemBlockClass());
        assertEquals(0, plain.metadataProgram().evaluate(15));
        assertFalse(plain.customPlaceBlockAt());

        var masked = analysis.behaviors().stream()
                .filter(value -> value.registryName().equals("masked"))
                .findFirst().orElseThrow();
        assertEquals("foreign/item/MaskingItemBlock", masked.itemBlockClass());
        assertEquals(7, masked.metadataProgram().evaluate(15));
        assertEquals(3, masked.metadataProgram().evaluate(11));
        assertFalse(masked.customPlaceBlockAt());
        assertEquals("foreign/item/MaskingItemBlock", masked.metadataOwner());

        var extended = analysis.behaviors().stream()
                .filter(value -> value.registryName().equals("extended"))
                .findFirst().orElseThrow();
        assertTrue(extended.customPlaceBlockAt());
        assertEquals("foreign/item/ExtendedItemBlock", extended.placeBlockAtOwner());
        assertEquals(5, extended.metadataProgram().evaluate(5));
    }

    private static byte[] block(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] maskingItemBlock() {
        ClassWriter w = itemBlockClass("foreign/item/MaskingItemBlock");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_77647_b", "(I)I", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ILOAD, 1);
        m.visitIntInsn(Opcodes.BIPUSH, 7);
        m.visitInsn(Opcodes.IAND);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 2);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] extendedItemBlock() {
        ClassWriter w = itemBlockClass("foreign/item/ExtendedItemBlock");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "getMetadata", "(I)I", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ILOAD, 1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 2);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC, "placeBlockAt",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/world/World;IIIIFFFI)Z", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 13);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static ClassWriter itemBlockClass(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemBlock", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/block/Block;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemBlock", "<init>",
                "(Lnet/minecraft/block/Block;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        return w;
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/world/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor av = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        av.visitEnd();
        m.visitCode();
        registerDefault(m, "foreign/world/PlainBlock", "plain");
        registerCustom(m, "foreign/world/MaskedBlock", "foreign/item/MaskingItemBlock", "masked");
        registerCustom(m, "foreign/world/ExtendedBlock", "foreign/item/ExtendedItemBlock", "extended");
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void registerDefault(MethodVisitor m, String blockClass, String name) {
        m.visitTypeInsn(Opcodes.NEW, blockClass);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void registerCustom(MethodVisitor m, String blockClass, String itemBlockClass, String name) {
        m.visitTypeInsn(Opcodes.NEW, blockClass);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        m.visitLdcInsn(Type.getObjectType(itemBlockClass));
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        m.visitInsn(Opcodes.POP);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
