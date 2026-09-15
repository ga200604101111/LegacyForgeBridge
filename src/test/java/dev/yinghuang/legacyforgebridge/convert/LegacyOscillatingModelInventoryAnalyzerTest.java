package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyOscillatingModelInventoryAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedRenderIdMapWrapperAndRenderInvChainAreProven() throws Exception {
        Path jar=tempDir.resolve("inventory-chain.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/inv/Block.class",block());
            put(out,"foreign/inv/Handler.class",handler());
            put(out,"foreign/inv/Route.class",route());
            put(out,"foreign/inv/Wrapper.class",wrapper());
            put(out,"foreign/inv/WorldRenderer.class",worldRenderer());
        }
        var rule=new LegacyOscillatingModelBlockAnalyzer.Rule("thing",null,"foreign/inv/Block","foreign/inv/Tile","ServerTile",new int[]{0,1,2,3},true,0,true,true,1,"swing","dir",90,.7F,0F,45F,true,false);
        var part=new LegacyOscillatingModelPresentationAnalyzer.Part("arm",0,0,-1,-1,-1,2,7,2,4,-1,0,true,0,(float)Math.PI,0,true);
        var presentation=new LegacyOscillatingModelPresentationAnalyzer.Presentation("foreign/inv/WorldRenderer","ClientTile","foreign/inv/Model","foreign:textures/entitys/x.png",64,32,64,32,List.of(part),"arm",.0625F,.5F,.5F,.5F,3,90F,180F,true,true,false);
        var analysis=new LegacyOscillatingModelInventoryAnalyzer().analyze(jar,rule,presentation);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        var proof=analysis.proof().orElseThrow();
        assertEquals("foreign/inv/Handler",proof.handlerClass());
        assertEquals("uid",proof.uidField());
        assertEquals("foreign/inv/Route",proof.routeClass());
        assertEquals("map",proof.inventoryMapField());
        assertEquals("foreign/inv/Wrapper",proof.inventoryRendererClass());
        assertEquals("foreign/inv/WorldRenderer",proof.sourceRendererClass());
        assertEquals(180F,proof.yawDegrees());
        assertEquals(-.25F,proof.translateY());
        assertEquals(1F,proof.scale());
        assertEquals(0F,proof.dynamicAngleDegrees());
    }

    private static byte[] block(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/inv/Block",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_149645_b","()I",null,null);m.visitCode();m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/inv/Handler","uid","I");m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] handler(){String h="foreign/inv/Handler";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,h,null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"uid","I",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"map","Ljava/util/HashMap;",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"instance","L"+h+";",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE,"router","Lforeign/inv/Route;",null,null).visitEnd();MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitTypeInsn(Opcodes.NEW,"foreign/inv/Route");c.visitInsn(Opcodes.DUP);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/inv/Route","<init>","()V",false);c.visitFieldInsn(Opcodes.PUTFIELD,h,"router","Lforeign/inv/Route;");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();MethodVisitor make=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"makeUid","()I",null,null);make.visitCode();make.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","getNextAvailableRenderId","()I",false);make.visitVarInsn(Opcodes.ISTORE,0);make.visitVarInsn(Opcodes.ILOAD,0);make.visitFieldInsn(Opcodes.GETSTATIC,h,"instance","L"+h+";");make.visitFieldInsn(Opcodes.GETFIELD,h,"router","Lforeign/inv/Route;");make.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerBlockHandler","(ILcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V",false);make.visitVarInsn(Opcodes.ILOAD,0);make.visitInsn(Opcodes.IRETURN);make.visitMaxs(0,0);make.visitEnd();MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"init","()V",null,null);init.visitCode();init.visitFieldInsn(Opcodes.GETSTATIC,h,"map","Ljava/util/HashMap;");init.visitFieldInsn(Opcodes.GETSTATIC,h,"uid","I");init.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);init.visitTypeInsn(Opcodes.NEW,"foreign/inv/Wrapper");init.visitInsn(Opcodes.DUP);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/inv/Wrapper","<init>","()V",false);init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);init.visitInsn(Opcodes.POP);init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitTypeInsn(Opcodes.NEW,h);s.visitInsn(Opcodes.DUP);s.visitMethodInsn(Opcodes.INVOKESPECIAL,h,"<init>","()V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,h,"instance","L"+h+";");s.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");s.visitInsn(Opcodes.DUP);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,h,"map","Ljava/util/HashMap;");s.visitMethodInsn(Opcodes.INVOKESTATIC,h,"makeUid","()I",false);s.visitFieldInsn(Opcodes.PUTSTATIC,h,"uid","I");s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] route(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/inv/Route",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"renderInventoryBlock","(Lnet/minecraft/block/Block;IILnet/minecraft/client/renderer/RenderBlocks;)V",null,null);m.visitCode();m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/inv/Handler","map","Ljava/util/HashMap;");m.visitVarInsn(Opcodes.ILOAD,3);m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","get","(Ljava/lang/Object;)Ljava/lang/Object;",false);m.visitTypeInsn(Opcodes.CHECKCAST,"foreign/inv/IInventoryRenderer");m.visitVarInsn(Opcodes.ALOAD,4);m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"foreign/inv/IInventoryRenderer","renderInventory","(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;I)V",true);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] wrapper(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/inv/Wrapper",null,"java/lang/Object",new String[]{"foreign/inv/IInventoryRenderer"});MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"renderInventory","(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;I)V",null,null);m.visitCode();m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/inv/WorldRenderer","instance","Lforeign/inv/WorldRenderer;");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/inv/WorldRenderer","renderInv","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] worldRenderer(){String owner="foreign/inv/WorldRenderer",model="foreign/inv/Model";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"instance","L"+owner+";",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE,"model","L"+model+";",null,null).visitEnd();MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"renderInv","()V",null,null);m.visitCode();m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glPushMatrix","()V",false);m.visitLdcInsn(180F);m.visitInsn(Opcodes.FCONST_0);m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FCONST_0);m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glRotatef","(FFFF)V",false);m.visitInsn(Opcodes.FCONST_0);m.visitLdcInsn(-.25F);m.visitInsn(Opcodes.FCONST_0);m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glTranslatef","(FFF)V",false);m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FCONST_1);m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glScalef","(FFF)V",false);m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"model","L"+model+";");m.visitInsn(Opcodes.ACONST_NULL);for(int i=0;i<5;i++)m.visitInsn(Opcodes.FCONST_0);m.visitLdcInsn(.0625F);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,model,"func_78088_a","(Lnet/minecraft/entity/Entity;FFFFFF)V",false);m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"model","L"+model+";");m.visitInsn(Opcodes.FCONST_0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,model,"renderJoint","(F)V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glPopMatrix","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
