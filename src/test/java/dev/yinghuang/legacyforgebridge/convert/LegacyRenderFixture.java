package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Synthetic source bytecode: no legacy Minecraft classes need to load or execute. */
public final class LegacyRenderFixture implements Opcodes {
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String RENDERER = "net/minecraftforge/client/IItemRenderer";
    private static final String TYPE = RENDERER + "$ItemRenderType";
    private static final String HELPER = RENDERER + "$ItemRendererHelper";
    private static final String RESOURCE = "net/minecraft/util/ResourceLocation";
    private static final String MODEL = "net/minecraftforge/client/model/IModelCustom";

    private LegacyRenderFixture() { }
    public static Path create(Path jarPath, String namespace, boolean dynamic) throws IOException {
        String renderer = namespace + "/Renderer", client = namespace + "/Client";
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            add(jar, "mcmod.info", ("[{\"modid\":\"" + namespace + "\",\"name\":\"Fixture\",\"version\":\"1\",\"mcversion\":\"1.7.10\"}]").getBytes(StandardCharsets.UTF_8));
            add(jar, renderer + ".class", renderer(renderer, namespace, dynamic));
            add(jar, client + ".class", client(client, renderer, namespace));
        }
        return jarPath;
    }
    private static void add(JarOutputStream jar, String path, byte[] bytes) throws IOException {
        jar.putNextEntry(new JarEntry(path)); jar.write(bytes); jar.closeEntry();
    }
    private static ClassWriter writer(String name, String[] interfaces) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V1_7, ACC_PUBLIC, name, null, "java/lang/Object", interfaces); return w;
    }
    private static MethodVisitor method(ClassWriter w, int access, String name, String desc) {
        MethodVisitor m = w.visitMethod(access, name, desc, null, null); m.visitCode(); return m;
    }
    private static void end(MethodVisitor m) { m.visitMaxs(0, 0); m.visitEnd(); }
    private static void field(ClassWriter w, int access, String name, String desc) {
        w.visitField(access, name, desc, null, null).visitEnd();
    }
    private static byte[] client(String client, String renderer, String namespace) {
        ClassWriter w = writer(client, null);
        field(w, ACC_PUBLIC | ACC_STATIC, "TOOL", "L" + ITEM + ";");
        MethodVisitor c=method(w,ACC_STATIC,"<clinit>","()V");
        c.visitTypeInsn(NEW,ITEM);c.visitInsn(DUP);c.visitMethodInsn(INVOKESPECIAL,ITEM,"<init>","()V",false);
        c.visitLdcInsn("tool");c.visitMethodInsn(INVOKEVIRTUAL,ITEM,"setUnlocalizedName","(Ljava/lang/String;)L"+ITEM+";",false);
        c.visitFieldInsn(PUTSTATIC,client,"TOOL","L"+ITEM+";");c.visitInsn(RETURN);end(c);
        MethodVisitor p=method(w,ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V");
        AnnotationVisitor av=p.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();
        p.visitFieldInsn(GETSTATIC,client,"TOOL","L"+ITEM+";");p.visitLdcInsn("tool");
        p.visitMethodInsn(INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(L"+ITEM+";Ljava/lang/String;)V",false);p.visitInsn(RETURN);end(p);
        MethodVisitor m = method(w, ACC_PUBLIC | ACC_STATIC, "install", "()V");
        m.visitFieldInsn(GETSTATIC, client, "TOOL", "L" + ITEM + ";");
        m.visitTypeInsn(NEW, renderer); m.visitInsn(DUP); m.visitLdcInsn(namespace + ":textures/tool.png");
        m.visitMethodInsn(INVOKESPECIAL, renderer, "<init>", "(Ljava/lang/String;)V", false);
        m.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/client/MinecraftForgeClient", "registerItemRenderer",
                "(L" + ITEM + ";L" + RENDERER + ";)V", false);
        m.visitInsn(RETURN); end(m); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] renderer(String name, String namespace, boolean dynamic) {
        ClassWriter w = writer(name, new String[]{RENDERER});
        field(w, ACC_PUBLIC | ACC_STATIC, "MESH", "L" + MODEL + ";");
        field(w, ACC_PRIVATE, "texture", "L" + RESOURCE + ";");
        MethodVisitor m = method(w, ACC_STATIC, "<clinit>", "()V");
        m.visitTypeInsn(NEW, RESOURCE); m.visitInsn(DUP); m.visitLdcInsn(namespace + ":models/tool.obj");
        m.visitMethodInsn(INVOKESPECIAL, RESOURCE, "<init>", "(Ljava/lang/String;)V", false);
        m.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/client/model/AdvancedModelLoader", "loadModel", "(L" + RESOURCE + ";)L" + MODEL + ";", false);
        m.visitFieldInsn(PUTSTATIC, name, "MESH", "L" + MODEL + ";"); m.visitInsn(RETURN); end(m);
        m = method(w, ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V");
        m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(NEW, RESOURCE); m.visitInsn(DUP); m.visitVarInsn(ALOAD, 1);
        m.visitMethodInsn(INVOKESPECIAL, RESOURCE, "<init>", "(Ljava/lang/String;)V", false);
        m.visitFieldInsn(PUTFIELD, name, "texture", "L" + RESOURCE + ";"); m.visitInsn(RETURN); end(m);
        m = method(w, ACC_PUBLIC, "handleRenderType", "(" + STACK + "L" + TYPE + ";)Z");
        Label no = new Label();
        m.visitVarInsn(ALOAD, 2); m.visitFieldInsn(GETSTATIC, TYPE, "INVENTORY", "L" + TYPE + ";"); m.visitJumpInsn(IF_ACMPEQ, no);
        m.visitInsn(ICONST_1); m.visitInsn(IRETURN); m.visitLabel(no); m.visitInsn(ICONST_0); m.visitInsn(IRETURN); end(m);
        m = method(w, ACC_PUBLIC, "shouldUseRenderHelper", "(L" + TYPE + ";" + STACK + "L" + HELPER + ";)Z");
        no = new Label(); Label yes = new Label();
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(GETSTATIC, TYPE, "ENTITY", "L" + TYPE + ";"); m.visitJumpInsn(IF_ACMPNE, no);
        m.visitVarInsn(ALOAD, 3); m.visitFieldInsn(GETSTATIC, HELPER, "ENTITY_BOBBING", "L" + HELPER + ";"); m.visitJumpInsn(IF_ACMPEQ, yes);
        m.visitVarInsn(ALOAD, 3); m.visitFieldInsn(GETSTATIC, HELPER, "ENTITY_ROTATION", "L" + HELPER + ";"); m.visitJumpInsn(IF_ACMPNE, no);
        m.visitLabel(yes); m.visitInsn(ICONST_1); m.visitInsn(IRETURN); m.visitLabel(no); m.visitInsn(ICONST_0); m.visitInsn(IRETURN); end(m);
        m = method(w, ACC_PUBLIC, "renderItem", "(L" + TYPE + ";" + STACK + "[Ljava/lang/Object;)V");
        if (dynamic) {
            Label notNull = new Label(); m.visitVarInsn(ALOAD, 2); m.visitJumpInsn(IFNONNULL, notNull);
            m.visitInsn(RETURN); m.visitLabel(notNull);
        }
        m.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPushMatrix", "()V", false);
        m.visitMethodInsn(INVOKESTATIC, "net/minecraft/client/Minecraft", "func_71410_x", "()Lnet/minecraft/client/Minecraft;", false);
        m.visitFieldInsn(GETFIELD, "net/minecraft/client/Minecraft", "renderEngine", "Lnet/minecraft/client/renderer/texture/TextureManager;");
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, name, "texture", "L" + RESOURCE + ";");
        m.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/client/renderer/texture/TextureManager", "func_110577_a", "(L" + RESOURCE + ";)V", false);
        Label normal = new Label(), apply = new Label();
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(GETSTATIC, TYPE, "EQUIPPED_FIRST_PERSON", "L" + TYPE + ";"); m.visitJumpInsn(IF_ACMPNE, normal);
        m.visitInsn(ICONST_3); m.visitInsn(ICONST_2); m.visitInsn(IDIV); m.visitInsn(I2F); m.visitJumpInsn(GOTO, apply);
        m.visitLabel(normal); m.visitLdcInsn(.25F); m.visitLabel(apply);
        m.visitLdcInsn(.5F); m.visitInsn(FCONST_0); m.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTranslatef", "(FFF)V", false);
        for (int i = 0; i < 3; i++) m.visitLdcInsn(.4F);
        m.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glScalef", "(FFF)V", false);
        m.visitLdcInsn(45F); m.visitInsn(FCONST_0); m.visitInsn(FCONST_1); m.visitInsn(FCONST_0);
        m.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glRotatef", "(FFFF)V", false);
        m.visitFieldInsn(GETSTATIC, name, "MESH", "L" + MODEL + ";");
        m.visitMethodInsn(INVOKEINTERFACE, MODEL, "renderAll", "()V", true);
        m.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false);
        m.visitInsn(RETURN); end(m); w.visitEnd(); return w.toByteArray();
    }
}
