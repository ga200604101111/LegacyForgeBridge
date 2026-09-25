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

public final class VariantSnowballMapBindingFixture {
    private VariantSnowballMapBindingFixture(){ }

    public static Path write(Path jar)throws Exception{return rewrite(jar,false);}
    public static Path writeCrossMapped(Path jar)throws Exception{return rewrite(jar,true);}

    private static Path rewrite(Path jar,boolean crossMapped)throws Exception{
        Path base=jar.resolveSibling(jar.getFileName()+".base");
        VariantSnowballFixture.write(base);
        try(JarFile input=new JarFile(base.toFile());JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){
            var entries=input.entries();
            while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();
                if("foreign/item/VariantBall.class".equals(entry.getName()))continue;
                output.putNextEntry(new JarEntry(entry.getName()));
                try(var stream=input.getInputStream(entry)){output.write(stream.readAllBytes());}
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("foreign/item/VariantBall.class"));output.write(item(crossMapped));output.closeEntry();
        }finally{Files.deleteIfExists(base);}
        return jar;
    }

    private static byte[] item(boolean crossMapped){
        String owner="foreign/item/VariantBall",selector="foreign/entity/VariantKind";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"net/minecraft/item/ItemSnowball",null);
        w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"KINDS","Ljava/util/Map;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSnowball","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();

        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();
        s.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");s.visitInsn(Opcodes.DUP);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"KINDS","Ljava/util/Map;");
        s.visitMethodInsn(Opcodes.INVOKESTATIC,selector,"values","()[Lforeign/entity/VariantKind;",false);s.visitVarInsn(Opcodes.ASTORE,0);
        s.visitVarInsn(Opcodes.ALOAD,0);s.visitInsn(Opcodes.ARRAYLENGTH);s.visitVarInsn(Opcodes.ISTORE,1);s.visitInsn(Opcodes.ICONST_0);s.visitVarInsn(Opcodes.ISTORE,2);
        Label loop=new Label(),end=new Label();s.visitLabel(loop);s.visitVarInsn(Opcodes.ILOAD,2);s.visitVarInsn(Opcodes.ILOAD,1);s.visitJumpInsn(Opcodes.IF_ICMPGE,end);
        s.visitVarInsn(Opcodes.ALOAD,0);s.visitVarInsn(Opcodes.ILOAD,2);s.visitInsn(Opcodes.AALOAD);s.visitVarInsn(Opcodes.ASTORE,3);
        s.visitFieldInsn(Opcodes.GETSTATIC,owner,"KINDS","Ljava/util/Map;");
        if(crossMapped)s.visitInsn(Opcodes.ICONST_0);else{s.visitVarInsn(Opcodes.ALOAD,3);s.visitMethodInsn(Opcodes.INVOKEVIRTUAL,selector,"code","()I",false);}
        s.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);s.visitVarInsn(Opcodes.ALOAD,3);
        s.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Map","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",true);s.visitInsn(Opcodes.POP);
        s.visitIincInsn(2,1);s.visitJumpInsn(Opcodes.GOTO,loop);s.visitLabel(end);s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_77659_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitTypeInsn(Opcodes.NEW,"foreign/entity/VariantProjectile");m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,2);m.visitVarInsn(Opcodes.ALOAD,3);
        m.visitFieldInsn(Opcodes.GETSTATIC,owner,"KINDS","Ljava/util/Map;");m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77960_j","()I",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Map","get","(Ljava/lang/Object;)Ljava/lang/Object;",true);m.visitTypeInsn(Opcodes.CHECKCAST,selector);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/entity/VariantProjectile","<init>","(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;Lforeign/entity/VariantKind;)V",false);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72838_d","(Lnet/minecraft/entity/Entity;)Z",false);m.visitInsn(Opcodes.POP);m.visitVarInsn(Opcodes.ALOAD,1);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
}
