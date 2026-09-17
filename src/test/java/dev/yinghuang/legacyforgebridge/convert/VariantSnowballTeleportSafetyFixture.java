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

public final class VariantSnowballTeleportSafetyFixture {
    private VariantSnowballTeleportSafetyFixture(){ }
    public static Path write(Path jar)throws Exception{return rewrite(jar,false);}
    public static Path writeInvertedLiquidGate(Path jar)throws Exception{return rewrite(jar,true);}

    private static Path rewrite(Path jar,boolean invertedLiquid)throws Exception{
        Path base=jar.resolveSibling(jar.getFileName()+".base");VariantSnowballTeleportStateFixture.write(base);
        try(JarFile input=new JarFile(base.toFile());JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){var entries=input.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();output.putNextEntry(new JarEntry(entry.getName()));try(var stream=input.getInputStream(entry)){byte[] bytes=stream.readAllBytes();if("foreign/entity/VariantProjectile.class".equals(entry.getName()))bytes=patch(bytes,invertedLiquid);output.write(bytes);}output.closeEntry();}}finally{Files.deleteIfExists(base);}return jar;
    }

    private static byte[] patch(byte[] source,boolean invertedLiquid){
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(source).accept(node,0);MethodNode method=node.methods.stream().filter(m->"teleportTo".equals(m.name)&&"(Lnet/minecraft/entity/EntityLiving;DDD)Z".equals(m.desc)).findFirst().orElseThrow();method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();InsnList x=method.instructions;
        save(x,"field_70165_t",8);save(x,"field_70163_u",10);save(x,"field_70161_v",12);assign(x,"field_70165_t",2);assign(x,"field_70163_u",4);assign(x,"field_70161_v",6);x.add(new InsnNode(Opcodes.ICONST_0));x.add(new VarInsnNode(Opcodes.ISTORE,14));
        floor(x,"field_70165_t",15);floor(x,"field_70163_u",16);floor(x,"field_70161_v",17);
        LabelNode failure=new LabelNode(),loop=new LabelNode(),afterGround=new LabelNode(),groundMiss=new LabelNode(),successReturn=new LabelNode();
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new VarInsnNode(Opcodes.ILOAD,15));x.add(new VarInsnNode(Opcodes.ILOAD,16));x.add(new VarInsnNode(Opcodes.ILOAD,17));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72899_e","(III)Z",false));x.add(new JumpInsnNode(Opcodes.IFEQ,failure));
        x.add(new InsnNode(Opcodes.ICONST_0));x.add(new VarInsnNode(Opcodes.ISTORE,19));x.add(loop);x.add(new VarInsnNode(Opcodes.ILOAD,19));x.add(new JumpInsnNode(Opcodes.IFNE,afterGround));x.add(new VarInsnNode(Opcodes.ILOAD,16));x.add(new JumpInsnNode(Opcodes.IFLE,afterGround));
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new VarInsnNode(Opcodes.ILOAD,15));x.add(new VarInsnNode(Opcodes.ILOAD,16));x.add(new InsnNode(Opcodes.ICONST_1));x.add(new InsnNode(Opcodes.ISUB));x.add(new VarInsnNode(Opcodes.ILOAD,17));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_147439_a","(III)Lnet/minecraft/block/Block;",false));x.add(new VarInsnNode(Opcodes.ASTORE,18));
        x.add(new VarInsnNode(Opcodes.ALOAD,18));x.add(new FieldInsnNode(Opcodes.GETSTATIC,"net/minecraft/init/Blocks","field_150350_a","Lnet/minecraft/block/Block;"));x.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,groundMiss));x.add(new VarInsnNode(Opcodes.ALOAD,18));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149688_o","()Lnet/minecraft/block/material/Material;",false));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/material/Material","func_76230_c","()Z",false));x.add(new JumpInsnNode(Opcodes.IFEQ,groundMiss));x.add(new InsnNode(Opcodes.ICONST_1));x.add(new VarInsnNode(Opcodes.ISTORE,19));x.add(new JumpInsnNode(Opcodes.GOTO,loop));
        x.add(groundMiss);x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new InsnNode(Opcodes.DUP));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70163_u","D"));x.add(new InsnNode(Opcodes.DCONST_1));x.add(new InsnNode(Opcodes.DSUB));x.add(new FieldInsnNode(Opcodes.PUTFIELD,"net/minecraft/entity/Entity","field_70163_u","D"));x.add(new IincInsnNode(16,-1));x.add(new JumpInsnNode(Opcodes.GOTO,loop));
        x.add(afterGround);x.add(new VarInsnNode(Opcodes.ILOAD,19));x.add(new JumpInsnNode(Opcodes.IFEQ,failure));x.add(new VarInsnNode(Opcodes.ALOAD,1));currentPos(x,"field_70165_t");currentPos(x,"field_70163_u");currentPos(x,"field_70161_v");x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/EntityLiving","func_70107_b","(DDD)V",false));
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70121_D","Lnet/minecraft/util/AxisAlignedBB;"));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72945_a","(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;",false));x.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"java/util/List","size","()I",true));x.add(new JumpInsnNode(Opcodes.IFNE,failure));
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70121_D","Lnet/minecraft/util/AxisAlignedBB;"));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72953_d","(Lnet/minecraft/util/AxisAlignedBB;)Z",false));x.add(new JumpInsnNode(invertedLiquid?Opcodes.IFEQ:Opcodes.IFNE,failure));x.add(new InsnNode(Opcodes.ICONST_1));x.add(new VarInsnNode(Opcodes.ISTORE,14));
        x.add(failure);x.add(new VarInsnNode(Opcodes.ILOAD,14));x.add(new JumpInsnNode(Opcodes.IFNE,successReturn));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new VarInsnNode(Opcodes.DLOAD,8));x.add(new VarInsnNode(Opcodes.DLOAD,10));x.add(new VarInsnNode(Opcodes.DLOAD,12));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/EntityLiving","func_70107_b","(DDD)V",false));x.add(new InsnNode(Opcodes.ICONST_0));x.add(new InsnNode(Opcodes.IRETURN));x.add(successReturn);x.add(new InsnNode(Opcodes.ICONST_1));x.add(new InsnNode(Opcodes.IRETURN));method.maxLocals=20;method.maxStack=0;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);node.accept(writer);return writer.toByteArray();
    }
    private static void save(InsnList x,String field,int local){x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity",field,"D"));x.add(new VarInsnNode(Opcodes.DSTORE,local));}private static void assign(InsnList x,String field,int local){x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new VarInsnNode(Opcodes.DLOAD,local));x.add(new FieldInsnNode(Opcodes.PUTFIELD,"net/minecraft/entity/Entity",field,"D"));}
    private static void floor(InsnList x,String field,int local){x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity",field,"D"));x.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/util/MathHelper","func_76128_c","(D)I",false));x.add(new VarInsnNode(Opcodes.ISTORE,local));}private static void currentPos(InsnList x,String field){x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity",field,"D"));}
}
