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

/** Bounded proof for the selector-independent impact shell of metadata-indexed custom snowballs. */
public final class LegacyVariantSnowballImpactAnalyzer {
    private static final String MOVING="net/minecraft/util/MovingObjectPosition";
    private static final String ENTITY="net/minecraft/entity/Entity";
    private static final String LIVING="net/minecraft/entity/EntityLivingBase";
    private static final String WORLD="net/minecraft/world/World";
    private static final String DAMAGE_SOURCE="net/minecraft/util/DamageSource";
    private static final Set<String> KILL_NAMES=Set.of("kill","func_70076_C");
    private static final Set<String> GET_THROWER_NAMES=Set.of("getThrower","func_85052_h");
    private static final Set<String> THROWN_DAMAGE_NAMES=Set.of("causeThrownDamage","func_76356_a");
    private static final Set<String> ATTACK_NAMES=Set.of("attackEntityFrom","func_70097_a");
    private static final Set<String> PARTICLE_NAMES=Set.of("spawnParticle","func_72869_a");
    private static final Set<String> SET_DEAD_NAMES=Set.of("setDead","func_70106_y");
    private static final Set<String> REMOTE_FIELDS=Set.of("isRemote","field_72995_K");
    private static final Set<String> WORLD_FIELDS=Set.of("worldObj","field_70170_p");
    private static final Set<String> POS_X_FIELDS=Set.of("posX","field_70165_t");
    private static final Set<String> POS_Y_FIELDS=Set.of("posY","field_70163_u");
    private static final Set<String> POS_Z_FIELDS=Set.of("posZ","field_70161_v");
    private static final String ATTACK_DESC="(L"+DAMAGE_SOURCE+";F)Z";
    private static final String THROW_DESC="(L"+ENTITY+";L"+ENTITY+";)L"+DAMAGE_SOURCE+";";
    private static final String PARTICLE_DESC="(Ljava/lang/String;DDDDDD)V";

    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,
                        boolean selectorNullGuardProven,boolean selectorBaseDamageAttackProven,
                        boolean snowballPoofLoopProven,boolean serverTerminationProven,
                        boolean commonImpactSemanticsProven,List<String> blockers){
        public Proof{blockers=List.copyOf(blockers);}
    }
    public record Analysis(List<Proof> proofs,List<String> diagnostics){
        public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}
    }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);
        LegacyVariantSnowballAnalyzer.Analysis base=new LegacyVariantSnowballAnalyzer().analyze(jarPath);
        List<Proof> proofs=new ArrayList<>();
        for(LegacyVariantSnowballAnalyzer.Rule rule:base.rules())proofs.add(prove(rule));
        diagnostics.addAll(base.diagnostics());
        return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballAnalyzer.Rule rule){
        List<String> blockers=new ArrayList<>();
        ClassNode projectile=classes.get(rule.projectileClass());
        MethodNode impact=projectile==null?null:find(projectile,rule.impactMethod(),rule.impactDescriptor());
        FieldNode selector=projectile==null?null:uniqueSelectorField(projectile,rule.selectorClass());
        boolean nullGuard=false,damage=false,particle=false,termination=false;
        if(projectile==null||impact==null||selector==null){
            blockers.add("missing-projectile-impact-or-unique-selector-field");
        }else{
            List<AbstractInsnNode> code=meaningful(impact);Map<AbstractInsnNode,Integer> indices=indices(code);
            nullGuard=proveNullGuard(code,indices,projectile,selector);
            damage=proveBaseDamageAttack(code,indices,projectile,selector,rule.selectorClass(),rule.selectorDamageGetter());
            particle=proveSnowballPoofLoop(code,indices);
            termination=proveServerTermination(code);
            if(!nullGuard)blockers.add("selector-null-kill-guard-not-proven");
            if(!damage)blockers.add("selector-base-damage-entity-hit-path-not-proven");
            if(!particle)blockers.add("eight-snowballpoof-self-position-loop-not-proven");
            if(!termination)blockers.add("server-side-projectile-termination-not-proven");
        }
        boolean common=nullGuard&&damage&&particle&&termination;
        return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),
                nullGuard,damage,particle,termination,common,blockers);
    }

    private static FieldNode uniqueSelectorField(ClassNode projectile,String selectorClass){
        String desc="L"+selectorClass+";";FieldNode found=null;
        for(FieldNode field:projectile.fields){
            if((field.access&Opcodes.ACC_STATIC)!=0||!desc.equals(field.desc))continue;
            if(found!=null)return null;found=field;
        }
        return found;
    }

    private static boolean proveNullGuard(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices,ClassNode projectile,FieldNode selector){
        for(int i=0;i+5<code.size();i++){
            if(!aload(code.get(i),0)||!selectorField(code.get(i+1),projectile,selector))continue;
            if(!(code.get(i+2) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFNONNULL)continue;
            if(!aload(code.get(i+3),0))continue;
            if(!(code.get(i+4) instanceof MethodInsnNode kill)||kill.getOpcode()!=Opcodes.INVOKEVIRTUAL||!KILL_NAMES.contains(kill.name)||!"()V".equals(kill.desc))continue;
            if(code.get(i+5).getOpcode()!=Opcodes.RETURN)continue;
            AbstractInsnNode target=nextMeaningful(jump.label);Integer targetIndex=indices.get(target);
            if(targetIndex!=null&&targetIndex>i+5)return true;
        }
        return false;
    }

    private static boolean proveBaseDamageAttack(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices,ClassNode projectile,FieldNode selector,String selectorClass,String damageGetter){
        for(int i=0;i+3<code.size();i++){
            if(!aload(code.get(i),0)||!selectorField(code.get(i+1),projectile,selector))continue;
            if(!(code.get(i+2) instanceof MethodInsnNode getter)||getter.getOpcode()!=Opcodes.INVOKEVIRTUAL||!selectorClass.equals(getter.owner)||!damageGetter.equals(getter.name)||!"()B".equals(getter.desc))continue;
            if(!(code.get(i+3) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.ISTORE)continue;
            int damageLocal=store.var;
            for(int a=i+4;a+8<code.size();a++){
                if(!aload(code.get(a),1))continue;
                if(!(code.get(a+1) instanceof FieldInsnNode hit)||hit.getOpcode()!=Opcodes.GETFIELD||!MOVING.equals(hit.owner)||!("L"+ENTITY+";").equals(hit.desc))continue;
                if(!aload(code.get(a+2),0)||!aload(code.get(a+3),0))continue;
                if(!(code.get(a+4) instanceof MethodInsnNode thrower)||thrower.getOpcode()!=Opcodes.INVOKEVIRTUAL||!GET_THROWER_NAMES.contains(thrower.name)||!("()L"+LIVING+";").equals(thrower.desc))continue;
                if(!(code.get(a+5) instanceof MethodInsnNode thrown)||thrown.getOpcode()!=Opcodes.INVOKESTATIC||!DAMAGE_SOURCE.equals(thrown.owner)||!THROWN_DAMAGE_NAMES.contains(thrown.name)||!THROW_DESC.equals(thrown.desc))continue;
                if(!(code.get(a+6) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ILOAD||load.var!=damageLocal||code.get(a+7).getOpcode()!=Opcodes.I2F)continue;
                if(!(code.get(a+8) instanceof MethodInsnNode attack)||attack.getOpcode()!=Opcodes.INVOKEVIRTUAL||!ATTACK_NAMES.contains(attack.name)||!ATTACK_DESC.equals(attack.desc))continue;
                if(hasEntityHitNullGuard(code,indices,a,hit.name))return true;
            }
        }
        return false;
    }

    private static boolean hasEntityHitNullGuard(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices,int attackStart,String hitField){
        for(int i=0;i+2<attackStart;i++){
            if(!aload(code.get(i),1))continue;
            if(!(code.get(i+1) instanceof FieldInsnNode hit)||hit.getOpcode()!=Opcodes.GETFIELD||!MOVING.equals(hit.owner)||!hitField.equals(hit.name)||!("L"+ENTITY+";").equals(hit.desc))continue;
            if(!(code.get(i+2) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFNULL)continue;
            Integer exit=indices.get(nextMeaningful(jump.label));if(exit!=null&&exit>attackStart)return true;
        }
        return false;
    }

    private static boolean proveSnowballPoofLoop(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices){
        for(int i=2;i+3<code.size();i++){
            if(!(code.get(i) instanceof VarInsnNode indexLoad)||indexLoad.getOpcode()!=Opcodes.ILOAD)continue;
            if(intConstant(code.get(i+1))!=8)continue;
            if(!(code.get(i+2) instanceof JumpInsnNode exit)||exit.getOpcode()!=Opcodes.IF_ICMPGE)continue;
            if(intConstant(code.get(i-2))!=0)continue;
            if(!(code.get(i-1) instanceof VarInsnNode init)||init.getOpcode()!=Opcodes.ISTORE||init.var!=indexLoad.var)continue;
            Integer end=indices.get(nextMeaningful(exit.label));if(end==null||end<=i+3)continue;
            boolean spawn=false,increment=false,back=false;
            for(int j=i+3;j<end;j++){
                if(code.get(j) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&PARTICLE_NAMES.contains(call.name)&&PARTICLE_DESC.equals(call.desc)
                        &&selfPositionSnowballPoof(code,j))spawn=true;
                if(code.get(j) instanceof IincInsnNode inc&&inc.var==indexLoad.var&&inc.incr==1)increment=true;
                if(code.get(j) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.GOTO&&nextMeaningful(jump.label)==code.get(i))back=true;
            }
            if(spawn&&increment&&back)return true;
        }
        return false;
    }

    private static boolean selfPositionSnowballPoof(List<AbstractInsnNode> code,int call){
        if(call<12)return false;
        return aload(code.get(call-12),0)&&worldField(code.get(call-11))
                &&code.get(call-10) instanceof LdcInsnNode ldc&&"snowballpoof".equals(ldc.cst)
                &&aload(code.get(call-9),0)&&positionField(code.get(call-8),POS_X_FIELDS)
                &&aload(code.get(call-7),0)&&positionField(code.get(call-6),POS_Y_FIELDS)
                &&aload(code.get(call-5),0)&&positionField(code.get(call-4),POS_Z_FIELDS)
                &&code.get(call-3).getOpcode()==Opcodes.DCONST_0&&code.get(call-2).getOpcode()==Opcodes.DCONST_0&&code.get(call-1).getOpcode()==Opcodes.DCONST_0;
    }

    private static boolean proveServerTermination(List<AbstractInsnNode> code){
        for(int i=0;i+5<code.size();i++){
            if(!aload(code.get(i),0)||!worldField(code.get(i+1)))continue;
            if(!(code.get(i+2) instanceof FieldInsnNode remote)||remote.getOpcode()!=Opcodes.GETFIELD||!WORLD.equals(remote.owner)||!REMOTE_FIELDS.contains(remote.name)||!"Z".equals(remote.desc))continue;
            if(!(code.get(i+3) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFNE)continue;
            if(!aload(code.get(i+4),0))continue;
            if(code.get(i+5) instanceof MethodInsnNode dead&&dead.getOpcode()==Opcodes.INVOKEVIRTUAL&&SET_DEAD_NAMES.contains(dead.name)&&"()V".equals(dead.desc))return true;
        }
        return false;
    }

    private static boolean selectorField(AbstractInsnNode insn,ClassNode projectile,FieldNode selector){
        return insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&projectile.name.equals(field.owner)&&selector.name.equals(field.name)&&selector.desc.equals(field.desc);
    }
    private static boolean worldField(AbstractInsnNode insn){return insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&WORLD_FIELDS.contains(field.name)&&("L"+WORLD+";").equals(field.desc);}
    private static boolean positionField(AbstractInsnNode insn,Set<String> names){return insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&names.contains(field.name)&&"D".equals(field.desc);}
    private static boolean aload(AbstractInsnNode insn,int local){return insn instanceof VarInsnNode var&&var.getOpcode()==Opcodes.ALOAD&&var.var==local;}
    private static int intConstant(AbstractInsnNode insn){
        int op=insn.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;
        if(insn instanceof IntInsnNode value&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return value.operand;
        if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value)return value;return Integer.MIN_VALUE;
    }
    private static Map<AbstractInsnNode,Integer> indices(List<AbstractInsnNode> code){Map<AbstractInsnNode,Integer> out=new IdentityHashMap<>();for(int i=0;i<code.size();i++)out.put(code.get(i),i);return out;}
    private static List<AbstractInsnNode> meaningful(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(!(insn instanceof LabelNode||insn instanceof LineNumberNode||insn instanceof FrameNode))out.add(insn);return out;}
    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node){while(node instanceof LabelNode||node instanceof LineNumberNode||node instanceof FrameNode)node=node.getNext();return node;}
    private static MethodNode find(ClassNode owner,String name,String desc){for(MethodNode method:owner.methods)if(name.equals(method.name)&&desc.equals(method.desc))return method;return null;}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball impact class "+entry.getName()+": "+malformed.getClass().getSimpleName());}}}
    }
}
