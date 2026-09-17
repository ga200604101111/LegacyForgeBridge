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

public final class VariantSnowballImpactFixture {
    private VariantSnowballImpactFixture(){ }
    public static Path write(Path jar)throws Exception{return rewrite(jar,8,true);}
    public static Path writeWrongParticleCount(Path jar)throws Exception{return rewrite(jar,7,true);}
    public static Path writeMissingServerGate(Path jar)throws Exception{return rewrite(jar,8,false);}

    private static Path rewrite(Path jar,int particleCount,boolean serverGate)throws Exception{
        Path base=jar.resolveSibling(jar.getFileName()+".base");VariantSnowballMapBindingFixture.write(base);
        try(JarFile input=new JarFile(base.toFile());JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){
            var entries=input.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if("foreign/entity/VariantProjectile.class".equals(entry.getName()))continue;output.putNextEntry(new JarEntry(entry.getName()));try(var stream=input.getInputStream(entry)){output.write(stream.readAllBytes());}output.closeEntry();}
            output.putNextEntry(new JarEntry("foreign/entity/VariantProjectile.class"));output.write(projectile(particleCount,serverGate));output.closeEntry();
        }finally{Files.deleteIfExists(base);}return jar;
    }

    private static byte[] projectile(int particleCount,boolean serverGate){
        String owner="foreign/entity/VariantProjectile",selector="foreign/entity/VariantKind";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"net/minecraft/entity/projectile/EntitySnowball",null);
        w.visitField(Opcodes.ACC_PRIVATE,"kind","L"+selector+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;L"+selector+";)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/projectile/EntitySnowball","<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,3);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"kind","L"+selector+";");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PROTECTED,"func_70184_a","(Lnet/minecraft/util/MovingObjectPosition;)V",null,null);m.visitCode();
        Label selectorOk=new Label();m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"kind","L"+selector+";");m.visitJumpInsn(Opcodes.IFNONNULL,selectorOk);m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_70076_C","()V",false);m.visitInsn(Opcodes.RETURN);m.visitLabel(selectorOk);
        Label afterHit=new Label();m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/util/MovingObjectPosition","field_72308_g","Lnet/minecraft/entity/Entity;");m.visitJumpInsn(Opcodes.IFNULL,afterHit);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"kind","L"+selector+";");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,selector,"power","()B",false);m.visitVarInsn(Opcodes.ISTORE,2);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/util/MovingObjectPosition","field_72308_g","Lnet/minecraft/entity/Entity;");m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_85052_h","()Lnet/minecraft/entity/EntityLivingBase;",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/DamageSource","func_76356_a","(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/DamageSource;",false);m.visitVarInsn(Opcodes.ILOAD,2);m.visitInsn(Opcodes.I2F);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/Entity","func_70097_a","(Lnet/minecraft/util/DamageSource;F)Z",false);m.visitInsn(Opcodes.POP);m.visitLabel(afterHit);
        m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,3);Label loop=new Label(),afterLoop=new Label();m.visitLabel(loop);m.visitVarInsn(Opcodes.ILOAD,3);pushInt(m,particleCount);m.visitJumpInsn(Opcodes.IF_ICMPGE,afterLoop);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;");m.visitLdcInsn("snowballpoof");
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70165_t","D");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70163_u","D");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70161_v","D");m.visitInsn(Opcodes.DCONST_0);m.visitInsn(Opcodes.DCONST_0);m.visitInsn(Opcodes.DCONST_0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72869_a","(Ljava/lang/String;DDDDDD)V",false);m.visitIincInsn(3,1);m.visitJumpInsn(Opcodes.GOTO,loop);m.visitLabel(afterLoop);
        if(serverGate){Label done=new Label();m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;");m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/world/World","field_72995_K","Z");m.visitJumpInsn(Opcodes.IFNE,done);m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_70106_y","()V",false);m.visitLabel(done);}else{m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"func_70106_y","()V",false);}
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void pushInt(MethodVisitor m,int value){if(value>=-1&&value<=5)m.visitInsn(Opcodes.ICONST_0+value);else if(value>=Byte.MIN_VALUE&&value<=Byte.MAX_VALUE)m.visitIntInsn(Opcodes.BIPUSH,value);else m.visitLdcInsn(value);}
}
