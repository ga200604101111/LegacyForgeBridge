package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyNbtByteIconSelectorAnalyzerTest {
    @TempDir Path tempDir;
    private static final String ITEM="foreign/icon/StateItem";

    @Test
    void unrelatedNamespaceProvesFixedArrayPrefixAndNbtByteSelector()throws Exception{
        Path jar=tempDir.resolve("foreign-nbt-icon.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,ITEM+".class",item());
            put(out,"foreign/icon/Bootstrap.class",bootstrap());
        }
        var analysis=new LegacyNbtByteIconSelectorAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        assertEquals(1,analysis.rules().size(),analysis.skipped().toString());
        var rule=analysis.rules().getFirst();
        assertEquals("state_item",rule.registryName());
        assertEquals(ITEM,rule.sourceClass());
        assertEquals("phase",rule.nbtKey());
        assertEquals("foreign:state_",rule.texturePrefix());
        assertEquals(4,rule.variants());assertEquals(0,rule.defaultIndex());
    }

    private static byte[] item(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,ITEM,null,"net/minecraft/item/Item",null);
        w.visitField(Opcodes.ACC_PRIVATE,"icons","[Lnet/minecraft/util/IIcon;",null,null).visitEnd();

        MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitInsn(Opcodes.ICONST_4);init.visitTypeInsn(Opcodes.ANEWARRAY,"net/minecraft/util/IIcon");
        init.visitFieldInsn(Opcodes.PUTFIELD,ITEM,"icons","[Lnet/minecraft/util/IIcon;");
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();

        MethodVisitor reg=w.visitMethod(Opcodes.ACC_PUBLIC,"func_94581_a","(Lnet/minecraft/client/renderer/texture/IIconRegister;)V",null,null);
        reg.visitCode();reg.visitInsn(Opcodes.ICONST_0);reg.visitVarInsn(Opcodes.ISTORE,2);
        Label check=new Label(),end=new Label();reg.visitLabel(check);
        reg.visitVarInsn(Opcodes.ILOAD,2);reg.visitVarInsn(Opcodes.ALOAD,0);reg.visitFieldInsn(Opcodes.GETFIELD,ITEM,"icons","[Lnet/minecraft/util/IIcon;");
        reg.visitInsn(Opcodes.ARRAYLENGTH);reg.visitJumpInsn(Opcodes.IF_ICMPGE,end);
        reg.visitVarInsn(Opcodes.ALOAD,0);reg.visitFieldInsn(Opcodes.GETFIELD,ITEM,"icons","[Lnet/minecraft/util/IIcon;");reg.visitVarInsn(Opcodes.ILOAD,2);
        reg.visitVarInsn(Opcodes.ALOAD,1);reg.visitTypeInsn(Opcodes.NEW,"java/lang/StringBuilder");reg.visitInsn(Opcodes.DUP);
        reg.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/StringBuilder","<init>","()V",false);
        reg.visitLdcInsn("foreign:state_");reg.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);
        reg.visitVarInsn(Opcodes.ILOAD,2);reg.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(I)Ljava/lang/StringBuilder;",false);
        reg.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);
        reg.visitMethodInsn(Opcodes.INVOKEINTERFACE,"net/minecraft/client/renderer/texture/IIconRegister","func_94245_a","(Ljava/lang/String;)Lnet/minecraft/util/IIcon;",true);
        reg.visitInsn(Opcodes.AASTORE);reg.visitIincInsn(2,1);reg.visitJumpInsn(Opcodes.GOTO,check);
        reg.visitLabel(end);reg.visitInsn(Opcodes.RETURN);reg.visitMaxs(0,0);reg.visitEnd();

        MethodVisitor icon=w.visitMethod(Opcodes.ACC_PUBLIC,"getIcon","(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;",null,null);
        icon.visitCode();Label fallback=new Label();
        icon.visitVarInsn(Opcodes.ALOAD,1);icon.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");
        icon.visitJumpInsn(Opcodes.IFNULL,fallback);
        icon.visitVarInsn(Opcodes.ALOAD,1);icon.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");
        icon.visitLdcInsn("phase");icon.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74764_b","(Ljava/lang/String;)Z",false);
        icon.visitJumpInsn(Opcodes.IFEQ,fallback);
        icon.visitVarInsn(Opcodes.ALOAD,0);icon.visitFieldInsn(Opcodes.GETFIELD,ITEM,"icons","[Lnet/minecraft/util/IIcon;");
        icon.visitVarInsn(Opcodes.ALOAD,1);icon.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");
        icon.visitLdcInsn("phase");icon.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74771_c","(Ljava/lang/String;)B",false);
        icon.visitInsn(Opcodes.AALOAD);icon.visitInsn(Opcodes.ARETURN);
        icon.visitLabel(fallback);icon.visitVarInsn(Opcodes.ALOAD,0);icon.visitFieldInsn(Opcodes.GETFIELD,ITEM,"icons","[Lnet/minecraft/util/IIcon;");
        icon.visitInsn(Opcodes.ICONST_0);icon.visitInsn(Opcodes.AALOAD);icon.visitInsn(Opcodes.ARETURN);
        icon.visitMaxs(0,0);icon.visitEnd();

        w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/icon/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,ITEM);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,ITEM,"<init>","()V",false);
        m.visitLdcInsn("state_item");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
