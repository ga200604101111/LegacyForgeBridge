package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyConnectedCuboidRendererAnalyzerTest {
    @TempDir Path tempDir;
    private static final String IFACE="foreign/beam/IConnected";
    private static final String IDS="foreign/beam/Ids";
    private static final String BEAM="foreign/beam/Beam";
    private static final String RENDERER="foreign/beam/Renderer";

    @Test
    void unrelatedConnectedCuboidFamilyIsRecoveredStructurally() throws Exception {
        Path jar=tempDir.resolve("foreign-beam.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,IFACE+".class",iface());
            put(out,IDS+".class",ids());
            put(out,BEAM+".class",beam());
            put(out,RENDERER+".class",renderer());
            put(out,"foreign/beam/Bootstrap.class",bootstrap());
        }
        var analysis=new LegacyConnectedCuboidRendererAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        assertEquals(1,analysis.rules().size());
        var rule=analysis.rules().getFirst();
        assertEquals("beam",rule.registryName());
        assertEquals(.15,rule.minWidth(),1e-6);assertEquals(.85,rule.maxWidth(),1e-6);
        assertEquals(.2,rule.minHeight(),1e-6);assertEquals(.8,rule.maxHeight(),1e-6);
        assertFalse(rule.axisLocked());assertFalse(rule.sameMetadataOnly());
        assertTrue(rule.connectFullBlocks());assertEquals("full",rule.collision());
        assertEquals("self",rule.materialSource().mode());
    }

    private static byte[] iface(){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_INTERFACE,IFACE,null,"java/lang/Object",null);
        String set="(Lnet/minecraft/world/IBlockAccess;IIIZ)Z";
        for(int i=0;i<6;i++)w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"dir"+i,set,null,null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"link","(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;)Z",null,null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"core","(Lnet/minecraft/world/IBlockAccess;III)V",null,null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"skip","()Z",null,null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"different","(II)Z",null,null).visitEnd();
        for(String n:new String[]{"size","minW","maxW","minH","maxH"})w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,n,"()F",null,null).visitEnd();
        w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"side","(I)V",null,null).visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] ids(){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,IDS,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"connected","I",null,null).visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] beam(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,BEAM,null,"net/minecraft/block/Block",new String[]{IFACE});
        for(String n:new String[]{"minW","maxW","minH","maxH"})w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,n,"F",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(FFFF)V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/Block","<init>","()V",false);
        for(int i=0;i<4;i++){m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.FLOAD,1+i);m.visitFieldInsn(Opcodes.PUTFIELD,BEAM,new String[]{"minW","maxW","minH","maxH"}[i],"F");}
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getRenderType","()I",null,null);m.visitCode();m.visitFieldInsn(Opcodes.GETSTATIC,IDS,"connected","I");m.visitInsn(Opcodes.IRETURN);m.visitMaxs(1,1);m.visitEnd();
        String set="(Lnet/minecraft/world/IBlockAccess;IIIZ)Z";
        for(int i=0;i<6;i++){m=w.visitMethod(Opcodes.ACC_PUBLIC,"dir"+i,set,null,null);m.visitCode();m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(1,6);m.visitEnd();}
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"link","(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;)Z",null,null);m.visitCode();
        m.visitInsn(Opcodes.ACONST_NULL);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149721_r","()Z",false);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(1,6);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"core","(Lnet/minecraft/world/IBlockAccess;III)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,5);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"skip","()Z",null,null);m.visitCode();m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(1,1);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"different","(II)Z",null,null);m.visitCode();m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(1,3);m.visitEnd();
        for(int i=0;i<5;i++){m=w.visitMethod(Opcodes.ACC_PUBLIC,new String[]{"size","minW","maxW","minH","maxH"}[i],"()F",null,null);m.visitCode();m.visitLdcInsn(i==0?.7F:new float[]{0,.15F,.85F,.2F,.8F}[i]);m.visitInsn(Opcodes.FRETURN);m.visitMaxs(1,1);m.visitEnd();}
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"side","(I)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"registerBlockIcons","(Lnet/minecraft/client/renderer/texture/IIconRegister;)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getCollisionBoundingBoxFromPool","(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;",null,null);m.visitCode();
        for(int local=2;local<=4;local++){m.visitVarInsn(Opcodes.ILOAD,local);m.visitInsn(Opcodes.I2D);}
        for(int local=2;local<=4;local++){m.visitVarInsn(Opcodes.ILOAD,local);m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IADD);m.visitInsn(Opcodes.I2D);}
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/AxisAlignedBB","func_72330_a","(DDDDDD)Lnet/minecraft/util/AxisAlignedBB;",false);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,5);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] renderer(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,RENDERER,null,"java/lang/Object",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(1,1);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(Lforeign/beam/IConnected;Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;Lnet/minecraftforge/common/util/ForgeDirection;)V",null,null);m.visitCode();
        String set="(Lnet/minecraft/world/IBlockAccess;IIIZ)Z";
        for(int i=0;i<6;i++){m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ALOAD,4);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"dir"+i,set,true);m.visitInsn(Opcodes.POP);}
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ALOAD,4);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ALOAD,5);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"link","(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;)Z",true);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ALOAD,4);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"core","(Lnet/minecraft/world/IBlockAccess;III)V",true);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"skip","()Z",true);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_1);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"different","(II)Z",true);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"size","()F",true);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,IFACE,"minW","()F",true);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitVarInsn(Opcodes.ALOAD,3);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/client/renderer/RenderBlocks","func_147784_q","(Lnet/minecraft/block/Block;III)Z",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,6);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/beam/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,BEAM);m.visitInsn(Opcodes.DUP);m.visitLdcInsn(.15F);m.visitLdcInsn(.85F);m.visitLdcInsn(.2F);m.visitLdcInsn(.8F);m.visitMethodInsn(Opcodes.INVOKESPECIAL,BEAM,"<init>","(FFFF)V",false);m.visitLdcInsn("beam");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
        m.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);m.visitVarInsn(Opcodes.ASTORE,2);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitFieldInsn(Opcodes.GETSTATIC,IDS,"connected","I");m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);
        m.visitTypeInsn(Opcodes.NEW,RENDERER);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDERER,"<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Map","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",true);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,3);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
