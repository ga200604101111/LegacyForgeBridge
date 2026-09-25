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

import static org.junit.jupiter.api.Assertions.*;

class LegacyRandomDisplayParticleAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedNamespaceProvesSymmetricZeroVelocityParticleFamily()throws Exception{
        Path jar=tempDir.resolve("particles.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/particles/EmberBlock.class",block());
            put(out,"foreign/particles/Bootstrap.class",bootstrap());
        }
        var analysis=new LegacyRandomDisplayParticleAnalyzer().analyze(jar);
        assertEquals(1,analysis.rules().size(),analysis.skipped().toString());
        var rule=analysis.rules().getFirst();
        assertEquals("ember",rule.registryName());assertEquals("foreign/particles/EmberBlock",rule.sourceBlockClass());
        assertEquals(java.util.List.of("smoke","flame"),rule.particles());
        assertEquals(.5F,rule.centerX(),.0001F);assertEquals(.2F,rule.centerY(),.0001F);assertEquals(.5F,rule.centerZ(),.0001F);
        assertEquals(.2F,rule.spreadX(),.0001F);assertEquals(.2F,rule.spreadZ(),.0001F);
    }

    private static byte[] block(){
        String owner="foreign/particles/EmberBlock";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"net/minecraft/block/Block",null);
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitInsn(Opcodes.ACONST_NULL);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/Block","<init>","(Lnet/minecraft/block/material/Material;)V",false);
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_149734_b","(Lnet/minecraft/world/World;IIILjava/util/Random;)V",null,null);m.visitCode();
        center(m,2,.5F,6);center(m,3,.2F,7);center(m,4,.5F,8);jitter(m,5,.4F,.2F,9);jitter(m,5,.4F,.2F,10);
        spawn(m,"smoke");spawn(m,"flame");m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void center(MethodVisitor m,int coordinate,float offset,int target){
        m.visitVarInsn(Opcodes.ILOAD,coordinate);m.visitInsn(Opcodes.I2F);m.visitLdcInsn(offset);m.visitInsn(Opcodes.FADD);m.visitVarInsn(Opcodes.FSTORE,target);
    }
    private static void jitter(MethodVisitor m,int random,float amplitude,float half,int target){
        m.visitVarInsn(Opcodes.ALOAD,random);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/Random","nextFloat","()F",false);
        m.visitLdcInsn(amplitude);m.visitInsn(Opcodes.FMUL);m.visitLdcInsn(half);m.visitInsn(Opcodes.FSUB);m.visitVarInsn(Opcodes.FSTORE,target);
    }
    private static void spawn(MethodVisitor m,String name){
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitLdcInsn(name);
        m.visitVarInsn(Opcodes.FLOAD,6);m.visitVarInsn(Opcodes.FLOAD,9);m.visitInsn(Opcodes.FADD);m.visitInsn(Opcodes.F2D);
        m.visitVarInsn(Opcodes.FLOAD,7);m.visitInsn(Opcodes.F2D);
        m.visitVarInsn(Opcodes.FLOAD,8);m.visitVarInsn(Opcodes.FLOAD,10);m.visitInsn(Opcodes.FADD);m.visitInsn(Opcodes.F2D);
        m.visitInsn(Opcodes.DCONST_0);m.visitInsn(Opcodes.DCONST_0);m.visitInsn(Opcodes.DCONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72869_a","(Ljava/lang/String;DDDDDD)V",false);
    }
    private static byte[] bootstrap(){
        String type="foreign/particles/EmberBlock";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/particles/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);
        m.visitLdcInsn("ember");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
