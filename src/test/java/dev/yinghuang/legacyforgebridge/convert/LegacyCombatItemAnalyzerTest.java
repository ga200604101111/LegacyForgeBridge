package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCombatItemAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedPlainItemWeaponIsInferredFromSourceDamageBehavior() throws Exception {
        Path jar=tempDir.resolve("foreign-weapons.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/weapons/Blade.class",weapon("foreign/weapons/Blade",false));
            put(out,"foreign/weapons/AmbiguousBlade.class",weapon("foreign/weapons/AmbiguousBlade",true));
            put(out,"foreign/weapons/Bootstrap.class",bootstrap());
        }

        var analysis=new LegacyCombatItemAnalyzer().analyze(jar);
        var blade=analysis.rules().stream().filter(rule->rule.registryName().equals("foreign_blade")).findFirst().orElseThrow();
        assertEquals(LegacyCombatItemAnalyzer.Kind.SWORD,blade.kind());
        assertEquals(6.5F,blade.attackDamage(),0.0001F);
        assertEquals("foreign/weapons/Blade",blade.sourceClass());

        assertTrue(analysis.rules().stream().noneMatch(rule->rule.registryName().equals("ambiguous_blade")),
                "A branch before the source damage base value must fail closed instead of guessing a weapon value");
    }

    private static byte[] weapon(String name,boolean branchFirst){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/Item",null);
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();

        MethodVisitor damage=w.visitMethod(Opcodes.ACC_PUBLIC,"getDamageVsEntity","(Lnet/minecraft/entity/Entity;)F",null,null);
        damage.visitCode();
        if(branchFirst){
            Label after=new Label();
            damage.visitVarInsn(Opcodes.ALOAD,1);
            damage.visitJumpInsn(Opcodes.IFNULL,after);
            damage.visitInsn(Opcodes.FCONST_1);
            damage.visitInsn(Opcodes.FRETURN);
            damage.visitLabel(after);
        }
        damage.visitLdcInsn(6.5F);
        damage.visitVarInsn(Opcodes.FSTORE,2);
        damage.visitVarInsn(Opcodes.FLOAD,2);
        damage.visitInsn(Opcodes.FRETURN);
        damage.visitMaxs(0,0);damage.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/weapons/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);
        m.visitCode();register(m,"foreign/weapons/Blade","foreign_blade");register(m,"foreign/weapons/AmbiguousBlade","ambiguous_blade");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void register(MethodVisitor m,String type,String id){
        m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);
        m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
