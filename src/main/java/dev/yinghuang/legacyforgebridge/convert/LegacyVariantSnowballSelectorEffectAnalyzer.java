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

/** Proves selector-enum switch dispatch and bounded per-selector impact effects. */
public final class LegacyVariantSnowballSelectorEffectAnalyzer {
    private static final String MOVING="net/minecraft/util/MovingObjectPosition";
    private static final String ENTITY="net/minecraft/entity/Entity";
    private static final String ENTITY_LIVING="net/minecraft/entity/EntityLiving";
    private static final String ENTITY_LIVING_BASE="net/minecraft/entity/EntityLivingBase";
    private static final String POTION="net/minecraft/potion/Potion";
    private static final String POTION_EFFECT="net/minecraft/potion/PotionEffect";
    private static final Set<String> POTION_ID_NAMES=Set.of("getId","func_76396_c");
    private static final Set<String> ADD_POTION_NAMES=Set.of("addPotionEffect","func_70690_d");
    private static final Map<String,String> POTION_FIELDS=Map.of(
            "poison","poison","field_76436_u","poison",
            "confusion","confusion","field_76431_k","confusion",
            "regeneration","regeneration","field_76428_l","regeneration");

    public record VariantEffect(String enumField,int selectorId,String impactEffect,String potion,
                                Integer duration,Integer amplifier,String helperMethod){ }
    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,
                        boolean selectorDispatchProven,boolean selectorEffectEdgesProven,
                        boolean selectorSpecificImpactSemanticsComplete,int potionBranchesProven,
                        List<VariantEffect> effects,List<String> blockers){
        public Proof{effects=List.copyOf(effects);blockers=List.copyOf(blockers);}
    }
    public record Analysis(List<Proof> proofs,List<String> diagnostics){
        public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}
    }

    private record ArraySource(String owner,String field,String helper){ }
    private record SwitchShape(AbstractInsnNode switchInsn,ArraySource source,Map<Integer,LabelNode> cases,
                               LabelNode defaultLabel,int targetLocal,LabelNode joinLabel,boolean defaultNoOp){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);
        LegacyVariantSnowballAnalyzer.Analysis base=new LegacyVariantSnowballAnalyzer().analyze(jarPath);
        List<Proof> proofs=new ArrayList<>();
        for(var rule:base.rules())proofs.add(prove(rule));
        diagnostics.addAll(base.diagnostics());
        return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballAnalyzer.Rule rule){
        List<String> blockers=new ArrayList<>();
        ClassNode projectile=classes.get(rule.projectileClass());
        MethodNode impact=projectile==null?null:find(projectile,rule.impactMethod(),rule.impactDescriptor());
        FieldNode selector=projectile==null?null:uniqueSelectorField(projectile,rule.selectorClass());
        if(projectile==null||impact==null||selector==null)
            return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),false,false,false,0,List.of(),List.of("missing-projectile-impact-or-unique-selector-field"));

        List<AbstractInsnNode> code=meaningful(impact);Map<AbstractInsnNode,Integer> indices=indices(code);
        SwitchShape shape=findSelectorSwitch(projectile,selector,rule.selectorClass(),code,indices);
        if(shape==null)
            return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),false,false,false,0,List.of(),List.of("selector-enum-switch-dispatch-not-proven"));

        Map<String,Integer> enumToCase=recoverEnumCaseMap(shape.source(),rule.selectorClass());
        Set<Integer> caseKeys=new LinkedHashSet<>(shape.cases().keySet());
        if(enumToCase.isEmpty()||!new LinkedHashSet<>(enumToCase.values()).equals(caseKeys)){
            blockers.add("selector-switch-case-table-not-source-bound");
            return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),false,false,false,0,List.of(),blockers);
        }
        if(!shape.defaultNoOp()){
            blockers.add("selector-switch-default-not-proven-noop");
            return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),true,false,false,0,List.of(),blockers);
        }

        Map<Integer,int[]> ranges=caseRanges(shape,code,indices);
        List<VariantEffect> effects=new ArrayList<>();int potionCount=0;boolean allEdges=true,allComplete=true;
        for(var variant:rule.variants()){
            Integer key=enumToCase.get(variant.enumField());
            VariantEffect effect;
            if(key==null){
                effect=new VariantEffect(variant.enumField(),variant.id(),"NONE",null,null,null,null);
            }else{
                int[] range=ranges.get(key);
                effect=range==null?null:classifyBranch(projectile,rule.selectorClass(),variant,shape.targetLocal(),code,range[0],range[1]);
                if(effect==null){
                    allEdges=false;allComplete=false;
                    effect=new VariantEffect(variant.enumField(),variant.id(),"UNCOMPILED",null,null,null,null);
                }else if("POTION".equals(effect.impactEffect()))potionCount++;
                else if("CUSTOM_HELPER_UNCOMPILED".equals(effect.impactEffect()))allComplete=false;
            }
            effects.add(effect);
        }
        effects.sort(Comparator.comparingInt(VariantEffect::selectorId));
        if(!allEdges)blockers.add("one-or-more-selector-case-effects-not-proven");
        boolean dispatch=true;
        return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),dispatch,allEdges,allEdges&&allComplete,potionCount,effects,blockers);
    }

    private SwitchShape findSelectorSwitch(ClassNode projectile,FieldNode selector,String selectorClass,
                                           List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices){
        for(int i=5;i<code.size();i++){
            AbstractInsnNode switchInsn=code.get(i);
            if(!(switchInsn instanceof TableSwitchInsnNode)&&!(switchInsn instanceof LookupSwitchInsnNode))continue;
            if(code.get(i-1).getOpcode()!=Opcodes.IALOAD)continue;
            if(!(code.get(i-2) instanceof MethodInsnNode ordinal)||ordinal.getOpcode()!=Opcodes.INVOKEVIRTUAL||!"ordinal".equals(ordinal.name)||!"()I".equals(ordinal.desc)
                    ||!(selectorClass.equals(ordinal.owner)||"java/lang/Enum".equals(ordinal.owner)))continue;
            if(!selectorField(code.get(i-3),projectile,selector)||!aload(code.get(i-4),0))continue;
            ArraySource source=arraySource(code.get(i-5));if(source==null)continue;
            Map<Integer,LabelNode> cases=cases(switchInsn);LabelNode dflt=defaultLabel(switchInsn);
            if(cases.isEmpty()||dflt==null)continue;
            int targetLocal=livingTargetLocal(code,i,indices);if(targetLocal<0)continue;
            LabelNode join=commonJoin(cases,dflt,code,indices);if(join==null)continue;
            boolean defaultNoOp=defaultNoOp(dflt,join,code,indices,cases);
            return new SwitchShape(switchInsn,source,cases,dflt,targetLocal,join,defaultNoOp);
        }
        return null;
    }

    private static ArraySource arraySource(AbstractInsnNode insn){
        if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&"[I".equals(field.desc))
            return new ArraySource(field.owner,field.name,null);
        if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&"()[I".equals(call.desc))
            return new ArraySource(call.owner,null,call.name);
        return null;
    }

    private Map<String,Integer> recoverEnumCaseMap(ArraySource source,String selectorClass){
        ClassNode owner=classes.get(source.owner());if(owner==null)return Map.of();
        MethodNode method=source.field()!=null?find(owner,"<clinit>","()V"):find(owner,source.helper(),"()[I");
        if(method==null)return Map.of();
        List<AbstractInsnNode> code=meaningful(method);Map<String,Integer> result=new LinkedHashMap<>();Set<Integer> keys=new HashSet<>();
        for(int i=0;i+4<code.size();i++){
            AbstractInsnNode array=code.get(i);
            boolean arrayOk;
            if(source.field()!=null)arrayOk=array instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&source.owner().equals(f.owner)&&source.field().equals(f.name)&&"[I".equals(f.desc);
            else arrayOk=array.getOpcode()==Opcodes.DUP||(array instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD)||(array instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&"[I".equals(f.desc));
            if(!arrayOk)continue;
            if(!(code.get(i+1) instanceof FieldInsnNode enumField)||enumField.getOpcode()!=Opcodes.GETSTATIC||!selectorClass.equals(enumField.owner)||!("L"+selectorClass+";").equals(enumField.desc))continue;
            if(!(code.get(i+2) instanceof MethodInsnNode ordinal)||ordinal.getOpcode()!=Opcodes.INVOKEVIRTUAL||!"ordinal".equals(ordinal.name)||!"()I".equals(ordinal.desc))continue;
            int key=intConstant(code.get(i+3));if(key==Integer.MIN_VALUE||key<=0||code.get(i+4).getOpcode()!=Opcodes.IASTORE)continue;
            if(result.putIfAbsent(enumField.name,key)!=null||!keys.add(key))return Map.of();
        }
        return Map.copyOf(result);
    }

    private static Map<Integer,LabelNode> cases(AbstractInsnNode switchInsn){
        LinkedHashMap<Integer,LabelNode> result=new LinkedHashMap<>();
        if(switchInsn instanceof LookupSwitchInsnNode lookup){
            for(int i=0;i<lookup.keys.size();i++)if(lookup.labels.get(i)!=lookup.dflt)result.put(lookup.keys.get(i),lookup.labels.get(i));
        }else if(switchInsn instanceof TableSwitchInsnNode table){
            for(int i=0;i<table.labels.size();i++)if(table.labels.get(i)!=table.dflt)result.put(table.min+i,table.labels.get(i));
        }
        return result;
    }
    private static LabelNode defaultLabel(AbstractInsnNode switchInsn){return switchInsn instanceof LookupSwitchInsnNode l?l.dflt:((TableSwitchInsnNode)switchInsn).dflt;}

    private static int livingTargetLocal(List<AbstractInsnNode> code,int switchIndex,Map<AbstractInsnNode,Integer> indices){
        for(int i=Math.max(0,switchIndex-20);i+4<switchIndex;i++){
            if(!aload(code.get(i),1)||!(code.get(i+1) instanceof FieldInsnNode hit)||hit.getOpcode()!=Opcodes.GETFIELD||!MOVING.equals(hit.owner)||!("L"+ENTITY+";").equals(hit.desc))continue;
            if(!(code.get(i+2) instanceof TypeInsnNode cast)||cast.getOpcode()!=Opcodes.CHECKCAST||!ENTITY_LIVING.equals(cast.desc))continue;
            if(!(code.get(i+3) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.ASTORE)continue;
            if(hasLivingGuard(code,i,indices,hit.name,switchIndex))return store.var;
        }
        return -1;
    }
    private static boolean hasLivingGuard(List<AbstractInsnNode> code,int castStart,Map<AbstractInsnNode,Integer> indices,String hitField,int switchIndex){
        for(int i=Math.max(0,castStart-12);i+3<castStart;i++){
            if(!aload(code.get(i),1)||!(code.get(i+1) instanceof FieldInsnNode hit)||hit.getOpcode()!=Opcodes.GETFIELD||!MOVING.equals(hit.owner)||!hitField.equals(hit.name))continue;
            if(!(code.get(i+2) instanceof TypeInsnNode type)||type.getOpcode()!=Opcodes.INSTANCEOF||!ENTITY_LIVING.equals(type.desc))continue;
            if(!(code.get(i+3) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFEQ)continue;
            Integer target=indices.get(nextMeaningful(jump.label));if(target!=null&&target>switchIndex)return true;
        }
        return false;
    }

    private static LabelNode commonJoin(Map<Integer,LabelNode> cases,LabelNode dflt,List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices){
        List<Integer> starts=new ArrayList<>();for(LabelNode label:cases.values()){Integer index=indices.get(nextMeaningful(label));if(index!=null)starts.add(index);}Integer def=indices.get(nextMeaningful(dflt));if(def!=null)starts.add(def);starts.sort(Integer::compareTo);
        LabelNode common=null;
        for(LabelNode label:cases.values()){
            Integer start=indices.get(nextMeaningful(label));if(start==null)return null;int end=nextBoundary(start,starts,code.size());LabelNode target=null;
            for(int i=start;i<end;i++)if(code.get(i) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.GOTO)target=jump.label;
            if(target==null)return null;if(common==null)common=target;else if(common!=target)return null;
        }
        return common;
    }
    private static boolean defaultNoOp(LabelNode dflt,LabelNode join,List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices,Map<Integer,LabelNode> cases){
        AbstractInsnNode defInsn=nextMeaningful(dflt),joinInsn=nextMeaningful(join);if(defInsn==joinInsn)return true;
        Integer start=indices.get(defInsn);if(start==null)return false;List<Integer> starts=new ArrayList<>();for(LabelNode label:cases.values()){Integer idx=indices.get(nextMeaningful(label));if(idx!=null)starts.add(idx);}Integer joinIndex=indices.get(joinInsn);if(joinIndex!=null)starts.add(joinIndex);starts.sort(Integer::compareTo);int end=nextBoundary(start,starts,code.size());
        return end==start+1&&code.get(start) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.GOTO&&nextMeaningful(jump.label)==joinInsn;
    }

    private static Map<Integer,int[]> caseRanges(SwitchShape shape,List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> indices){
        List<Integer> boundaries=new ArrayList<>();for(LabelNode label:shape.cases().values()){Integer idx=indices.get(nextMeaningful(label));if(idx!=null)boundaries.add(idx);}Integer def=indices.get(nextMeaningful(shape.defaultLabel()));if(def!=null)boundaries.add(def);Integer join=indices.get(nextMeaningful(shape.joinLabel()));if(join!=null)boundaries.add(join);boundaries.sort(Integer::compareTo);
        Map<Integer,int[]> result=new LinkedHashMap<>();for(var entry:shape.cases().entrySet()){Integer start=indices.get(nextMeaningful(entry.getValue()));if(start!=null)result.put(entry.getKey(),new int[]{start,nextBoundary(start,boundaries,code.size())});}return result;
    }
    private static int nextBoundary(int start,List<Integer> boundaries,int fallback){for(int value:boundaries)if(value>start)return value;return fallback;}

    private static VariantEffect classifyBranch(ClassNode projectile,String selectorClass,LegacyVariantSnowballAnalyzer.Variant variant,int targetLocal,List<AbstractInsnNode> code,int start,int end){
        for(int i=start;i+8<end;i++){
            if(!aload(code.get(i),targetLocal)||!(code.get(i+1) instanceof TypeInsnNode allocation)||allocation.getOpcode()!=Opcodes.NEW||!POTION_EFFECT.equals(allocation.desc)||code.get(i+2).getOpcode()!=Opcodes.DUP)continue;
            if(!(code.get(i+3) instanceof FieldInsnNode potion)||potion.getOpcode()!=Opcodes.GETSTATIC||!POTION.equals(potion.owner)||!("L"+POTION+";").equals(potion.desc))continue;
            String logical=POTION_FIELDS.get(potion.name);if(logical==null)continue;
            if(!(code.get(i+4) instanceof MethodInsnNode id)||id.getOpcode()!=Opcodes.INVOKEVIRTUAL||!POTION.equals(id.owner)||!POTION_ID_NAMES.contains(id.name)||!"()I".equals(id.desc))continue;
            int duration=intConstant(code.get(i+5)),amplifier=intConstant(code.get(i+6));if(duration<0||amplifier<0)continue;
            if(!(code.get(i+7) instanceof MethodInsnNode ctor)||ctor.getOpcode()!=Opcodes.INVOKESPECIAL||!POTION_EFFECT.equals(ctor.owner)||!"<init>".equals(ctor.name)||!"(III)V".equals(ctor.desc))continue;
            if(!(code.get(i+8) instanceof MethodInsnNode add)||add.getOpcode()!=Opcodes.INVOKEVIRTUAL||!(ENTITY_LIVING.equals(add.owner)||ENTITY_LIVING_BASE.equals(add.owner))||!ADD_POTION_NAMES.contains(add.name)||!("(L"+POTION_EFFECT+";)V").equals(add.desc))continue;
            return new VariantEffect(variant.enumField(),variant.id(),"POTION",logical,duration,amplifier,null);
        }
        for(int i=start;i+3<end;i++){
            if(!aload(code.get(i),0)||!aload(code.get(i+1),targetLocal))continue;
            if(code.get(i+2) instanceof MethodInsnNode helper&&(helper.getOpcode()==Opcodes.INVOKESPECIAL||helper.getOpcode()==Opcodes.INVOKEVIRTUAL)
                    &&projectile.name.equals(helper.owner)&&!(helper.name.equals("<init>"))&&("(L"+ENTITY_LIVING+";)Z").equals(helper.desc)
                    &&code.get(i+3).getOpcode()==Opcodes.POP)
                return new VariantEffect(variant.enumField(),variant.id(),"CUSTOM_HELPER_UNCOMPILED",null,null,null,helper.name);
        }
        return null;
    }

    private static FieldNode uniqueSelectorField(ClassNode projectile,String selectorClass){String desc="L"+selectorClass+";";FieldNode found=null;for(FieldNode field:projectile.fields){if((field.access&Opcodes.ACC_STATIC)!=0||!desc.equals(field.desc))continue;if(found!=null)return null;found=field;}return found;}
    private static boolean selectorField(AbstractInsnNode insn,ClassNode projectile,FieldNode selector){return insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&projectile.name.equals(field.owner)&&selector.name.equals(field.name)&&selector.desc.equals(field.desc);}
    private static boolean aload(AbstractInsnNode insn,int local){return insn instanceof VarInsnNode var&&var.getOpcode()==Opcodes.ALOAD&&var.var==local;}
    private static int intConstant(AbstractInsnNode insn){int op=insn.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;if(insn instanceof IntInsnNode v&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return v.operand;if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v)return v;return Integer.MIN_VALUE;}
    private static Map<AbstractInsnNode,Integer> indices(List<AbstractInsnNode> code){Map<AbstractInsnNode,Integer> out=new IdentityHashMap<>();for(int i=0;i<code.size();i++)out.put(code.get(i),i);return out;}
    private static List<AbstractInsnNode> meaningful(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(!(insn instanceof LabelNode||insn instanceof LineNumberNode||insn instanceof FrameNode))out.add(insn);return out;}
    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node){while(node instanceof LabelNode||node instanceof LineNumberNode||node instanceof FrameNode)node=node.getNext();return node;}
    private static MethodNode find(ClassNode owner,String name,String desc){if(owner==null)return null;for(MethodNode method:owner.methods)if(name.equals(method.name)&&desc.equals(method.desc))return method;return null;}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball selector-effect class "+entry.getName()+": "+malformed.getClass().getSimpleName());}}}
    }
}
