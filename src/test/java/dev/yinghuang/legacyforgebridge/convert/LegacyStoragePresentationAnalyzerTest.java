package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyStoragePresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedNormalCubeWithYawAndTwoIconsIsAdmittedButCustomRendererFailsClosed() throws Exception {
        Path jar = tempDir.resolve("Presentation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "unrelated/store/GoodBox.class", block("unrelated/store/GoodBox", 0));
            put(out, "unrelated/store/UnsafeBox.class", block("unrelated/store/UnsafeBox", 22));
            put(out, "unrelated/store/Facing.class", facing());
        }

        LegacyStoragePresentationAnalyzer.Analysis analysis = new LegacyStoragePresentationAnalyzer().analyze(
                jar, List.of("unrelated/store/GoodBox", "unrelated/store/UnsafeBox"));
        assertEquals(1, analysis.presentations().size());
        var presentation = analysis.presentations().get("unrelated/store/GoodBox");
        assertEquals(LegacyStoragePresentationAnalyzer.ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT,
                presentation.orientation());
        assertEquals("foreign:box_front", presentation.frontTexture());
        assertEquals("foreign:box_other", presentation.otherTexture());
        assertEquals(1, analysis.diagnostics().size());
        assertTrue(analysis.diagnostics().getFirst().contains("UnsafeBox"));
    }

    private static byte[] block(String name, int renderType) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/BlockContainer", null);
        w.visitField(Opcodes.ACC_PRIVATE, "front", "Lnet/minecraft/util/IIcon;", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE, "other", "Lnet/minecraft/util/IIcon;", null, null).visitEnd();

        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitInsn(Opcodes.ACONST_NULL);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/BlockContainer", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitInsn(Opcodes.FCONST_0); ctor.visitInsn(Opcodes.FCONST_0); ctor.visitInsn(Opcodes.FCONST_0);
        ctor.visitInsn(Opcodes.FCONST_1); ctor.visitInsn(Opcodes.FCONST_1); ctor.visitInsn(Opcodes.FCONST_1);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "func_149676_a", "(FFFFFF)V", false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();

        intReturn(w, "func_149645_b", "()I", renderType);
        intReturn(w, "func_149662_c", "()Z", 1);
        intReturn(w, "func_149686_d", "()Z", 1);

        MethodVisitor placed = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149689_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
                null, null);
        placed.visitCode();
        placed.visitVarInsn(Opcodes.ALOAD, 1); placed.visitVarInsn(Opcodes.ILOAD, 2);
        placed.visitVarInsn(Opcodes.ILOAD, 3); placed.visitVarInsn(Opcodes.ILOAD, 4);
        placed.visitVarInsn(Opcodes.ALOAD, 5);
        placed.visitMethodInsn(Opcodes.INVOKESTATIC, "unrelated/store/Facing", "quadrant",
                "(Lnet/minecraft/entity/Entity;)B", false);
        placed.visitInsn(Opcodes.ICONST_3);
        placed.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72921_c", "(IIIII)Z", false);
        placed.visitInsn(Opcodes.POP); placed.visitInsn(Opcodes.RETURN); placed.visitMaxs(0, 0); placed.visitEnd();

        MethodVisitor icons = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149651_a",
                "(Lnet/minecraft/client/renderer/texture/IIconRegister;)V", null, null);
        icons.visitCode();
        icons.visitVarInsn(Opcodes.ALOAD, 0); icons.visitVarInsn(Opcodes.ALOAD, 1); icons.visitLdcInsn("foreign:box_front");
        icons.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/client/renderer/texture/IIconRegister", "func_94245_a",
                "(Ljava/lang/String;)Lnet/minecraft/util/IIcon;", true);
        icons.visitFieldInsn(Opcodes.PUTFIELD, name, "front", "Lnet/minecraft/util/IIcon;");
        icons.visitVarInsn(Opcodes.ALOAD, 0); icons.visitVarInsn(Opcodes.ALOAD, 1); icons.visitLdcInsn("foreign:box_other");
        icons.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/client/renderer/texture/IIconRegister", "func_94245_a",
                "(Ljava/lang/String;)Lnet/minecraft/util/IIcon;", true);
        icons.visitFieldInsn(Opcodes.PUTFIELD, name, "other", "Lnet/minecraft/util/IIcon;");
        icons.visitInsn(Opcodes.RETURN); icons.visitMaxs(0, 0); icons.visitEnd();

        MethodVisitor icon = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149673_e",
                "(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;", null, null);
        icon.visitCode();
        icon.visitVarInsn(Opcodes.ALOAD, 1); icon.visitVarInsn(Opcodes.ILOAD, 2); icon.visitVarInsn(Opcodes.ILOAD, 3); icon.visitVarInsn(Opcodes.ILOAD, 4);
        icon.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/world/IBlockAccess", "func_72805_g", "(III)I", true);
        icon.visitVarInsn(Opcodes.ISTORE, 6);
        face(icon, name, 0, 2); face(icon, name, 1, 5); face(icon, name, 2, 3); face(icon, name, 3, 4);
        icon.visitVarInsn(Opcodes.ALOAD, 0); icon.visitFieldInsn(Opcodes.GETFIELD, name, "other", "Lnet/minecraft/util/IIcon;");
        icon.visitInsn(Opcodes.ARETURN); icon.visitMaxs(0, 0); icon.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static void face(MethodVisitor m, String owner, int meta, int side) {
        Label next = new Label();
        m.visitVarInsn(Opcodes.ILOAD, 6);
        if (meta == 0) m.visitJumpInsn(Opcodes.IFNE, next);
        else { push(m, meta); m.visitJumpInsn(Opcodes.IF_ICMPNE, next); }
        m.visitVarInsn(Opcodes.ILOAD, 5); push(m, side); m.visitJumpInsn(Opcodes.IF_ICMPNE, next);
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitFieldInsn(Opcodes.GETFIELD, owner, "front", "Lnet/minecraft/util/IIcon;");
        m.visitInsn(Opcodes.ARETURN); m.visitLabel(next);
    }

    private static byte[] facing() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "unrelated/store/Facing", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "quadrant",
                "(Lnet/minecraft/entity/Entity;)B", null, null);
        m.visitCode(); m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70177_z", "F");
        m.visitLdcInsn(4.0F); m.visitInsn(Opcodes.FMUL); m.visitLdcInsn(360.0F); m.visitInsn(Opcodes.FDIV);
        m.visitInsn(Opcodes.F2D); m.visitLdcInsn(0.5D); m.visitInsn(Opcodes.DADD);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/util/MathHelper", "func_76128_c", "(D)I", false);
        m.visitInsn(Opcodes.ICONST_3); m.visitInsn(Opcodes.IAND); m.visitInsn(Opcodes.I2B); m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static void intReturn(ClassWriter w, String name, String desc, int value) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null); m.visitCode(); push(m, value);
        m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void push(MethodVisitor m, int value) {
        if (value >= 0 && value <= 5) m.visitInsn(Opcodes.ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) m.visitIntInsn(Opcodes.BIPUSH, value);
        else m.visitLdcInsn(value);
    }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
