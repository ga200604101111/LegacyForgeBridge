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
        Path jar=sourceFixture(tempDir);

        var analysis=new LegacyCombatItemAnalyzer().analyze(jar);
        var blade=analysis.rules().stream().filter(rule->rule.registryName().equals("foreign_blade")).findFirst().orElseThrow();
        assertEquals(LegacyCombatItemAnalyzer.Kind.SWORD,blade.kind());
        assertEquals(6.5F,blade.attackDamage(),0.0001F);
        assertEquals("foreign/weapons/Blade",blade.sourceClass());

        assertTrue(analysis.rules().stream().noneMatch(rule->rule.registryName().equals("ambiguous_blade")),
                "A branch before the source damage base value must fail closed instead of guessing a weapon value");

        var bow=analysis.rules().stream().filter(rule->rule.registryName().equals("foreign_bow")).findFirst().orElseThrow();
        assertEquals(LegacyCombatItemAnalyzer.Kind.BOW,bow.kind());
        assertEquals(300,bow.durability());
        assertEquals(3,bow.pullStages());
        assertEquals("foreign:bow_pull_",bow.pullTexturePrefix());
        assertEquals(java.util.List.of(1,26,40),bow.pullStageMinTicks());
    }


    static Path sourceFixture(Path directory)throws Exception {
        Path jar=directory.resolve("foreign-weapons.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/weapons/Blade.class",weapon("foreign/weapons/Blade",false));
            put(out,"foreign/weapons/AmbiguousBlade.class",weapon("foreign/weapons/AmbiguousBlade",true));
            put(out,"foreign/weapons/BowLike.class",bow());
            put(out,"foreign/weapons/Bootstrap.class",bootstrap());
        }

        return jar;
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

    private static byte[] bow(){
        String name="foreign/weapons/BowLike",icon="net/minecraft/util/IIcon";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/ItemBow",null);
        w.visitField(Opcodes.ACC_PRIVATE,"pull","[L"+icon+";",null,null).visitEnd();
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemBow","<init>","()V",false);
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitIntInsn(Opcodes.SIPUSH,300);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"func_77656_e","(I)Lnet/minecraft/item/Item;",false);init.visitInsn(Opcodes.POP);
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitInsn(Opcodes.ICONST_3);init.visitTypeInsn(Opcodes.ANEWARRAY,icon);
        init.visitFieldInsn(Opcodes.PUTFIELD,name,"pull","[L"+icon+";");
        init.visitLdcInsn("foreign:bow_pull_");init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();

        MethodVisitor stage=w.visitMethod(Opcodes.ACC_PUBLIC,"stage","(I)L"+icon+";",null,null);
        stage.visitCode();stage.visitVarInsn(Opcodes.ALOAD,0);stage.visitFieldInsn(Opcodes.GETFIELD,name,"pull","[L"+icon+";");
        stage.visitVarInsn(Opcodes.ILOAD,1);stage.visitInsn(Opcodes.AALOAD);stage.visitInsn(Opcodes.ARETURN);
        stage.visitMaxs(0,0);stage.visitEnd();

        MethodVisitor get=w.visitMethod(Opcodes.ACC_PUBLIC,"getIcon","(I)L"+icon+";",null,null);
        get.visitCode();
        Label stage1=new Label(),stage0=new Label(),ordinary=new Label();
        get.visitVarInsn(Opcodes.ILOAD,1);get.visitIntInsn(Opcodes.BIPUSH,40);get.visitJumpInsn(Opcodes.IF_ICMPLT,stage1);
        get.visitVarInsn(Opcodes.ALOAD,0);get.visitInsn(Opcodes.ICONST_2);get.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"stage","(I)L"+icon+";",false);get.visitInsn(Opcodes.ARETURN);
        get.visitLabel(stage1);
        get.visitVarInsn(Opcodes.ILOAD,1);get.visitIntInsn(Opcodes.BIPUSH,25);get.visitJumpInsn(Opcodes.IF_ICMPLE,stage0);
        get.visitVarInsn(Opcodes.ALOAD,0);get.visitInsn(Opcodes.ICONST_1);get.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"stage","(I)L"+icon+";",false);get.visitInsn(Opcodes.ARETURN);
        get.visitLabel(stage0);
        get.visitVarInsn(Opcodes.ILOAD,1);get.visitJumpInsn(Opcodes.IFLE,ordinary);
        get.visitVarInsn(Opcodes.ALOAD,0);get.visitInsn(Opcodes.ICONST_0);get.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"stage","(I)L"+icon+";",false);get.visitInsn(Opcodes.ARETURN);
        get.visitLabel(ordinary);get.visitInsn(Opcodes.ACONST_NULL);get.visitInsn(Opcodes.ARETURN);
        get.visitMaxs(0,0);get.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/weapons/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
        m.visitCode();register(m,"foreign/weapons/Blade","foreign_blade");register(m,"foreign/weapons/AmbiguousBlade","ambiguous_blade");register(m,"foreign/weapons/BowLike","foreign_bow");
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
