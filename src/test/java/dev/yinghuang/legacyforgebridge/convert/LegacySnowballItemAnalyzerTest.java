package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySnowballItemAnalyzerTest {
    @TempDir Path tempDir;
    @Test void unrelatedInheritedSnowballIsAcceptedButCustomUseIsFailClosed()throws Exception{
        Path jar=tempDir.resolve("ForeignSnowball.jar");try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){put(out,"other/projectile/PureSnow.class",pure());put(out,"other/projectile/CustomSnow.class",custom());put(out,"other/projectile/Bootstrap.class",bootstrap());}
        var analysis=new LegacySnowballItemAnalyzer().analyze(jar);assertTrue(analysis.diagnostics().isEmpty());assertEquals(1,analysis.rules().size(),analysis.skipped().toString());assertEquals("pure_snow",analysis.rules().getFirst().registryName());assertEquals(1,analysis.skipped().size());assertTrue(analysis.skipped().getFirst().reason().contains("custom method"),analysis.skipped().toString());
    }
    private static byte[] pure(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/projectile/PureSnow",null,"net/minecraft/item/ItemSnowball",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSnowball","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] custom(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/projectile/CustomSnow",null,"net/minecraft/item/ItemSnowball",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSnowball","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();MethodVisitor use=w.visitMethod(Opcodes.ACC_PUBLIC,"onItemRightClick","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",null,null);use.visitCode();use.visitVarInsn(Opcodes.ALOAD,1);use.visitInsn(Opcodes.ARETURN);use.visitMaxs(0,0);use.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/projectile/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();register(m,"other/projectile/PureSnow","pure_snow");register(m,"other/projectile/CustomSnow","custom_snow");m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void register(MethodVisitor m,String type,String id){m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
