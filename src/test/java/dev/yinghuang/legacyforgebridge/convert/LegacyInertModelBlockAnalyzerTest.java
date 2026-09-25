package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyInertModelBlockAnalyzerTest {
    @TempDir Path tempDir;
    @Test void unrelatedInertRendererBlockIsAdmittedWithoutNames() throws Exception {
        Path jar=tempDir.resolve("ForeignInert.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/deco/LampBlock.class",block("foreign/deco/LampBlock","foreign/deco/LampTile"));
            put(out,"foreign/deco/LampTile.class",tile("foreign/deco/LampTile",false));
            put(out,"foreign/deco/StatefulBlock.class",block("foreign/deco/StatefulBlock","foreign/deco/StatefulTile"));
            put(out,"foreign/deco/StatefulTile.class",tile("foreign/deco/StatefulTile",true));
            put(out,"foreign/deco/Bootstrap.class",bootstrap());
        }
        var analysis=new LegacyInertModelBlockAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        assertEquals(1,analysis.rules().size());
        var rule=analysis.rules().getFirst();
        assertEquals("lamp",rule.registryName());assertEquals("foreign/deco/LampBlock",rule.sourceBlockClass());assertEquals("foreign/deco/LampTile",rule.sourceTileClass());assertEquals("lamp_tile",rule.legacyTileId());
        assertEquals(0.2F,rule.bounds().minX());assertEquals(0F,rule.bounds().minY());assertEquals(0.2F,rule.bounds().minZ());assertEquals(0.8F,rule.bounds().maxX());assertEquals(0.9F,rule.bounds().maxY());assertEquals(0.8F,rule.bounds().maxZ());
        assertEquals(0.95F,rule.sourceLightLevel());assertEquals(14,rule.modernLightEmission());assertEquals(LegacyInertModelBlockAnalyzer.ORIENTATION_PLAYER_YAW_QUADRANT,rule.orientation());assertTrue(rule.nonOpaque());assertTrue(rule.nonNormalRender());assertEquals(1,rule.renderPass());
    }
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/deco/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);AnnotationVisitor event=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);event.visitEnd();m.visitCode();registerBlock(m,"foreign/deco/LampBlock","lamp");registerTile(m,"foreign/deco/LampTile","lamp_tile");registerBlock(m,"foreign/deco/StatefulBlock","stateful");registerTile(m,"foreign/deco/StatefulTile","stateful_tile");m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void registerBlock(MethodVisitor m,String type,String id){m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);}
    private static void registerTile(MethodVisitor m,String type,String id){m.visitLdcInsn(Type.getObjectType(type));m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerTileEntity","(Ljava/lang/Class;Ljava/lang/String;)V",false);}
    private static byte[] block(String name,String tile){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/block/BlockContainer",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitInsn(Opcodes.ACONST_NULL);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/BlockContainer","<init>","(Lnet/minecraft/block/material/Material;)V",false);c.visitVarInsn(Opcodes.ALOAD,0);for(float v:new float[]{0.2F,0F,0.2F,0.8F,0.9F,0.8F})push(c,v);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"func_149676_a","(FFFFFF)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitLdcInsn(0.95F);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"func_149715_a","(F)Lnet/minecraft/block/Block;",false);c.visitInsn(Opcodes.POP);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();intReturn(w,"func_149662_c",0,true);intReturn(w,"func_149686_d",0,true);intReturn(w,"func_149656_h",1,false);MethodVisitor create=w.visitMethod(Opcodes.ACC_PUBLIC,"func_149915_a","(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;",null,null);create.visitCode();create.visitTypeInsn(Opcodes.NEW,tile);create.visitInsn(Opcodes.DUP);create.visitMethodInsn(Opcodes.INVOKESPECIAL,tile,"<init>","()V",false);create.visitInsn(Opcodes.ARETURN);create.visitMaxs(0,0);create.visitEnd();MethodVisitor p=w.visitMethod(Opcodes.ACC_PUBLIC,"func_149689_a","(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",null,null);p.visitCode();p.visitVarInsn(Opcodes.ALOAD,5);p.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/EntityLivingBase","field_70177_z","F");p.visitLdcInsn(4F);p.visitInsn(Opcodes.FMUL);p.visitLdcInsn(360F);p.visitInsn(Opcodes.FDIV);p.visitInsn(Opcodes.F2D);p.visitLdcInsn(0.5D);p.visitInsn(Opcodes.DADD);p.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/MathHelper","func_76128_c","(D)I",false);p.visitInsn(Opcodes.ICONST_3);p.visitInsn(Opcodes.IAND);p.visitVarInsn(Opcodes.ISTORE,7);p.visitVarInsn(Opcodes.ALOAD,1);p.visitVarInsn(Opcodes.ILOAD,2);p.visitVarInsn(Opcodes.ILOAD,3);p.visitVarInsn(Opcodes.ILOAD,4);p.visitVarInsn(Opcodes.ILOAD,7);p.visitInsn(Opcodes.ICONST_3);p.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72921_c","(IIIII)Z",false);p.visitInsn(Opcodes.POP);p.visitInsn(Opcodes.RETURN);p.visitMaxs(0,0);p.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] tile(String name,boolean stateful){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/tileentity/TileEntity",null);if(stateful)w.visitField(Opcodes.ACC_PRIVATE,"state","I",null,null).visitEnd();MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/tileentity/TileEntity","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();MethodVisitor u=w.visitMethod(Opcodes.ACC_PUBLIC,"canUpdate","()Z",null,null);u.visitCode();u.visitInsn(Opcodes.ICONST_0);u.visitInsn(Opcodes.IRETURN);u.visitMaxs(0,0);u.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void intReturn(ClassWriter w,String name,int value,boolean bool){MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,name,bool?"()Z":"()I",null,null);m.visitCode();m.visitInsn(value==0?Opcodes.ICONST_0:Opcodes.ICONST_1);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();}
    private static void push(MethodVisitor m,float v){if(v==0F)m.visitInsn(Opcodes.FCONST_0);else m.visitLdcInsn(v);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
