package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyHeldItemVisibilityAnalyzerTest {
    private static final String VISIBLE = "foreign/visibility/GlowBlock";
    private static final String UNPROVEN = "foreign/visibility/NoiseBlock";

    @TempDir
    Path tempDir;

    @Test
    void unrelatedNamespaceProvesOnlyHeldOwnBlockVisibilityPattern() throws Exception {
        Path jar = tempDir.resolve("foreign-visibility.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, VISIBLE + ".class", block(VISIBLE, true));
            put(out, UNPROVEN + ".class", block(UNPROVEN, false));
            put(out, "foreign/visibility/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyHeldItemVisibilityAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(1, analysis.rules().size());

        var rule = analysis.rules().getFirst();
        assertEquals("glow", rule.registryName());
        assertEquals(VISIBLE, rule.sourceBlockClass());
        assertEquals(8, rule.visibleOrMask());
        assertEquals(7, rule.hiddenAndMask());
        assertTrue(analysis.rules().stream().noneMatch(value -> value.registryName().equals("noise")),
                "A similar client tick without Block.getBlockFromItem provenance must fail closed");
    }

    private static byte[] block(String name, boolean proveHeldOwnBlock) {
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

        MethodVisitor tick = writer.visitMethod(Opcodes.ACC_PUBLIC, "randomDisplayTick",
                "(Lnet/minecraft/world/World;IIILjava/util/Random;)V", null, null);
        tick.visitCode();

        tick.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/client/FMLClientHandler", "instance",
                "()Lcpw/mods/fml/client/FMLClientHandler;", false);
        tick.visitInsn(Opcodes.POP);

        tick.visitInsn(Opcodes.ACONST_NULL);
        tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer",
                "getCurrentEquippedItem", "()Lnet/minecraft/item/ItemStack;", false);
        tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "getItem",
                "()Lnet/minecraft/item/Item;", false);
        if (proveHeldOwnBlock) {
            tick.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/block/Block", "getBlockFromItem",
                    "(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;", false);
        } else {
            tick.visitInsn(Opcodes.POP);
            tick.visitInsn(Opcodes.ACONST_NULL);
        }
        tick.visitVarInsn(Opcodes.ALOAD, 0);

        Label hidden = new Label();
        Label end = new Label();
        tick.visitJumpInsn(Opcodes.IF_ACMPNE, hidden);

        tick.visitIntInsn(Opcodes.BIPUSH, 5);
        tick.visitIntInsn(Opcodes.BIPUSH, 8);
        tick.visitInsn(Opcodes.IOR);
        tick.visitVarInsn(Opcodes.ISTORE, 6);
        setBlock(tick);
        bounds(tick, name, 0F, 1F);
        tick.visitJumpInsn(Opcodes.GOTO, end);

        tick.visitLabel(hidden);
        tick.visitIntInsn(Opcodes.BIPUSH, 13);
        tick.visitIntInsn(Opcodes.BIPUSH, 7);
        tick.visitInsn(Opcodes.IAND);
        tick.visitVarInsn(Opcodes.ISTORE, 6);
        setBlock(tick);
        bounds(tick, name, 0.25F, 0.75F);

        tick.visitLabel(end);
        tick.visitInsn(Opcodes.RETURN);
        tick.visitMaxs(0, 0);
        tick.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void setBlock(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.ICONST_3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlock",
                "(IIILnet/minecraft/block/Block;II)Z", false);
        method.visitInsn(Opcodes.POP);
    }

    private static void bounds(MethodVisitor method, String owner, float min, float max) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        pushFloat(method, min);
        method.visitInsn(Opcodes.FCONST_0);
        pushFloat(method, min);
        pushFloat(method, max);
        method.visitInsn(Opcodes.FCONST_1);
        pushFloat(method, max);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setBlockBounds", "(FFFFFF)V", false);
    }

    private static void pushFloat(MethodVisitor method, float value) {
        if (value == 0F) method.visitInsn(Opcodes.FCONST_0);
        else if (value == 1F) method.visitInsn(Opcodes.FCONST_1);
        else method.visitLdcInsn(value);
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/visibility/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        register(method, VISIBLE, "glow");
        register(method, UNPROVEN, "noise");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String type, String id) {
        method.visitTypeInsn(Opcodes.NEW, type);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false);
        method.visitLdcInsn(id);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
