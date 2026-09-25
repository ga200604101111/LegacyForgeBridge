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

public final class VariantSnowballTeleportPresentationFixture {
    private VariantSnowballTeleportPresentationFixture(){ }
    public static Path write(Path jar)throws Exception{return rewrite(jar,128,"mob.endermen.portal");}
    public static Path writeWrongParticleCount(Path jar)throws Exception{return rewrite(jar,127,"mob.endermen.portal");}
    public static Path writeWrongEntitySound(Path jar)throws Exception{return rewrite(jar,128,"mob.endermen.teleport");}

    private static Path rewrite(Path jar,int particleCount,String entitySound)throws Exception{
        Path base=jar.resolveSibling(jar.getFileName()+".base");VariantSnowballTeleportSafetyFixture.write(base);
        try(JarFile input=new JarFile(base.toFile());JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){var entries=input.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();output.putNextEntry(new JarEntry(entry.getName()));try(var stream=input.getInputStream(entry)){byte[] bytes=stream.readAllBytes();if("foreign/entity/VariantProjectile.class".equals(entry.getName()))bytes=patch(bytes,particleCount,entitySound);output.write(bytes);}output.closeEntry();}}finally{Files.deleteIfExists(base);}return jar;
    }

    private static byte[] patch(byte[] source,int particleCount,String entitySound){
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(source).accept(node,0);MethodNode method=node.methods.stream().filter(m->"teleportTo".equals(m.name)&&"(Lnet/minecraft/entity/EntityLiving;DDD)Z".equals(m.desc)).findFirst().orElseThrow();
        AbstractInsnNode trueConst=null;for(AbstractInsnNode i=method.instructions.getLast();i!=null;i=i.getPrevious())if(i.getOpcode()==Opcodes.IRETURN){AbstractInsnNode p=previousMeaningful(i);if(p!=null&&p.getOpcode()==Opcodes.ICONST_1){trueConst=p;break;}}
        if(trueConst==null)throw new IllegalStateException("success return not found");InsnList x=new InsnList();
        pushInt(x,particleCount);x.add(new VarInsnNode(Opcodes.ISTORE,20));x.add(new InsnNode(Opcodes.ICONST_0));x.add(new VarInsnNode(Opcodes.ISTORE,21));LabelNode loop=new LabelNode(),after=new LabelNode();x.add(loop);x.add(new VarInsnNode(Opcodes.ILOAD,21));x.add(new VarInsnNode(Opcodes.ILOAD,20));x.add(new JumpInsnNode(Opcodes.IF_ICMPGE,after));
        x.add(new VarInsnNode(Opcodes.ILOAD,21));x.add(new InsnNode(Opcodes.I2D));x.add(new VarInsnNode(Opcodes.ILOAD,20));x.add(new InsnNode(Opcodes.I2D));x.add(new InsnNode(Opcodes.DCONST_1));x.add(new InsnNode(Opcodes.DSUB));x.add(new InsnNode(Opcodes.DDIV));x.add(new VarInsnNode(Opcodes.DSTORE,22));
        velocity(x,24);velocity(x,25);velocity(x,26);axisWidth(x,8,"field_70165_t",27);axisHeight(x,10,29);axisWidth(x,12,"field_70161_v",31);
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new LdcInsnNode("portal"));x.add(new VarInsnNode(Opcodes.DLOAD,27));x.add(new VarInsnNode(Opcodes.DLOAD,29));x.add(new VarInsnNode(Opcodes.DLOAD,31));x.add(new VarInsnNode(Opcodes.FLOAD,24));x.add(new InsnNode(Opcodes.F2D));x.add(new VarInsnNode(Opcodes.FLOAD,25));x.add(new InsnNode(Opcodes.F2D));x.add(new VarInsnNode(Opcodes.FLOAD,26));x.add(new InsnNode(Opcodes.F2D));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72869_a","(Ljava/lang/String;DDDDDD)V",false));x.add(new IincInsnNode(21,1));x.add(new JumpInsnNode(Opcodes.GOTO,loop));x.add(after);
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new VarInsnNode(Opcodes.DLOAD,8));x.add(new VarInsnNode(Opcodes.DLOAD,10));x.add(new VarInsnNode(Opcodes.DLOAD,12));x.add(new LdcInsnNode("mob.endermen.portal"));x.add(new InsnNode(Opcodes.FCONST_1));x.add(new InsnNode(Opcodes.FCONST_1));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72908_a","(DDDLjava/lang/String;FF)V",false));
        x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70170_p","Lnet/minecraft/world/World;"));x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new LdcInsnNode(entitySound));x.add(new InsnNode(Opcodes.FCONST_1));x.add(new InsnNode(Opcodes.FCONST_1));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72956_a","(Lnet/minecraft/entity/Entity;Ljava/lang/String;FF)V",false));
        method.instructions.insertBefore(trueConst,x);method.maxLocals=33;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);node.accept(writer);return writer.toByteArray();
    }

    private static void velocity(InsnList x,int local){x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70146_Z","Ljava/util/Random;"));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Random","nextFloat","()F",false));x.add(new LdcInsnNode(0.5F));x.add(new InsnNode(Opcodes.FSUB));x.add(new LdcInsnNode(0.2F));x.add(new InsnNode(Opcodes.FMUL));x.add(new VarInsnNode(Opcodes.FSTORE,local));}
    private static void axisWidth(InsnList x,int saved,String field,int out){x.add(new VarInsnNode(Opcodes.DLOAD,saved));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity",field,"D"));x.add(new VarInsnNode(Opcodes.DLOAD,saved));x.add(new InsnNode(Opcodes.DSUB));x.add(new VarInsnNode(Opcodes.DLOAD,22));x.add(new InsnNode(Opcodes.DMUL));x.add(new InsnNode(Opcodes.DADD));x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70146_Z","Ljava/util/Random;"));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Random","nextDouble","()D",false));x.add(new LdcInsnNode(0.5D));x.add(new InsnNode(Opcodes.DSUB));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70130_N","F"));x.add(new InsnNode(Opcodes.F2D));x.add(new InsnNode(Opcodes.DMUL));x.add(new LdcInsnNode(2.0D));x.add(new InsnNode(Opcodes.DMUL));x.add(new InsnNode(Opcodes.DADD));x.add(new VarInsnNode(Opcodes.DSTORE,out));}
    private static void axisHeight(InsnList x,int saved,int out){x.add(new VarInsnNode(Opcodes.DLOAD,saved));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70163_u","D"));x.add(new VarInsnNode(Opcodes.DLOAD,saved));x.add(new InsnNode(Opcodes.DSUB));x.add(new VarInsnNode(Opcodes.DLOAD,22));x.add(new InsnNode(Opcodes.DMUL));x.add(new InsnNode(Opcodes.DADD));x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70146_Z","Ljava/util/Random;"));x.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Random","nextDouble","()D",false));x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/Entity","field_70131_O","F"));x.add(new InsnNode(Opcodes.F2D));x.add(new InsnNode(Opcodes.DMUL));x.add(new InsnNode(Opcodes.DADD));x.add(new VarInsnNode(Opcodes.DSTORE,out));}
    private static void pushInt(InsnList x,int value){if(value>=-1&&value<=5)x.add(new InsnNode(Opcodes.ICONST_0+value));else if(value>=Byte.MIN_VALUE&&value<=Byte.MAX_VALUE)x.add(new IntInsnNode(Opcodes.BIPUSH,value));else x.add(new IntInsnNode(Opcodes.SIPUSH,value));}
    private static AbstractInsnNode previousMeaningful(AbstractInsnNode node){for(AbstractInsnNode p=node.getPrevious();p!=null;p=p.getPrevious())if(!(p instanceof LabelNode||p instanceof LineNumberNode||p instanceof FrameNode))return p;return null;}
}
