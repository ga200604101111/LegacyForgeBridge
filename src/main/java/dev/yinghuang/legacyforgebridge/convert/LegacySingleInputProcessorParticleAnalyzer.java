package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Fail-closed proof for the legacy client grinding-particle family used by a converted processor.
 * No mod/class identity participates in admission: the source TileEntity bytecode must prove the
 * client-only metadata gate, block-dig particle branch and one-count icon-crack branch.
 */
public final class LegacySingleInputProcessorParticleAnalyzer {
    public record Proof(float blockVelocityMultiplier,float blockScale,int itemParticleCount,
                        double itemVelocityYOffset,boolean metadataGated,boolean clientOnly) { }
    public record Analysis(Optional<Proof> proof,List<String> diagnostics) {
        public Analysis { proof=proof==null?Optional.empty():proof; diagnostics=List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path jarPath,LegacySingleInputProcessorAnalyzer.Rule machine)throws IOException{
        Objects.requireNonNull(machine,"machine");
        List<String> diagnostics=new ArrayList<>();
        ClassNode tile=readClass(jarPath,machine.sourceTileClass());
        if(tile==null){
            diagnostics.add("Processor particle source TileEntity is missing: "+machine.sourceTileClass()+".");
            return new Analysis(Optional.empty(),diagnostics);
        }
        MethodNode tick=method(tile,Set.of("updateEntity","func_145845_h"),"()V");
        if(tick==null){
            diagnostics.add("Processor particle source has no admitted client tick method.");
            return new Analysis(Optional.empty(),diagnostics);
        }
        boolean clientOnly=field(tick,"net/minecraft/world/World","field_72995_K","Z")
                && calls(tick,machine.sourceTileClass(),"func_145832_p","()I")
                && hasConditionalJump(tick);
        if(!clientOnly){
            diagnostics.add("Processor particles are not proven behind the source client/metadata gate.");
            return new Analysis(Optional.empty(),diagnostics);
        }
        if(!blockParticleBranch(tick)){
            diagnostics.add("Processor block-input digging particle branch is not the admitted source shape.");
            return new Analysis(Optional.empty(),diagnostics);
        }
        MethodNode helper=itemParticleHelper(tile,tick);
        if(helper==null||!itemParticleShape(helper)){
            diagnostics.add("Processor ordinary-item icon-crack particle branch is not the admitted one-count source shape.");
            return new Analysis(Optional.empty(),diagnostics);
        }
        return new Analysis(Optional.of(new Proof(0.2F,0.6F,1,0.15D,true,true)),
                List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static boolean blockParticleBranch(MethodNode tick){
        return hasType(tick,Opcodes.INSTANCEOF,"net/minecraft/item/ItemBlock")
                &&hasType(tick,Opcodes.NEW,"net/minecraft/client/particle/EntityDiggingFX")
                &&calls(tick,"net/minecraft/client/particle/EntityDiggingFX","<init>",
                "(Lnet/minecraft/world/World;DDDDDDLnet/minecraft/block/Block;I)V")
                &&calls(tick,"net/minecraft/block/Block","func_149741_i","(I)I")
                &&calls(tick,"net/minecraft/client/particle/EntityDiggingFX","func_90019_g","(I)Lnet/minecraft/client/particle/EntityDiggingFX;")
                &&calls(tick,"net/minecraft/client/particle/EntityDiggingFX","func_70543_e","(F)Lnet/minecraft/client/particle/EntityFX;")
                &&calls(tick,"net/minecraft/client/particle/EntityFX","func_70541_f","(F)Lnet/minecraft/client/particle/EntityFX;")
                &&calls(tick,"net/minecraft/client/particle/EffectRenderer","func_78873_a","(Lnet/minecraft/client/particle/EntityFX;)V")
                &&hasFloat(tick,0.2F)&&hasFloat(tick,0.6F)&&hasString(tick,"iconcrack_")
                &&calls(tick,"net/minecraft/util/RegistryNamespaced","func_148757_b","(Ljava/lang/Object;)I");
    }

    private static MethodNode itemParticleHelper(ClassNode tile,MethodNode tick){
        Set<String> names=new LinkedHashSet<>();
        for(AbstractInsnNode insn:tick.instructions){
            if(insn instanceof MethodInsnNode call&&call.owner.equals(tile.name)
                    &&call.desc.equals("(Ljava/lang/String;)V")&&call.getOpcode()!=Opcodes.INVOKESTATIC)names.add(call.name);
        }
        if(names.size()!=1)return null;
        return method(tile,names,"(Ljava/lang/String;)V");
    }

    private static boolean itemParticleShape(MethodNode method){
        return calls(method,"net/minecraft/world/World","func_72869_a","(Ljava/lang/String;DDDDDD)V")
                &&calls(method,"net/minecraft/util/Vec3","func_72443_a","(DDD)Lnet/minecraft/util/Vec3;")
                &&calls(method,"net/minecraft/util/Vec3","func_72442_b","(F)V")
                &&calls(method,"net/minecraft/util/Vec3","func_72441_c","(DDD)Lnet/minecraft/util/Vec3;")
                &&calls(method,"java/lang/Math","random","()D")
                &&hasDouble(method,0.5D)&&hasDouble(method,0.1D)&&hasDouble(method,0.3D)
                &&hasDouble(method,0.6D)&&hasDouble(method,0.15D)&&hasLoopBoundOne(method);
    }

    private static boolean hasLoopBoundOne(MethodNode method){
        List<AbstractInsnNode> code=real(method);
        for(int i=1;i<code.size();i++){
            if(code.get(i) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IF_ICMPGE
                    &&intConstant(code.get(i-1))!=null&&intConstant(code.get(i-1))==1)return true;
        }
        return false;
    }

    private static boolean hasConditionalJump(MethodNode method){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof JumpInsnNode jump){
            int op=jump.getOpcode();if(op>=Opcodes.IFEQ&&op<=Opcodes.IF_ACMPNE)return true;
        }
        return false;
    }
    private static MethodNode method(ClassNode owner,Set<String> names,String desc){
        for(MethodNode method:owner.methods)if(names.contains(method.name)&&method.desc.equals(desc))return method;return null;
    }
    private static boolean field(MethodNode method,String owner,String name,String desc){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode value
                &&value.owner.equals(owner)&&value.name.equals(name)&&value.desc.equals(desc))return true;return false;
    }
    private static boolean calls(MethodNode method,String owner,String name,String desc){
        if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call
                &&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;
    }
    private static boolean hasType(MethodNode method,int opcode,String type){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode value
                &&value.getOpcode()==opcode&&value.desc.equals(type))return true;return false;
    }
    private static boolean hasString(MethodNode method,String value){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&value.equals(ldc.cst))return true;return false;
    }
    private static boolean hasFloat(MethodNode method,float value){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float number
                &&Float.compare(number,value)==0)return true;return false;
    }
    private static boolean hasDouble(MethodNode method,double value){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double number
                &&Double.compare(number,value)==0)return true;return false;
    }
    private static Integer intConstant(AbstractInsnNode insn){
        int op=insn.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;
        if(insn instanceof IntInsnNode value)return value.operand;
        if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value)return value;return null;
    }
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)
            if(!(insn instanceof LabelNode)&&!(insn instanceof LineNumberNode)&&!(insn instanceof FrameNode))result.add(insn);return result;
    }
    private static ClassNode readClass(Path jarPath,String internalName)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){
            JarEntry entry=jar.getJarEntry(internalName+".class");if(entry==null)return null;
            try(InputStream input=jar.getInputStream(entry)){
                ClassNode node=new ClassNode();new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;
            }
        }
    }
}
