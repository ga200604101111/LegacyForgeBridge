package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Renamed, synthetic 1.7.10 renderer/model bytecode: no Twilight Forest production selector. */
final class LegacyFixedModelProjectileFixture {
    static final String RENDERER = "foreign/visual/StaticProjectileRenderer";
    private static final String MODEL = "foreign/visual/CuboidModel";
    private static final String PART = "net/minecraft/client/model/ModelRenderer";
    private static final String MODELBASE = "net/minecraft/client/model/ModelBase";
    private static final String RESOURCE = "net/minecraft/util/ResourceLocation";
    private static final String TEXTURE = "foreign:textures/model/static.png";
    private static final String GL = "org/lwjgl/opengl/GL11";
    private static final String[] NAMES = { "pieceA", "pieceB", "pieceC", "pieceD" };
    private static final int[][] UV = {{0, 4}, {0, 8}, {0, 14}, {0, 0}};
    private static final int[][] DIMS = {{4, 2, 2}, {2, 2, 4}, {2, 2, 2}, {2, 2, 2}};
    private static final float[][] PIVOTS = {{-1,7,3}, {3,7,0}, {2,7,-2}, {-3,7,2}};
    private LegacyFixedModelProjectileFixture() { }

    static Path jar(Path target, boolean unreachableAnimation, boolean renderMutation,
                    boolean dynamicRotation, boolean extraRenderCall,
                    boolean partCtorRotation, boolean omitTexture) throws IOException {
        try (JarOutputStream stream = new JarOutputStream(Files.newOutputStream(target))) {
            put(stream, RENDERER + ".class", renderer(dynamicRotation, extraRenderCall));
            put(stream, MODEL + ".class", model(unreachableAnimation, renderMutation, partCtorRotation));
            if (!omitTexture) put(stream, "assets/foreign/textures/model/static.png", pngHeader());
        }
        return target;
    }

