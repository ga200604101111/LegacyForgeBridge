package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class VariantSnowballTeleportStateFixture {
    private VariantSnowballTeleportStateFixture(){ }
    public static Path write(Path jar)throws Exception{return rewrite(jar,false);}
    public static Path writeBrokenRollback(Path jar)throws Exception{return rewrite(jar,true);}

    private static Path rewrite(Path jar,boolean brokenRollback)throws Exception{
        Path base=jar.resolveSibling(jar.getFileName()+".base");VariantSnowballTeleportWrapperFixture.write(base);
        try(JarFile input=new JarFile(base.toFile());JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){
            var entries=input.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();output.putNextEntry(new JarEntry(entry.getName()));try(var stream=input.getInputStream(entry)){
                    byte[] bytes=stream.readAllBytes();if("foreign/entity/VariantProjectile.class".equals(entry.getName()))bytes=patch(bytes,brokenRollback);output.write(bytes);
                }output.closeEntry();
            }
        }finally{Files.deleteIfExists(base);}return jar;
    }

    private static byte[] patch(byte[] source,boolean brokenRollback){
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(source).accept(node,0);MethodNode method=node.methods.stream().filter(m->"teleportTo".equals(m.name)&&"(Lnet/minecraft/entity/EntityLiving;DDD)Z".equals(m.desc)).findFirst().orElseThrow();
        method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();InsnList x=method.instructions;
        save(x,"field_70165_t",8);save(x,"field_70163_u",10);save(x,"field_70161_v",12);
        assign(x,"field_70165_t",2);assign(x,"field_70163_u",4);assign(x,"field_70161_v",6);
        LabelNode falseFlag=new LabelNode(),flagReady=new LabelNode(),success=new LabelNode();
        x.add(new VarInsnNode(Opcodes.DLOAD,2));x.add(new InsnNode(Opcodes.DCONST_0));x.add(new InsnNode(Opcodes.DCMPL));x.add(new JumpInsnNode(Opcodes.IFLE,falseFlag));x.add(new InsnNode(Opcodes.ICONST_1));x.add(new JumpInsnNode(Opcodes.GOTO,flagReady));x.add(falseFlag);x.add(new InsnNode(Opcodes.ICONST_0));x.add(flagReady);x.add(new VarInsnNode(Opcodes.ISTORE,14));
        x.add(new VarInsnNode(Opcodes.ILOAD,14));x.add(new JumpInsnNode(Opcodes.IFNE,success));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new VarInsnNode(Opcodes.DLOAD,8));x.add(new VarInsnNode(Opcodes.DLOAD,brokenRollback?4:10));x.add(new VarInsnNode(Opcodes.DLOAD,12));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/EntityLiving","func_70107_b","(DDD)V",false));x.add(new InsnNode(Opcodes.ICONST_0));x.add(new InsnNode(Opcodes.IRETURN));
        x.add(success);x.add(new InsnNode(Opcodes.ICONST_1));x.add(new InsnNode(Opcodes.IRETURN));method.maxStack=0;method.maxLocals=15;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);node.accept(writer);return writer.toByteArray();
    }
    private static void save(InsnList x,String field,int local){x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity",field,"D"));x.add(new VarInsnNode(Opcodes.DSTORE,local));}
    private static void assign(InsnList x,String field,int local){x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new VarInsnNode(Opcodes.DLOAD,local));x.add(new FieldInsnNode(Opcodes.PUTFIELD,"net/minecraft/entity/Entity",field,"D"));}
}
