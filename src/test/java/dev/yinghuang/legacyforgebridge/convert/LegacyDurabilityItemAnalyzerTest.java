package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDurabilityItemAnalyzerTest {
    @TempDir Path temp;

    @Test void literalMaxDamageAndExactInverseBarAreRecoveredWithoutItemNames()throws Exception{
        Path jar=temp.resolve("durability.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/ProgressTool.class",tool());
            put(out,"foreign/Bootstrap.class",bootstrap());
        }
        var analysis=new LegacyDurabilityItemAnalyzer().analyze(jar);
        var rule=analysis.rules().stream().filter(r->r.registryName().equals("progress_tool")).findFirst().orElseThrow();
        assertEquals(10000,rule.durability());assertTrue(rule.alwaysShowBar());assertTrue(rule.inverseProgressBar());
    }

    private static byte[] tool(){
        String name="foreign/ProgressTool",stack="net/minecraft/item/ItemStack";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/Item",null);
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitIntInsn(Opcodes.SIPUSH,10000);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"func_77656_e","(I)Lnet/minecraft/item/Item;",false);init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
        MethodVisitor visible=w.visitMethod(Opcodes.ACC_PUBLIC,"showDurabilityBar","(L"+stack+";)Z",null,null);
        visible.visitCode();visible.visitInsn(Opcodes.ICONST_1);visible.visitInsn(Opcodes.IRETURN);visible.visitMaxs(0,0);visible.visitEnd();
        MethodVisitor display=w.visitMethod(Opcodes.ACC_PUBLIC,"getDurabilityForDisplay","(L"+stack+";)D",null,null);
        display.visitCode();display.visitInsn(Opcodes.DCONST_1);display.visitVarInsn(Opcodes.ALOAD,1);
        display.visitMethodInsn(Opcodes.INVOKEVIRTUAL,stack,"func_77952_i","()I",false);display.visitInsn(Opcodes.I2D);
        display.visitVarInsn(Opcodes.ALOAD,1);display.visitMethodInsn(Opcodes.INVOKEVIRTUAL,stack,"func_77958_k","()I",false);
        display.visitInsn(Opcodes.I2D);display.visitInsn(Opcodes.DDIV);display.visitInsn(Opcodes.DSUB);display.visitInsn(Opcodes.DRETURN);
        display.visitMaxs(0,0);display.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"foreign/ProgressTool");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/ProgressTool","<init>","()V",false);m.visitLdcInsn("progress_tool");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
