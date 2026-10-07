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

    @Test
    void arrowRenderSnowballMayUseDistinctRegisteredCarrierAndReleaseUseLauncher()throws Exception{
        Path jar=tempDir.resolve("foreign-snowball-arrow.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"mcmod.info","[{\"modid\":\"foreign\",\"name\":\"Foreign\",\"version\":\"1\",\"mcversion\":\"1.7.10\"}]".getBytes(StandardCharsets.UTF_8));
            put(out,"foreign/snow/Pellet.class",pellet());
            put(out,"foreign/snow/BaseLauncher.class",baseLauncher());
            put(out,"foreign/snow/Launcher.class",launcher());
            put(out,"foreign/snow/Carrier.class",carrier());
            put(out,"foreign/snow/Bootstrap.class",snowballBootstrap());
            put(out,"foreign/snow/Client.class",snowballClient());
        }

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(jar);
        var rule=analysis.rules().stream().filter(value->value.registryName().equals("pellet_entity")).findFirst()
                .orElseThrow(()->new AssertionError("RenderSnowball arrow proof missing; skipped="+analysis.skipped()+" diagnostics="+analysis.diagnostics()));
        assertEquals(LegacyProjectilePresentationAnalyzer.BaseFamily.ARROW,rule.baseFamily());
        assertEquals(LegacyProjectilePresentationAnalyzer.Adapter.THROWN_ITEM,rule.adapter());
        assertEquals("foreign/snow/Carrier",rule.sourceItemClass());
        assertEquals("carrier",rule.sourceItemRegistryName(),
                "Presentation must follow the renderer carrier, not the launcher item");
        assertEquals(3,rule.defaultItemMetadata(),
                "RenderSnowball(Item, metadata) must preserve the constant presentation metadata");
        assertNull(rule.fixedTexture());
    }

    @Test
    void vanilla1710RenderSnowballItemFieldIsAPlatformPresentationIdentity()throws Exception{
        Path jar=tempDir.resolve("foreign-vanilla-snowball.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"mcmod.info","[{\"modid\":\"foreign\",\"name\":\"Foreign\",\"version\":\"1\",\"mcversion\":\"1.7.10\"}]".getBytes(StandardCharsets.UTF_8));
            put(out,"foreign/vanilla/Orb.class",vanillaSnowballThrowable());
            put(out,"foreign/vanilla/Bootstrap.class",vanillaSnowballBootstrap());
            put(out,"foreign/vanilla/Client.class",vanillaSnowballClient());
        }

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(jar);
        var rule=analysis.rules().stream().filter(value->value.registryName().equals("vanilla_orb")).findFirst()
                .orElseThrow(()->new AssertionError("Vanilla RenderSnowball proof missing; skipped="+analysis.skipped()+" diagnostics="+analysis.diagnostics()));
        assertEquals(LegacyProjectilePresentationAnalyzer.BaseFamily.THROWABLE,rule.baseFamily());
        assertEquals(LegacyProjectilePresentationAnalyzer.Adapter.THROWN_ITEM,rule.adapter());
        assertEquals(LegacyVanillaRegistry1710.ITEMS_OWNER,rule.sourceItemClass());
        assertEquals("snowball",rule.sourceItemRegistryName());
        assertEquals(0,rule.defaultItemMetadata());
        assertNull(rule.fixedTexture());
    }

    @Test
    void copiedArrowWireRenderSnowballDoesNotRequireUniqueLauncher()throws Exception{
        Path jar=tempDir.resolve("foreign-copied-arrow.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"mcmod.info","[{\"modid\":\"foreign\",\"name\":\"Foreign\",\"version\":\"1\",\"mcversion\":\"1.7.10\"}]".getBytes(StandardCharsets.UTF_8));
            put(out,"foreign/reach/Reach.class",copiedReach());
            put(out,"foreign/reach/ReachLauncher.class",copiedReachLauncher());
            put(out,"foreign/reach/Carrier.class",copiedReachCarrier());
            put(out,"foreign/reach/Bootstrap.class",copiedReachBootstrap());
            put(out,"foreign/reach/Client.class",copiedReachClient());
        }

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(jar);
        var rule=analysis.rules().stream().filter(value->value.registryName().equals("reach_entity")).findFirst()
                .orElseThrow(()->new AssertionError("Copied-arrow RenderSnowball proof missing; skipped="+analysis.skipped()+" diagnostics="+analysis.diagnostics()));
        assertEquals(LegacyProjectilePresentationAnalyzer.BaseFamily.ARROW,rule.baseFamily());
        assertEquals(LegacyProjectilePresentationAnalyzer.Adapter.THROWN_ITEM,rule.adapter());
        assertEquals("foreign/reach/Carrier",rule.sourceItemClass());
        assertEquals("reach_carrier",rule.sourceItemRegistryName());
        assertTrue(rule.proof().contains("IProjectile + DataWatcher16 byte wire proof"));
        assertEquals(.5F,rule.width(),0.0001F);assertEquals(.5F,rule.height(),0.0001F);
    }

    private static byte[] vanillaSnowballThrowable(){
        String n="foreign/vanilla/Orb";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/entity/projectile/EntityThrowable",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;)V",null,null);
        c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/projectile/EntityThrowable","<init>","(Lnet/minecraft/world/World;)V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] vanillaSnowballBootstrap(){
        String n="foreign/vanilla/Bootstrap";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor a=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);a.visitEnd();m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/vanilla/Orb"));m.visitLdcInsn("vanilla_orb");
        m.visitIntInsn(Opcodes.BIPUSH,47);m.visitVarInsn(Opcodes.ALOAD,0);m.visitIntInsn(Opcodes.BIPUSH,64);
        m.visitInsn(Opcodes.ICONST_2);m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] vanillaSnowballClient(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/vanilla/Client",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/vanilla/Orb"));
        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/client/renderer/entity/RenderSnowball");m.visitInsn(Opcodes.DUP);
        m.visitFieldInsn(Opcodes.GETSTATIC,LegacyVanillaRegistry1710.ITEMS_OWNER,"field_151126_ay","Lnet/minecraft/item/Item;");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/renderer/entity/RenderSnowball","<init>",
                "(Lnet/minecraft/item/Item;)V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerEntityRenderingHandler",
                "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] pellet(){
        String n="foreign/snow/Pellet";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/entity/projectile/EntityArrow",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;)V",null,null);
        c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/projectile/EntityArrow","<init>","(Lnet/minecraft/world/World;)V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] baseLauncher(){
        String n="foreign/snow/BaseLauncher",entity="foreign/snow/Pellet";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/item/Item",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_77615_a",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;I)V",null,null);
        m.visitCode();m.visitTypeInsn(Opcodes.NEW,entity);m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,2);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,entity,"<init>","(Lnet/minecraft/world/World;)V",false);m.visitVarInsn(Opcodes.ASTORE,5);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitVarInsn(Opcodes.ALOAD,5);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72838_d","(Lnet/minecraft/entity/Entity;)Z",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] launcher(){
        String n="foreign/snow/Launcher";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"foreign/snow/BaseLauncher",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/snow/BaseLauncher","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitLdcInsn("launcher");
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"setUnlocalizedName","(Ljava/lang/String;)Lnet/minecraft/item/Item;",false);c.visitInsn(Opcodes.POP);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] carrier(){
        String n="foreign/snow/Carrier";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/item/Item",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitLdcInsn("carrier");
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"setUnlocalizedName","(Ljava/lang/String;)Lnet/minecraft/item/Item;",false);c.visitInsn(Opcodes.POP);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] snowballBootstrap(){
        String n="foreign/snow/Bootstrap";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"LAUNCHER","Lforeign/snow/Launcher;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"CARRIER","Lforeign/snow/Carrier;",null,null).visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();
        s.visitTypeInsn(Opcodes.NEW,"foreign/snow/Launcher");s.visitInsn(Opcodes.DUP);
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/snow/Launcher","<init>","()V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,n,"LAUNCHER","Lforeign/snow/Launcher;");
        s.visitTypeInsn(Opcodes.NEW,"foreign/snow/Carrier");s.visitInsn(Opcodes.DUP);
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/snow/Carrier","<init>","()V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,n,"CARRIER","Lforeign/snow/Carrier;");
        s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor a=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);a.visitEnd();m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/snow/Pellet"));m.visitLdcInsn("pellet_entity");m.visitIntInsn(Opcodes.BIPUSH,45);m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitIntInsn(Opcodes.BIPUSH,64);m.visitInsn(Opcodes.ICONST_2);m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,n,"LAUNCHER","Lforeign/snow/Launcher;");m.visitLdcInsn("launcher");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,n,"CARRIER","Lforeign/snow/Carrier;");m.visitLdcInsn("carrier");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] snowballClient(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/snow/Client",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/snow/Pellet"));
        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/client/renderer/entity/RenderSnowball");m.visitInsn(Opcodes.DUP);
        m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/snow/Bootstrap","CARRIER","Lforeign/snow/Carrier;");
        m.visitInsn(Opcodes.ICONST_3);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/renderer/entity/RenderSnowball","<init>",
                "(Lnet/minecraft/item/Item;I)V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerEntityRenderingHandler",
                "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
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

    private static byte[] copiedReach(){
        String n="foreign/reach/Reach";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/entity/Entity",new String[]{"net/minecraft/entity/IProjectile"});
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/Entity","<init>","(Lnet/minecraft/world/World;)V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitLdcInsn(.5F);c.visitLdcInsn(.5F);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"func_70105_a","(FF)V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PROTECTED,"func_70088_a","()V",null,null);init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70180_af","Lnet/minecraft/entity/DataWatcher;");
        init.visitIntInsn(Opcodes.BIPUSH,16);init.visitInsn(Opcodes.ICONST_0);
        init.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Byte","valueOf","(B)Ljava/lang/Byte;",false);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/DataWatcher","func_75682_a","(ILjava/lang/Object;)V",false);
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
        MethodVisitor projectile=w.visitMethod(Opcodes.ACC_PUBLIC,"func_70186_c","(DDDFF)V",null,null);
        projectile.visitCode();projectile.visitInsn(Opcodes.RETURN);projectile.visitMaxs(0,0);projectile.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] copiedReachLauncher(){
        String n="foreign/reach/ReachLauncher",entity="foreign/reach/Reach";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/item/Item",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_77659_a",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",null,null);
        m.visitCode();m.visitTypeInsn(Opcodes.NEW,entity);m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,2);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,entity,"<init>","(Lnet/minecraft/world/World;)V",false);m.visitVarInsn(Opcodes.ASTORE,4);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitVarInsn(Opcodes.ALOAD,4);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72838_d","(Lnet/minecraft/entity/Entity;)Z",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] copiedReachCarrier(){
        String n="foreign/reach/Carrier";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/item/Item",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] copiedReachBootstrap(){
        String n="foreign/reach/Bootstrap";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"A","Lforeign/reach/ReachLauncher;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"B","Lforeign/reach/ReachLauncher;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"CARRIER","Lforeign/reach/Carrier;",null,null).visitEnd();
        MethodVisitor cl=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);cl.visitCode();
        for(String field:new String[]{"A","B"}){
            cl.visitTypeInsn(Opcodes.NEW,"foreign/reach/ReachLauncher");cl.visitInsn(Opcodes.DUP);
            cl.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/reach/ReachLauncher","<init>","()V",false);
            cl.visitFieldInsn(Opcodes.PUTSTATIC,n,field,"Lforeign/reach/ReachLauncher;");
        }
        cl.visitTypeInsn(Opcodes.NEW,"foreign/reach/Carrier");cl.visitInsn(Opcodes.DUP);
        cl.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/reach/Carrier","<init>","()V",false);
        cl.visitFieldInsn(Opcodes.PUTSTATIC,n,"CARRIER","Lforeign/reach/Carrier;");
        cl.visitInsn(Opcodes.RETURN);cl.visitMaxs(0,0);cl.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor a=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);a.visitEnd();m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/reach/Reach"));m.visitLdcInsn("reach_entity");m.visitIntInsn(Opcodes.BIPUSH,46);m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitIntInsn(Opcodes.BIPUSH,64);m.visitInsn(Opcodes.ICONST_2);m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,n,"A","Lforeign/reach/ReachLauncher;");m.visitLdcInsn("reach_a");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,n,"B","Lforeign/reach/ReachLauncher;");m.visitLdcInsn("reach_b");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,n,"CARRIER","Lforeign/reach/Carrier;");m.visitLdcInsn("reach_carrier");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] copiedReachClient(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/reach/Client",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/reach/Reach"));
        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/client/renderer/entity/RenderSnowball");m.visitInsn(Opcodes.DUP);
        m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/reach/Bootstrap","CARRIER","Lforeign/reach/Carrier;");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/renderer/entity/RenderSnowball","<init>",
                "(Lnet/minecraft/item/Item;)V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerEntityRenderingHandler",
                "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