    private static byte[] model(boolean unreachableAnimation, boolean renderMutation, boolean partCtorRotation) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, MODEL, null, MODELBASE, null);
        for (String part : NAMES) classWriter.visitField(Opcodes.ACC_PRIVATE, part,
                "L" + PART + ";", null, null).visitEnd();
        MethodVisitor ctor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, MODELBASE, "<init>", "()V", false);
        for (String size : new String[]{"textureWidth", "textureHeight"}) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            integer(ctor, 32);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, MODEL, size, "I");
        }
        for (int i = 0; i < NAMES.length; i++) {
            String part = NAMES[i];
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitTypeInsn(Opcodes.NEW, PART);
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            integer(ctor, UV[i][0]); integer(ctor, UV[i][1]);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, PART, "<init>",
                    "(L" + MODELBASE + ";II)V", false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, MODEL, part, "L" + PART + ";");
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, MODEL, part, "L" + PART + ";");
            ctor.visitLdcInsn(-1.0F); ctor.visitLdcInsn(-1.0F); ctor.visitLdcInsn(-1.0F);
            for (int dimension : DIMS[i]) integer(ctor, dimension);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PART, "addBox", "(FFFIII)V", false);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, MODEL, part, "L" + PART + ";");
            for (float axis : PIVOTS[i]) ctor.visitLdcInsn(axis);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PART, "setRotationPoint", "(FFF)V", false);
            if (partCtorRotation && i == 0) {
                ctor.visitVarInsn(Opcodes.ALOAD, 0);
                ctor.visitFieldInsn(Opcodes.GETFIELD, MODEL, part, "L" + PART + ";");
                ctor.visitLdcInsn(0.5F);
                ctor.visitFieldInsn(Opcodes.PUTFIELD, PART, "rotateAngleX", "F");
            }
        }
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();

        MethodVisitor render = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "render", "(F)V", null, null);
        render.visitCode();
        for (String part : NAMES) {
            render.visitVarInsn(Opcodes.ALOAD, 0);
            render.visitFieldInsn(Opcodes.GETFIELD, MODEL, part, "L" + PART + ";");
            render.visitVarInsn(Opcodes.FLOAD, 1);
            render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PART, "render", "(F)V", false);
        }
        if (renderMutation) {
            render.visitVarInsn(Opcodes.ALOAD, 0);
            render.visitFieldInsn(Opcodes.GETFIELD, MODEL, NAMES[0], "L" + PART + ";");
            render.visitLdcInsn(0.5F);
            render.visitFieldInsn(Opcodes.PUTFIELD, PART, "rotationPointY", "F");
        }
        render.visitInsn(Opcodes.RETURN); render.visitMaxs(0, 0); render.visitEnd();
        if (unreachableAnimation) {
            MethodVisitor alternate = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "animateTileOnly", "()V", null, null);
            alternate.visitCode(); alternate.visitVarInsn(Opcodes.ALOAD, 0);
            alternate.visitFieldInsn(Opcodes.GETFIELD, MODEL, NAMES[0], "L" + PART + ";");
            alternate.visitLdcInsn(0.5F);
            alternate.visitFieldInsn(Opcodes.PUTFIELD, PART, "rotationPointY", "F");
            alternate.visitInsn(Opcodes.RETURN); alternate.visitMaxs(0, 0); alternate.visitEnd();
        }
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] renderer(boolean dynamicRotation, boolean extraRenderCall) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, RENDERER, null,
                "net/minecraft/client/renderer/entity/Render", null);
        classWriter.visitField(Opcodes.ACC_PRIVATE, "model", "L" + MODEL + ";", null, null).visitEnd();
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "texture", "L" + RESOURCE + ";", null, null).visitEnd();
        MethodVisitor ctor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/client/renderer/entity/Render",
                "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitTypeInsn(Opcodes.NEW, MODEL);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, MODEL, "<init>", "()V", false);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, RENDERER, "model", "L" + MODEL + ";");
        ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitLdcInsn(0.5F);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, RENDERER, "shadowSize", "F");
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        MethodVisitor init = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitCode(); init.visitTypeInsn(Opcodes.NEW, RESOURCE); init.visitInsn(Opcodes.DUP);
        init.visitLdcInsn(TEXTURE);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, RESOURCE, "<init>", "(Ljava/lang/String;)V", false);
        init.visitFieldInsn(Opcodes.PUTSTATIC, RENDERER, "texture", "L" + RESOURCE + ";");
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();

        MethodVisitor draw = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "doRender",
                "(Lnet/minecraft/entity/Entity;DDDFF)V", null, null);
        draw.visitCode();
        draw.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "glPushMatrix", "()V", false);
        for (int local : new int[]{2, 4, 6}) {
            draw.visitVarInsn(Opcodes.DLOAD, local); draw.visitInsn(Opcodes.D2F);
        }
        draw.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "glTranslatef", "(FFF)V", false);
        if (dynamicRotation) draw.visitVarInsn(Opcodes.FLOAD, 8);
        else draw.visitLdcInsn(90.0F);
        draw.visitInsn(Opcodes.FCONST_1); draw.visitInsn(Opcodes.FCONST_0); draw.visitInsn(Opcodes.FCONST_1);
        draw.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "glRotatef", "(FFFF)V", false);
        if (extraRenderCall) {
            draw.visitIntInsn(Opcodes.SIPUSH, 3042);
            draw.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "glEnable", "(I)V", false);
        }
        draw.visitVarInsn(Opcodes.ALOAD, 0);
        draw.visitFieldInsn(Opcodes.GETSTATIC, RENDERER, "texture", "L" + RESOURCE + ";");
        draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, RENDERER, "bindTexture", "(L" + RESOURCE + ";)V", false);
        draw.visitVarInsn(Opcodes.ALOAD, 0);
        draw.visitFieldInsn(Opcodes.GETFIELD, RENDERER, "model", "L" + MODEL + ";");
        draw.visitLdcInsn(0.075F);
        draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MODEL, "render", "(F)V", false);
        draw.visitMethodInsn(Opcodes.INVOKESTATIC, GL, "glPopMatrix", "()V", false);
        draw.visitInsn(Opcodes.RETURN); draw.visitMaxs(0, 0); draw.visitEnd();
        MethodVisitor get = classWriter.visitMethod(Opcodes.ACC_PROTECTED, "getEntityTexture",
                "(Lnet/minecraft/entity/Entity;)L" + RESOURCE + ";", null, null);
        get.visitCode();
        get.visitFieldInsn(Opcodes.GETSTATIC, RENDERER, "texture", "L" + RESOURCE + ";");
        get.visitInsn(Opcodes.ARETURN); get.visitMaxs(0, 0); get.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void integer(MethodVisitor visitor, int value) {
        if (value >= 0 && value <= 5) visitor.visitInsn(Opcodes.ICONST_0 + value);
        else visitor.visitIntInsn(Opcodes.BIPUSH, value);
    }
    private static byte[] pngHeader() {
        byte[] header = new byte[24];
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        System.arraycopy(signature, 0, header, 0, signature.length);
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putInt(16, 32).putInt(20, 32);
        return header;
    }
    private static void put(JarOutputStream stream, String name, byte[] content) throws IOException {
        stream.putNextEntry(new JarEntry(name)); stream.write(content); stream.closeEntry();
    }
}
