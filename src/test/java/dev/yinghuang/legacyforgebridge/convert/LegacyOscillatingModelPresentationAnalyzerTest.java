package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyOscillatingModelPresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedFixedBodyAndSingleDegreeJointAreProvenWithoutNames() throws Exception {
        Path jar = tempDir.resolve("oscillating-presentation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/osc/Tile.class", tile());
            put(out, "foreign/osc/Client.class", client());
            put(out, "foreign/osc/Renderer.class", renderer());
            put(out, "foreign/osc/Model.class", model());
            put(out, "assets/foreign/textures/entitys/osc.png", pngHeader(64, 32));
        }

        var rule = new LegacyOscillatingModelBlockAnalyzer.Rule(
                "osc", null, "foreign/osc/Block", "foreign/osc/Tile", "ServerTile",
                new int[]{2, 1, 0, 3}, true, 0, true, true, 1,
                "swing", "direction", 90, 0.7F, 0F, 45F, true, false
        );
        var analysis = new LegacyOscillatingModelPresentationAnalyzer().analyze(jar, rule);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        var presentation = analysis.presentation().orElseThrow();
        assertEquals("foreign/osc/Renderer", presentation.sourceRendererClass());
        assertEquals("ClientTile", presentation.clientTileId());
        assertEquals("foreign/osc/Model", presentation.sourceModelClass());
        assertEquals("foreign:textures/entitys/osc.png", presentation.texture());
        assertEquals(64, presentation.imageWidth());
        assertEquals(32, presentation.imageHeight());
        assertEquals(64, presentation.modelTextureWidth());
        assertEquals(32, presentation.modelTextureHeight());
        assertEquals(2, presentation.parts().size());
        assertEquals("arm", presentation.animatedPartField());
        assertEquals(0.0625F, presentation.modelScale());
        assertEquals(0.5F, presentation.translateX());
        assertEquals(0.5F, presentation.translateY());
        assertEquals(0.5F, presentation.translateZ());
        assertEquals(3, presentation.metadataMask());
        assertEquals(90F, presentation.yawDegreesPerMeta());
        assertEquals(180F, presentation.yawOffsetDegrees());
        assertTrue(presentation.whiteColor());
        assertTrue(presentation.dynamicAngleUsesDegrees());
        assertFalse(presentation.inventoryPresentationProven());
        var arm = presentation.parts().stream().filter(part -> part.field().equals("arm")).findFirst().orElseThrow();
        assertTrue(arm.animated());
        assertTrue(arm.mirror());
        assertEquals((float) Math.PI, arm.baseYRot());
    }

    private static byte[] tile() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/osc/Tile", null,
                "net/minecraft/tileentity/TileEntity", null);
        w.visitField(Opcodes.ACC_PRIVATE, "swing", "F", null, null).visitEnd();
        MethodVisitor getter = w.visitMethod(Opcodes.ACC_PUBLIC, "angle", "()F", null, null);
        getter.visitCode();
        getter.visitVarInsn(Opcodes.ALOAD, 0);
        getter.visitFieldInsn(Opcodes.GETFIELD, "foreign/osc/Tile", "swing", "F");
        getter.visitInsn(Opcodes.FRETURN);
        getter.visitMaxs(0, 0);
        getter.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] client() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/osc/Client", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "bind", "()V", null, null);
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/osc/Tile"));
        m.visitLdcInsn("ClientTile");
        m.visitTypeInsn(Opcodes.NEW, "foreign/osc/Renderer");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/osc/Renderer", "<init>", "()V", false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/client/registry/ClientRegistry", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] renderer() {
        String owner = "foreign/osc/Renderer";
        String model = "foreign/osc/Model";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null,
                "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer", null);
        w.visitField(Opcodes.ACC_PRIVATE, "model", "L" + model + ";", null, null).visitEnd();

        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitTypeInsn(Opcodes.NEW, model);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, model, "<init>", "()V", false);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, owner, "model", "L" + model + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor render = w.visitMethod(Opcodes.ACC_PRIVATE, "draw",
                "(Lforeign/osc/Tile;DDD)V", null, null);
        render.visitCode();
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPushMatrix", "()V", false);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "foreign/osc/Tile", "func_145832_p", "()I", false);
        render.visitVarInsn(Opcodes.ISTORE, 8);
        render.visitVarInsn(Opcodes.DLOAD, 2);
        render.visitInsn(Opcodes.D2F);
        render.visitLdcInsn(0.5F);
        render.visitInsn(Opcodes.FADD);
        render.visitVarInsn(Opcodes.DLOAD, 4);
        render.visitInsn(Opcodes.D2F);
        render.visitLdcInsn(0.5F);
        render.visitInsn(Opcodes.FADD);
        render.visitVarInsn(Opcodes.DLOAD, 6);
        render.visitInsn(Opcodes.D2F);
        render.visitLdcInsn(0.5F);
        render.visitInsn(Opcodes.FADD);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTranslatef", "(FFF)V", false);
        render.visitVarInsn(Opcodes.ILOAD, 8);
        render.visitInsn(Opcodes.ICONST_3);
        render.visitInsn(Opcodes.IAND);
        render.visitInsn(Opcodes.I2F);
        render.visitLdcInsn(90F);
        render.visitInsn(Opcodes.FMUL);
        render.visitLdcInsn(180F);
        render.visitInsn(Opcodes.FADD);
        render.visitInsn(Opcodes.FCONST_0);
        render.visitInsn(Opcodes.FCONST_1);
        render.visitInsn(Opcodes.FCONST_0);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glRotatef", "(FFFF)V", false);
        render.visitInsn(Opcodes.FCONST_1);
        render.visitInsn(Opcodes.FCONST_1);
        render.visitInsn(Opcodes.FCONST_1);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glColor3f", "(FFF)V", false);
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitFieldInsn(Opcodes.GETFIELD, owner, "model", "L" + model + ";");
        render.visitInsn(Opcodes.ACONST_NULL);
        for (int i = 0; i < 5; i++) render.visitInsn(Opcodes.FCONST_0);
        render.visitLdcInsn(0.0625F);
        render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, model, "func_78088_a",
                "(Lnet/minecraft/entity/Entity;FFFFFF)V", false);
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitFieldInsn(Opcodes.GETFIELD, owner, "model", "L" + model + ";");
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "foreign/osc/Tile", "angle", "()F", false);
        render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, model, "renderJoint", "(F)V", false);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false);
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();

        MethodVisitor clinit = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn("foreign:textures/entitys/osc.png");
        clinit.visitInsn(Opcodes.POP);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] model() {
        String owner = "foreign/osc/Model";
        String renderer = "net/minecraft/client/model/ModelRenderer";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/client/model/ModelBase", null);
        w.visitField(Opcodes.ACC_PRIVATE, "body", "L" + renderer + ";", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE, "arm", "L" + renderer + ";", null, null).visitEnd();

        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/client/model/ModelBase", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitIntInsn(Opcodes.BIPUSH, 64);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, owner, "field_78090_t", "I");
        ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitIntInsn(Opcodes.BIPUSH, 32);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, owner, "field_78089_u", "I");
        part(ctor, owner, "body", 0, 0, -3F, -6F, -3F, 6, 10, 6, 0F, -2F, 0F, false, 0F, 0F, (float) Math.PI);
        part(ctor, owner, "arm", 24, 0, -1F, -1F, -1F, 2, 7, 2, 4F, -1F, 0F, true, 0F, (float) Math.PI, 0F);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor normal = w.visitMethod(Opcodes.ACC_PUBLIC, "func_78088_a",
                "(Lnet/minecraft/entity/Entity;FFFFFF)V", null, null);
        normal.visitCode();
        normal.visitVarInsn(Opcodes.ALOAD, 0);
        normal.visitFieldInsn(Opcodes.GETFIELD, owner, "body", "L" + renderer + ";");
        normal.visitVarInsn(Opcodes.FLOAD, 7);
        normal.visitMethodInsn(Opcodes.INVOKEVIRTUAL, renderer, "func_78785_a", "(F)V", false);
        normal.visitInsn(Opcodes.RETURN);
        normal.visitMaxs(0, 0);
        normal.visitEnd();

        MethodVisitor joint = w.visitMethod(Opcodes.ACC_PUBLIC, "renderJoint", "(F)V", null, null);
        joint.visitCode();
        joint.visitVarInsn(Opcodes.ALOAD, 0);
        joint.visitFieldInsn(Opcodes.GETFIELD, owner, "arm", "L" + renderer + ";");
        joint.visitLdcInsn(Math.PI);
        joint.visitVarInsn(Opcodes.FLOAD, 1);
        joint.visitInsn(Opcodes.F2D);
        joint.visitInsn(Opcodes.DMUL);
        joint.visitLdcInsn(180D);
        joint.visitInsn(Opcodes.DDIV);
        joint.visitInsn(Opcodes.D2F);
        joint.visitFieldInsn(Opcodes.PUTFIELD, renderer, "field_78795_f", "F");
        joint.visitVarInsn(Opcodes.ALOAD, 0);
        joint.visitFieldInsn(Opcodes.GETFIELD, owner, "arm", "L" + renderer + ";");
        joint.visitLdcInsn(0.0625F);
        joint.visitMethodInsn(Opcodes.INVOKEVIRTUAL, renderer, "func_78785_a", "(F)V", false);
        joint.visitInsn(Opcodes.RETURN);
        joint.visitMaxs(0, 0);
        joint.visitEnd();

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PRIVATE, "setRotation",
                "(Lnet/minecraft/client/model/ModelRenderer;FFF)V", null, null);
        set.visitCode();
        set.visitVarInsn(Opcodes.ALOAD, 1); set.visitVarInsn(Opcodes.FLOAD, 2);
        set.visitFieldInsn(Opcodes.PUTFIELD, renderer, "field_78795_f", "F");
        set.visitVarInsn(Opcodes.ALOAD, 1); set.visitVarInsn(Opcodes.FLOAD, 3);
        set.visitFieldInsn(Opcodes.PUTFIELD, renderer, "field_78796_g", "F");
        set.visitVarInsn(Opcodes.ALOAD, 1); set.visitVarInsn(Opcodes.FLOAD, 4);
        set.visitFieldInsn(Opcodes.PUTFIELD, renderer, "field_78808_h", "F");
        set.visitInsn(Opcodes.RETURN);
        set.visitMaxs(0, 0);
        set.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void part(MethodVisitor m, String owner, String field, int u, int v,
                             float x, float y, float z, int width, int height, int depth,
                             float px, float py, float pz, boolean mirror,
                             float rx, float ry, float rz) {
        String renderer = "net/minecraft/client/model/ModelRenderer";
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitTypeInsn(Opcodes.NEW, renderer); m.visitInsn(Opcodes.DUP); m.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(m, u); pushInt(m, v);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, renderer, "<init>",
                "(Lnet/minecraft/client/model/ModelBase;II)V", false);
        m.visitFieldInsn(Opcodes.PUTFIELD, owner, field, "L" + renderer + ";");
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitFieldInsn(Opcodes.GETFIELD, owner, field, "L" + renderer + ";");
        pushFloat(m, x); pushFloat(m, y); pushFloat(m, z); pushInt(m, width); pushInt(m, height); pushInt(m, depth);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, renderer, "func_78789_a",
                "(FFFIII)Lnet/minecraft/client/model/ModelRenderer;", false); m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitFieldInsn(Opcodes.GETFIELD, owner, field, "L" + renderer + ";");
        pushFloat(m, px); pushFloat(m, py); pushFloat(m, pz);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, renderer, "func_78793_a", "(FFF)V", false);
        if (mirror) {
            m.visitVarInsn(Opcodes.ALOAD, 0); m.visitFieldInsn(Opcodes.GETFIELD, owner, field, "L" + renderer + ";");
            m.visitInsn(Opcodes.ICONST_1); m.visitFieldInsn(Opcodes.PUTFIELD, renderer, "field_78809_i", "Z");
        }
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, owner, field, "L" + renderer + ";");
        pushFloat(m, rx); pushFloat(m, ry); pushFloat(m, rz);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "setRotation",
                "(Lnet/minecraft/client/model/ModelRenderer;FFF)V", false);
    }

    private static void pushInt(MethodVisitor m, int value) {
        if (value >= 0 && value <= 5) m.visitInsn(Opcodes.ICONST_0 + value);
        else m.visitIntInsn(Opcodes.BIPUSH, value);
    }

    private static void pushFloat(MethodVisitor m, float value) {
        if (value == 0F) m.visitInsn(Opcodes.FCONST_0);
        else if (value == 1F) m.visitInsn(Opcodes.FCONST_1);
        else m.visitLdcInsn(value);
    }

    private static byte[] pngHeader(int width, int height) {
        byte[] bytes = new byte[24];
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        bytes[12] = 'I'; bytes[13] = 'H'; bytes[14] = 'D'; bytes[15] = 'R';
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(16, width).putInt(20, height);
        return bytes;
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
