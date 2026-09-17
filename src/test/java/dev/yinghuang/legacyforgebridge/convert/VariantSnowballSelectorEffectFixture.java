package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Javac-style enum-switch fixture layered on the proven variant-snowball common impact shell. */
public final class VariantSnowballSelectorEffectFixture {
    private VariantSnowballSelectorEffectFixture(){ }
    public static Path write(Path jar)throws Exception{return rewrite(jar,true);}
    public static Path writeMissingCaseBinding(Path jar)throws Exception{return rewrite(jar,false);}

    private static Path rewrite(Path jar,boolean bindCase)throws Exception{
        Path base=jar.resolveSibling(jar.getFileName()+".base");VariantSnowballMapBindingFixture.write(base);
        try(JarFile input=new JarFile(base.toFile());JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){
            var entries=input.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();if("foreign/entity/VariantProjectile.class".equals(entry.getName()))continue;
                output.putNextEntry(new JarEntry(entry.getName()));try(var stream=input.getInputStream(entry)){output.write(stream.readAllBytes());}output.closeEntry();
            }
            output.putNextEntry(new JarEntry("foreign/entity/VariantProjectile.class"));output.write(projectile());output.closeEntry();
            output.putNextEntry(new JarEntry("foreign/entity/VariantProjectile$1.class"));output.write(switchTable(bindCase));output.closeEntry();
        }finally{Files.deleteIfExists(base);}return jar;
    }

    private static byte[] switchTable(boolean bindCase){
        String owner="foreign/entity/VariantProjectile$1",selector="foreign/entity/VariantKind",field="$SwitchMap$foreign$entity$VariantKind";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_FINAL|Opcodes.ACC_SUPER|Opcodes.ACC_SYNTHETIC,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_STATIC|Opcodes.ACC_FINAL|Opcodes.ACC_SYNTHETIC,field,"[I",null,null).visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitInsn(Opcodes.ICONST_2);s.visitIntInsn(Opcodes.NEWARRAY,Opcodes.T_INT);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,field,"[I");
        if(bindCase){s.visitFieldInsn(Opcodes.GETSTATIC,owner,field,"[I");s.visitFieldInsn(Opcodes.GETSTATIC,selector,"poison","L"+selector+";");s.visitMethodInsn(Opcodes.INVOKEVIRTUAL,selector,"ordinal","()I",false);s.visitInsn(Opcodes.ICONST_1);s.visitInsn(Opcodes.IASTORE);}
        s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] projectile(){
        String owner="foreign/entity/VariantProjectile",selector="foreign/entity/VariantKind",tableOwner="foreign/entity/VariantProjectile$1",tableField="$SwitchMap$foreign$entity$VariantKind";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"net/minecraft/entity/projectile/EntitySnowball",null);
        w.visitField(Opcodes.ACC_PRIVATE,"kind","L"+selector+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;L"+selector+";)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/projectile/EntitySnowball","<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,3);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"kind","L"+selector+";");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PROTECTED,"func_70184_a","(Lnet/minecraft/util/MovingObjectPosition;)V",null,null);m.visitCode();
        Label selectorOk=new Label();m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"kind","L"+selector+";");m.visitJumpInsn(Opcodes.IFNONNULL,selectorOk);m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_70076_C","()V",false);m.visitInsn(Opcodes.RETURN);m.visitLabel(selectorOk);
        Label afterHit=new Label();m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/util/MovingObjectPosition","field_72308_g","Lnet/minecraft/entity/Entity;");m.visitJumpInsn(Opcodes.IFNULL,afterHit);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"kind","L"+selector+";");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,selector,"power","()B",false);m.visitVarInsn(Opcodes.ISTORE,2);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/util/MovingObjectPosition","field_72308_g","Lnet/minecraft/entity/Entity;");m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_85052_h","()Lnet/minecraft/entity/EntityLivingBase;",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/DamageSource","func_76356_a","(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/DamageSource;",false);m.visitVarInsn(Opcodes.ILOAD,2);m.visitInsn(Opcodes.I2F);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/Entity","func_70097_a","(Lnet/minecraft/util/DamageSource;F)Z",false);m.visitInsn(Opcodes.POP);

        Label afterSpecial=new Label(),poison=new Label();
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/util/MovingObjectPosition","field_72308_g","Lnet/minecraft/entity/Entity;");m.visitTypeInsn(Opcodes.INSTANCEOF,"net/minecraft/entity/EntityLiving");m.visitJumpInsn(Opcodes.IFEQ,afterSpecial);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/util/MovingObjectPosition","field_72308_g","Lnet/minecraft/entity/Entity;");m.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/EntityLiving");m.visitVarInsn(Opcodes.ASTORE,4);
        m.visitFieldInsn(Opcodes.GETSTATIC,tableOwner,tableField,"[I");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"kind","L"+selector+";");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,selector,"ordinal","()I",false);m.visitInsn(Opcodes.IALOAD);m.visitLookupSwitchInsn(afterSpecial,new int[]{1},new Label[]{poison});
        m.visitLabel(poison);m.visitVarInsn(Opcodes.ALOAD,4);m.visitTypeInsn(Opcodes.NEW,"net/minecraft/potion/PotionEffect");m.visitInsn(Opcodes.DUP);m.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/potion/Potion","field_76436_u","Lnet/minecraft/potion/Potion;");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/potion/Potion","func_76396_c","()I",false);m.visitIntInsn(Opcodes.BIPUSH,30);m.visitInsn(Opcodes.ICONST_3);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/potion/PotionEffect","<init>","(III)V",false);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/EntityLivingBase","func_70690_d","(Lnet/minecraft/potion/PotionEffect;)V",false);m.visitJumpInsn(Opcodes.GOTO,afterSpecial);
        m.visitLabel(afterSpecial);m.visitLabel(afterHit);

        m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,3);Label loop=new Label(),afterLoop=new Label();m.visitLabel(loop);m.visitVarInsn(Opcodes.ILOAD,3);m.visitIntInsn(Opcodes.BIPUSH,8);m.visitJumpInsn(Opcodes.IF_ICMPGE,afterLoop);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;");m.visitLdcInsn("snowballpoof");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70165_t","D");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70163_u","D");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70161_v","D");m.visitInsn(Opcodes.DCONST_0);m.visitInsn(Opcodes.DCONST_0);m.visitInsn(Opcodes.DCONST_0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72869_a","(Ljava/lang/String;DDDDDD)V",false);m.visitIincInsn(3,1);m.visitJumpInsn(Opcodes.GOTO,loop);m.visitLabel(afterLoop);
        Label done=new Label();m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;");m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/world/World","field_72995_K","Z");m.visitJumpInsn(Opcodes.IFNE,done);m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_70106_y","()V",false);m.visitLabel(done);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
