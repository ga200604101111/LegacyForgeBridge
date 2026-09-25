package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectilePresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedArrowFamilyIsBoundFromRegistrationItemSpawnAndRendererStructure()throws Exception{
        Path jar=tempDir.resolve("foreign-projectile.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"mcmod.info","[{\"modid\":\"foreign\",\"name\":\"Foreign\",\"version\":\"1\",\"mcversion\":\"1.7.10\"}]".getBytes(StandardCharsets.UTF_8));
            put(out,"foreign/proj/Dart.class",dart());
            put(out,"foreign/proj/DartItem.class",item());
            put(out,"foreign/proj/Bootstrap.class",bootstrap());
            put(out,"foreign/proj/Client.class",client());
            put(out,"foreign/proj/DartRenderer.class",renderer());
            put(out,"assets/foreign/textures/entity/dart.png",new byte[]{1});
        }

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(jar);
        var rule=analysis.rules().stream().filter(value->value.registryName().equals("dart_entity")).findFirst()
                .orElseThrow(()->new AssertionError("Generic projectile proof missing; skipped="+analysis.skipped()+" diagnostics="+analysis.diagnostics()));
        assertEquals("foreign/proj/Dart",rule.sourceClass());
        assertEquals("foreign/proj/DartRenderer",rule.rendererClass());
        assertEquals(LegacyProjectilePresentationAnalyzer.BaseFamily.ARROW,rule.baseFamily());
        assertEquals(LegacyProjectilePresentationAnalyzer.Adapter.ORIENTED_ITEM,rule.adapter());
        assertEquals("foreign/proj/DartItem",rule.sourceItemClass());
        assertEquals("dart_item",rule.sourceItemRegistryName());
        assertEquals(44,rule.legacyNumericId());assertEquals(80,rule.trackingRange());assertEquals(3,rule.updateFrequency());
        assertTrue(rule.velocityUpdates());assertEquals(.4F,rule.width(),0.0001F);assertEquals(.4F,rule.height(),0.0001F);
        assertEquals("foreign:textures/entity/dart.png",rule.fixedTexture());
    }

    private static byte[] dart(){
        String n="foreign/proj/Dart";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/entity/projectile/EntityArrow",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;)V",null,null);
        c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/projectile/EntityArrow","<init>","(Lnet/minecraft/world/World;)V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitLdcInsn(.4F);c.visitLdcInsn(.4F);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"func_70105_a","(FF)V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] item(){
        String n="foreign/proj/DartItem",entity="foreign/proj/Dart";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/item/Item",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_77659_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",null,null);
        m.visitCode();m.visitTypeInsn(Opcodes.NEW,entity);m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,2);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,entity,"<init>","(Lnet/minecraft/world/World;)V",false);m.visitVarInsn(Opcodes.ASTORE,4);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitVarInsn(Opcodes.ALOAD,4);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72838_d","(Lnet/minecraft/entity/Entity;)Z",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/proj/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor a=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);a.visitEnd();m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/proj/Dart"));m.visitLdcInsn("dart_entity");m.visitIntInsn(Opcodes.BIPUSH,44);m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitIntInsn(Opcodes.BIPUSH,80);m.visitInsn(Opcodes.ICONST_3);m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity","(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        m.visitTypeInsn(Opcodes.NEW,"foreign/proj/DartItem");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/proj/DartItem","<init>","()V",false);
        m.visitLdcInsn("dart_item");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] client(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/proj/Client",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/proj/Dart"));m.visitTypeInsn(Opcodes.NEW,"foreign/proj/DartRenderer");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/proj/DartRenderer","<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerEntityRenderingHandler","(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] renderer(){
        String n="foreign/proj/DartRenderer";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/client/renderer/entity/Render",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/renderer/entity/Render","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor r=w.visitMethod(Opcodes.ACC_PUBLIC,"doRender","(Lforeign/proj/Dart;DDDFF)V",null,null);r.visitCode();
        r.visitLdcInsn(90F);r.visitInsn(Opcodes.FCONST_0);r.visitInsn(Opcodes.FCONST_1);r.visitInsn(Opcodes.FCONST_0);r.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glRotatef","(FFFF)V",false);
        r.visitInsn(Opcodes.FCONST_1);r.visitInsn(Opcodes.FCONST_0);r.visitInsn(Opcodes.FCONST_0);r.visitInsn(Opcodes.FCONST_1);r.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glRotatef","(FFFF)V",false);
        r.visitLdcInsn(.1F);r.visitLdcInsn(.1F);r.visitLdcInsn(.1F);r.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glScalef","(FFF)V",false);
        r.visitInsn(Opcodes.RETURN);r.visitMaxs(0,0);r.visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitLdcInsn("foreign:textures/entity/dart.png");s.visitInsn(Opcodes.POP);s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
