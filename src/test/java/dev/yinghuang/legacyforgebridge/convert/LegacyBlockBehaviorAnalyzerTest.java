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

class LegacyBlockBehaviorAnalyzerTest {
    @TempDir Path tempDir;

    @Test void registeredBlockGetsEffectiveMcpAndSrgCallbacksAcrossSourceOwnedBaseClass() throws Exception {
        Path jar = tempDir.resolve("ForeignBlocks.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "other/world/BaseLamp.class", baseLamp());
            put(out, "other/world/TurnLamp.class", turnLamp());
            put(out, "other/world/GhostLamp.class", ghostLamp());
            put(out, "other/world/Bootstrap.class", bootstrap());
        }

        LegacyBlockBehaviorAnalyzer.Analysis analysis = new LegacyBlockBehaviorAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.blocks().size());
        var block = analysis.blocks().getFirst();
        assertEquals("turn_lamp", block.registryName());
        assertEquals("other/world/TurnLamp", block.implementationClass());
        assertEquals(4, block.callbacks().size());

        var activate = block.callbacks().stream()
                .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.ACTIVATE)
                .findFirst().orElseThrow();
        assertEquals("other/world/TurnLamp", activate.owner());
        assertEquals("func_149727_a", activate.method());

        var placementMeta = block.callbacks().stream()
                .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.PLACED)
                .findFirst().orElseThrow();
        assertEquals("other/world/TurnLamp", placementMeta.owner());
        assertEquals("func_149660_a", placementMeta.method());

        var placed = block.callbacks().stream()
                .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.PLACED_BY)
                .findFirst().orElseThrow();
        assertEquals("other/world/TurnLamp", placed.owner());
        assertEquals("onBlockPlacedBy", placed.method());

        var neighbor = block.callbacks().stream()
                .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.NEIGHBOR_CHANGED)
                .findFirst().orElseThrow();
        assertEquals("other/world/BaseLamp", neighbor.owner());
        assertEquals("func_149695_a", neighbor.method());
    }

    @Test void unregisteredAndWrongDescriptorMethodsAreNotInventedAsBlockBehavior() throws Exception {
        Path jar = tempDir.resolve("WrongShape.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "other/world/PlainLamp.class", plainLampWithWrongActivationShape());
            put(out, "other/world/GhostLamp.class", ghostLamp());
            put(out, "other/world/Bootstrap.class", bootstrap("other/world/PlainLamp", "plain_lamp"));
        }
        var analysis = new LegacyBlockBehaviorAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertTrue(analysis.blocks().isEmpty());
        assertEquals(0, analysis.callbackCount());
    }

    private static byte[] baseLamp() {
        ClassWriter w = blockClass("other/world/BaseLamp", "net/minecraft/block/Block");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149695_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 6);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] turnLamp() {
        ClassWriter w = blockClass("other/world/TurnLamp", "other/world/BaseLamp");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149660_a",
                "(Lnet/minecraft/world/World;IIIIFFFI)I", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ILOAD, 9);
        m.visitIntInsn(Opcodes.BIPUSH, 8);
        m.visitInsn(Opcodes.IOR);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 10);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockPlacedBy",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 7);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] ghostLamp() {
        ClassWriter w = blockClass("other/world/GhostLamp", "net/minecraft/block/Block");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] plainLampWithWrongActivationShape() {
        ClassWriter w = blockClass("other/world/PlainLamp", "net/minecraft/block/Block");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated", "()Z", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 1);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static ClassWriter blockClass(String name, String parent) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, parent, null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
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
        return w;
    }

    private static byte[] bootstrap() {
        return bootstrap("other/world/TurnLamp", "turn_lamp");
    }

    private static byte[] bootstrap(String blockClass, String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "other/world/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor av = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        av.visitEnd();
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, blockClass);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
