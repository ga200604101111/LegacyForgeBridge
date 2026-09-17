package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bounded proof for the target-position save/candidate/rollback skeleton of a source-mapped
 * variant-snowball teleport core. This does not prove how the success flag is computed.
 */
public final class LegacyVariantSnowballTeleportStateAnalyzer {
    private static final String ENTITY="net/minecraft/entity/Entity";
    private static final String ENTITY_LIVING="net/minecraft/entity/EntityLiving";
    private static final Set<String> POS_X=Set.of("posX","field_70165_t");
    private static final Set<String> POS_Y=Set.of("posY","field_70163_u");
    private static final Set<String> POS_Z=Set.of("posZ","field_70161_v");
    private static final Set<String> SET_POSITION=Set.of("setPosition","func_70107_b");
    private static final String CORE_DESC="(L"+ENTITY_LIVING+";DDD)Z";

    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,
                        String enumField,int selectorId,String teleportMethod,
                        boolean savedPositionProven,boolean candidateAssignmentProven,
                        boolean guardedRollbackFalseProven,boolean successTrueReturnProven,
                        boolean stateSkeletonProven,List<String> blockers){
        public Proof{blockers=List.copyOf(blockers);}
    }
    public record Analysis(List<Proof> proofs,List<String> diagnostics){
        public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}
    }
    private record Saved(int x,int y,int z,int lastIndex){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);
        var wrappers=new LegacyVariantSnowballTeleportWrapperAnalyzer().analyze(jarPath);List<Proof> proofs=new ArrayList<>();
        for(var wrapper:wrappers.proofs())if(wrapper.wrapperProven()&&wrapper.teleportMethod()!=null)proofs.add(prove(wrapper));
        diagnostics.addAll(wrappers.diagnostics());return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballTeleportWrapperAnalyzer.Proof wrapper){
        List<String> blockers=new ArrayList<>();ClassNode projectile=classes.get(wrapper.projectileClass());MethodNode core=projectile==null?null:find(projectile,wrapper.teleportMethod(),CORE_DESC);
        if(core==null)return new Proof(wrapper.registryName(),wrapper.itemClass(),wrapper.projectileClass(),wrapper.selectorClass(),wrapper.enumField(),wrapper.selectorId(),wrapper.teleportMethod(),false,false,false,false,false,List.of("teleport-core-method-missing"));
        List<AbstractInsnNode> code=meaningful(core);Map<AbstractInsnNode,Integer> indices=indices(code);
        Saved saved=savedPosition(code);boolean savedOk=saved!=null;boolean candidate=savedOk&&candidateAssignmentsAfter(code,saved.lastIndex());
        Rollback rollback=savedOk?guardedRollback(code,indices,saved):null;boolean rollbackOk=rollback!=null;boolean trueReturn=rollbackOk&&successTrueReturn(code,indices,rollback.successLabel());
        if(!savedOk)blockers.add("target-original-position-save-not-proven");
        if(!candidate)blockers.add("candidate-position-direct-assignment-not-proven");
        if(!rollbackOk)blockers.add("guarded-exact-position-rollback-false-path-not-proven");
        if(!trueReturn)blockers.add("guarded-success-true-return-not-proven");
        return new Proof(wrapper.registryName(),wrapper.itemClass(),wrapper.projectileClass(),wrapper.selectorClass(),wrapper.enumField(),wrapper.selectorId(),wrapper.teleportMethod(),savedOk,candidate,rollbackOk,trueReturn,savedOk&&candidate&&rollbackOk&&trueReturn,blockers);
    }

    private static Saved savedPosition(List<AbstractInsnNode> code){
        Integer x=null,y=null,z=null;int last=-1;
        for(int i=0;i+2<code.size();i++){
            if(!aload(code.get(i),1)||!(code.get(i+1) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD||!"D".equals(field.desc))continue;
            if(!(code.get(i+2) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.DSTORE)continue;
            if(POS_X.contains(field.name)){if(x!=null)return null;x=store.var;last=Math.max(last,i+2);}
            else if(POS_Y.contains(field.name)){if(y!=null)return null;y=store.var;last=Math.max(last,i+2);}
            else if(POS_Z.contains(field.name)){if(z!=null)return null;z=store.var;last=Math.max(last,i+2);}
        }
        if(x==null||y==null||z==null||x.equals(y)||x.equals(z)||y.equals(z))return null;
        return new Saved(x,y,z,last);
    }

    private static boolean candidateAssignmentsAfter(List<AbstractInsnNode> code,int after){
        boolean x=false,y=false,z=false;
        for(int i=Math.max(0,after+1);i+2<code.size();i++){
            if(!aload(code.get(i),1)||!(code.get(i+1) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.DLOAD)continue;
            if(!(code.get(i+2) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD||!"D".equals(field.desc))continue;
            if(POS_X.contains(field.name)&&load.var==2)x=true;
            else if(POS_Y.contains(field.name)&&load.var==4)y=true;
            else if(POS_Z.contains(field.name)&&load.var==6)z=true;
        }
        return x&&y&&z;
    }

    private record Rollback(LabelNode successLabel,int rollbackStart,int falseReturn){ }
    private static Rollback guardedRollback(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices,Saved saved){
        for(int i=0;i+8<code.size();i++){
            if(!(code.get(i) instanceof VarInsnNode flag)||flag.getOpcode()!=Opcodes.ILOAD)continue;
            if(!(code.get(i+1) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFNE)continue;
            if(!aload(code.get(i+2),1)||!dload(code.get(i+3),saved.x())||!dload(code.get(i+4),saved.y())||!dload(code.get(i+5),saved.z()))continue;
            if(!(code.get(i+6) instanceof MethodInsnNode set)||set.getOpcode()!=Opcodes.INVOKEVIRTUAL||!SET_POSITION.contains(set.name)||!"(DDD)V".equals(set.desc))continue;
            if(intConstant(code.get(i+7))!=0||code.get(i+8).getOpcode()!=Opcodes.IRETURN)continue;
            Integer success=indices.get(nextMeaningful(jump.label));if(success!=null&&success>i+8)return new Rollback(jump.label,i+2,i+8);
        }
        return null;
    }

    private static boolean successTrueReturn(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices,LabelNode successLabel){
        Integer start=indices.get(nextMeaningful(successLabel));if(start==null)return false;
        for(int i=start;i+1<code.size();i++)if(intConstant(code.get(i))==1&&code.get(i+1).getOpcode()==Opcodes.IRETURN)return true;
        return false;
    }

    private static boolean aload(AbstractInsnNode insn,int local){return insn instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==local;}
    private static boolean dload(AbstractInsnNode insn,int local){return insn instanceof VarInsnNode v&&v.getOpcode()==Opcodes.DLOAD&&v.var==local;}
    private static int intConstant(AbstractInsnNode insn){int op=insn.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;if(insn instanceof IntInsnNode v&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return v.operand;if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v)return v;return Integer.MIN_VALUE;}
    private static Map<AbstractInsnNode,Integer> indices(List<AbstractInsnNode> code){Map<AbstractInsnNode,Integer> out=new IdentityHashMap<>();for(int i=0;i<code.size();i++)out.put(code.get(i),i);return out;}
    private static List<AbstractInsnNode> meaningful(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(!(insn instanceof LabelNode||insn instanceof LineNumberNode||insn instanceof FrameNode))out.add(insn);return out;}
    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node){while(node instanceof LabelNode||node instanceof LineNumberNode||node instanceof FrameNode)node=node.getNext();return node;}
    private static MethodNode find(ClassNode owner,String name,String desc){if(owner==null)return null;for(MethodNode method:owner.methods)if(name.equals(method.name)&&desc.equals(method.desc))return method;return null;}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball teleport-state class "+entry.getName()+": "+malformed.getClass().getSimpleName());}}}
    }
}
