package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class VariantSnowballFixture {
    private VariantSnowballFixture() { }

    public static Path write(Path jar)throws Exception{
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/item/VariantBall.class",item());
            put(out,"foreign/entity/VariantProjectile.class",projectile());
            put(out,"foreign/entity/VariantKind.class",selector());
            put(out,"foreign/Bootstrap.class",bootstrap());
        }
        return jar;
    }

    private static byte[] item(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/item/VariantBall",null,"net/minecraft/item/ItemSnowball",null);
        w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"KINDS","Ljava/util/Map;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSnowball","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_77659_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,2);
        m.visitTypeInsn(Opcodes.NEW,"foreign/entity/VariantProjectile");m.visitInsn(Opcodes.DUP);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitVarInsn(Opcodes.ALOAD,3);
        m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/item/VariantBall","KINDS","Ljava/util/Map;");
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77960_j","()I",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Map","get","(Ljava/lang/Object;)Ljava/lang/Object;",true);
        m.visitTypeInsn(Opcodes.CHECKCAST,"foreign/entity/VariantKind");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/entity/VariantProjectile","<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;Lforeign/entity/VariantKind;)V",false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72838_d","(Lnet/minecraft/entity/Entity;)Z",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] projectile(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/entity/VariantProjectile",null,"net/minecraft/entity/projectile/EntitySnowball",null);
        w.visitField(Opcodes.ACC_PRIVATE,"kind","Lforeign/entity/VariantKind;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;Lforeign/entity/VariantKind;)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/projectile/EntitySnowball","<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,3);c.visitFieldInsn(Opcodes.PUTFIELD,"foreign/entity/VariantProjectile","kind","Lforeign/entity/VariantKind;");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor impact=w.visitMethod(Opcodes.ACC_PROTECTED,"func_70184_a","(Lnet/minecraft/util/MovingObjectPosition;)V",null,null);impact.visitCode();impact.visitInsn(Opcodes.RETURN);impact.visitMaxs(0,0);impact.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] selector(){
        String owner="foreign/entity/VariantKind";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_SUPER|Opcodes.ACC_ENUM,owner,"Ljava/lang/Enum<Lforeign/entity/VariantKind;>;","java/lang/Enum",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL|Opcodes.ACC_ENUM,"stone","Lforeign/entity/VariantKind;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL|Opcodes.ACC_ENUM,"poison","Lforeign/entity/VariantKind;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE,"code","I",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE,"power","B",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PRIVATE,"<init>","(Ljava/lang/String;III)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitVarInsn(Opcodes.ILOAD,2);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Enum","<init>","(Ljava/lang/String;I)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ILOAD,3);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"code","I");c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ILOAD,4);c.visitInsn(Opcodes.I2B);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"power","B");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor id=w.visitMethod(Opcodes.ACC_PUBLIC,"code","()I",null,null);id.visitCode();id.visitVarInsn(Opcodes.ALOAD,0);id.visitFieldInsn(Opcodes.GETFIELD,owner,"code","I");id.visitInsn(Opcodes.IRETURN);id.visitMaxs(0,0);id.visitEnd();
        MethodVisitor dmg=w.visitMethod(Opcodes.ACC_PUBLIC,"power","()B",null,null);dmg.visitCode();dmg.visitVarInsn(Opcodes.ALOAD,0);dmg.visitFieldInsn(Opcodes.GETFIELD,owner,"power","B");dmg.visitInsn(Opcodes.IRETURN);dmg.visitMaxs(0,0);dmg.visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();
        s.visitTypeInsn(Opcodes.NEW,owner);s.visitInsn(Opcodes.DUP);s.visitLdcInsn("stone");s.visitInsn(Opcodes.ICONST_0);s.visitInsn(Opcodes.ICONST_0);s.visitInsn(Opcodes.ICONST_2);s.visitMethodInsn(Opcodes.INVOKESPECIAL,owner,"<init>","(Ljava/lang/String;III)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"stone","Lforeign/entity/VariantKind;");
        s.visitTypeInsn(Opcodes.NEW,owner);s.visitInsn(Opcodes.DUP);s.visitLdcInsn("poison");s.visitInsn(Opcodes.ICONST_1);s.visitIntInsn(Opcodes.BIPUSH,7);s.visitInsn(Opcodes.ICONST_0);s.visitMethodInsn(Opcodes.INVOKESPECIAL,owner,"<init>","(Ljava/lang/String;III)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"poison","Lforeign/entity/VariantKind;");s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();m.visitTypeInsn(Opcodes.NEW,"foreign/item/VariantBall");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/item/VariantBall","<init>","()V",false);m.visitLdcInsn("variant_ball");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
