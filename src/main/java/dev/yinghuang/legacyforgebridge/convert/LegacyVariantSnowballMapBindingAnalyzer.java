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
 * Bounded proof that a metadata-indexed variant-snowball selector map is populated as
 * selector.getId() -> the same selector for every selector enum value.
 */
public final class LegacyVariantSnowballMapBindingAnalyzer {
    private static final String MAP="java/util/Map";
    private static final String HASH_MAP="java/util/HashMap";
    private static final String INTEGER="java/lang/Integer";
    private static final String ITEM_STACK="net/minecraft/item/ItemStack";
    private static final Set<String> DAMAGE_NAMES=Set.of("getItemDamage","func_77960_j");

    public record Proof(String registryName,String itemClass,String selectorClass,String mapOwner,String mapField,
                        boolean bindingProven,String blocker){ }
    public record Analysis(List<Proof> proofs,List<String> diagnostics){
        public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}
    }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);
        LegacyVariantSnowballAnalyzer.Analysis base=new LegacyVariantSnowballAnalyzer().analyze(jarPath);
        List<Proof> proofs=new ArrayList<>();
        for(LegacyVariantSnowballAnalyzer.Rule rule:base.rules()){
            String blocker=prove(rule);
            proofs.add(new Proof(rule.registryName(),rule.itemClass(),rule.selectorClass(),rule.selectorMapOwner(),rule.selectorMapField(),blocker==null,blocker));
        }
        diagnostics.addAll(base.diagnostics());
        return new Analysis(proofs,diagnostics);
    }

    private String prove(LegacyVariantSnowballAnalyzer.Rule rule){
        ClassNode owner=classes.get(rule.selectorMapOwner());
        if(owner==null)return "Missing selector-map owner class "+rule.selectorMapOwner()+".";
        FieldNode mapField=owner.fields.stream().filter(f->rule.selectorMapField().equals(f.name)
                &&("Ljava/util/Map;".equals(f.desc)||"Ljava/util/HashMap;".equals(f.desc))).findFirst().orElse(null);
        if(mapField==null)return "Selector-map field was not declared with the admitted Map/HashMap descriptor.";
        if((mapField.access&Opcodes.ACC_STATIC)==0||(mapField.access&Opcodes.ACC_PRIVATE)==0)
            return "Selector-map field is not private static, so bounded same-class mutation proof is unavailable.";
        MethodNode clinit=find(owner,"<clinit>","()V");
        if(clinit==null)return "Selector-map owner has no static initializer.";
        List<AbstractInsnNode> code=meaningful(clinit);
        int initializer=findInitializer(code,rule,mapField.desc);
        if(initializer<0)return "Selector map is not initialized exactly once from a fresh HashMap in the static initializer.";
        int loop=findPopulationLoop(code,rule);
        if(loop<0)return "Static initializer does not prove the canonical Enum.values() -> map.put(selector.getId(), selector) population loop.";
        if(!clinitMapAccessesBounded(code,rule,initializer,loop))
            return "Static initializer contains selector-map accesses outside the admitted population loop/read-only size path.";
        if(!sameClassUsesRemainReadOnly(owner,rule))
            return "Selector map has a same-class write, escape, or non-read-only use outside the admitted static population loop.";
        return null;
    }

    private static int findInitializer(List<AbstractInsnNode> code,LegacyVariantSnowballAnalyzer.Rule rule,String fieldDesc){
        int writes=0,match=-1;
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(!(insn instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTSTATIC||!sameMap(field,rule))continue;
            writes++;
            if(i<3)continue;
            AbstractInsnNode a=code.get(i-3),b=code.get(i-2),c=code.get(i-1);
            if(a instanceof TypeInsnNode created&&created.getOpcode()==Opcodes.NEW&&HASH_MAP.equals(created.desc)
                    &&b.getOpcode()==Opcodes.DUP
                    &&c instanceof MethodInsnNode ctor&&ctor.getOpcode()==Opcodes.INVOKESPECIAL&&HASH_MAP.equals(ctor.owner)
                    &&"<init>".equals(ctor.name)&&"()V".equals(ctor.desc)&&fieldDesc.equals(field.desc))match=i;
        }
        return writes==1?match:-1;
    }

    private static int findPopulationLoop(List<AbstractInsnNode> code,LegacyVariantSnowballAnalyzer.Rule rule){
        int matches=0,match=-1;
        for(int i=0;i+23<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode values)||values.getOpcode()!=Opcodes.INVOKESTATIC
                    ||!rule.selectorClass().equals(values.owner)||!"values".equals(values.name)
                    ||!("()[L"+rule.selectorClass()+";").equals(values.desc))continue;
            if(!(code.get(i+1) instanceof VarInsnNode arrayStore)||arrayStore.getOpcode()!=Opcodes.ASTORE)continue;
            int arrayLocal=arrayStore.var;
            if(!(code.get(i+2) instanceof VarInsnNode arrayLoad)||arrayLoad.getOpcode()!=Opcodes.ALOAD||arrayLoad.var!=arrayLocal)continue;
            if(code.get(i+3).getOpcode()!=Opcodes.ARRAYLENGTH)continue;
            if(!(code.get(i+4) instanceof VarInsnNode lengthStore)||lengthStore.getOpcode()!=Opcodes.ISTORE)continue;
            int lengthLocal=lengthStore.var;
            if(code.get(i+5).getOpcode()!=Opcodes.ICONST_0)continue;
            if(!(code.get(i+6) instanceof VarInsnNode indexStore)||indexStore.getOpcode()!=Opcodes.ISTORE)continue;
            int indexLocal=indexStore.var;
            if(!(code.get(i+7) instanceof VarInsnNode indexLoad)||indexLoad.getOpcode()!=Opcodes.ILOAD||indexLoad.var!=indexLocal)continue;
            if(!(code.get(i+8) instanceof VarInsnNode lengthLoad)||lengthLoad.getOpcode()!=Opcodes.ILOAD||lengthLoad.var!=lengthLocal)continue;
            if(!(code.get(i+9) instanceof JumpInsnNode exit)||exit.getOpcode()!=Opcodes.IF_ICMPGE)continue;
            if(!(code.get(i+10) instanceof VarInsnNode bodyArray)||bodyArray.getOpcode()!=Opcodes.ALOAD||bodyArray.var!=arrayLocal)continue;
            if(!(code.get(i+11) instanceof VarInsnNode bodyIndex)||bodyIndex.getOpcode()!=Opcodes.ILOAD||bodyIndex.var!=indexLocal)continue;
            if(code.get(i+12).getOpcode()!=Opcodes.AALOAD)continue;
            if(!(code.get(i+13) instanceof VarInsnNode selectorStore)||selectorStore.getOpcode()!=Opcodes.ASTORE)continue;
            int selectorLocal=selectorStore.var;
            if(!(code.get(i+14) instanceof FieldInsnNode mapGet)||mapGet.getOpcode()!=Opcodes.GETSTATIC||!sameMap(mapGet,rule))continue;
            if(!(code.get(i+15) instanceof VarInsnNode selectorForId)||selectorForId.getOpcode()!=Opcodes.ALOAD||selectorForId.var!=selectorLocal)continue;
            if(!(code.get(i+16) instanceof MethodInsnNode idGetter)||idGetter.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!rule.selectorClass().equals(idGetter.owner)||!rule.selectorIdGetter().equals(idGetter.name)||!"()I".equals(idGetter.desc))continue;
            if(!(code.get(i+17) instanceof MethodInsnNode box)||box.getOpcode()!=Opcodes.INVOKESTATIC||!INTEGER.equals(box.owner)
                    ||!"valueOf".equals(box.name)||!"(I)Ljava/lang/Integer;".equals(box.desc))continue;
            if(!(code.get(i+18) instanceof VarInsnNode selectorValue)||selectorValue.getOpcode()!=Opcodes.ALOAD||selectorValue.var!=selectorLocal)continue;
            if(!(code.get(i+19) instanceof MethodInsnNode put)||!isMapApiOwner(put.owner)||!"put".equals(put.name)
                    ||!"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(put.desc))continue;
            if(code.get(i+20).getOpcode()!=Opcodes.POP)continue;
            if(!(code.get(i+21) instanceof IincInsnNode increment)||increment.var!=indexLocal||increment.incr!=1)continue;
            if(!(code.get(i+22) instanceof JumpInsnNode back)||back.getOpcode()!=Opcodes.GOTO)continue;
            if(nextMeaningful(back.label)!=code.get(i+7)||nextMeaningful(exit.label)!=code.get(i+23))continue;
            matches++;match=i;
        }
        return matches==1?match:-1;
    }

    private static boolean clinitMapAccessesBounded(List<AbstractInsnNode> code,LegacyVariantSnowballAnalyzer.Rule rule,int initializer,int loop){
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(!(insn instanceof FieldInsnNode field)||!sameMap(field,rule))continue;
            if(field.getOpcode()==Opcodes.PUTSTATIC){if(i!=initializer)return false;continue;}
            if(field.getOpcode()!=Opcodes.GETSTATIC)return false;
            if(i==loop+14)continue;
            if(i+1<code.size()&&code.get(i+1) instanceof MethodInsnNode call&&isMapApiOwner(call.owner)
                    &&"size".equals(call.name)&&"()I".equals(call.desc))continue;
            return false;
        }
        return true;
    }

    private static boolean sameClassUsesRemainReadOnly(ClassNode owner,LegacyVariantSnowballAnalyzer.Rule rule){
        for(MethodNode method:owner.methods){
            if("<clinit>".equals(method.name))continue;
            List<AbstractInsnNode> code=meaningful(method);
            for(int i=0;i<code.size();i++){
                AbstractInsnNode insn=code.get(i);
                if(!(insn instanceof FieldInsnNode field)||!sameMap(field,rule))continue;
                if(field.getOpcode()!=Opcodes.GETSTATIC||!boundedRead(code,i))return false;
            }
        }
        return true;
    }

    private static boolean boundedRead(List<AbstractInsnNode> code,int fieldIndex){
        int end=Math.min(code.size(),fieldIndex+12);
        for(int i=fieldIndex+1;i<end;i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof MethodInsnNode call){
                if(isMapApiOwner(call.owner))
                    return ("get".equals(call.name)&&"(Ljava/lang/Object;)Ljava/lang/Object;".equals(call.desc))
                            ||("size".equals(call.name)&&"()I".equals(call.desc));
                if(allowedKeyProducer(call))continue;
                return false;
            }
            int opcode=insn.getOpcode();
            if(opcode==Opcodes.ASTORE||opcode==Opcodes.ARETURN||opcode==Opcodes.PUTFIELD||opcode==Opcodes.PUTSTATIC)return false;
        }
        return false;
    }

    private static boolean allowedKeyProducer(MethodInsnNode call){
        return call.getOpcode()==Opcodes.INVOKESTATIC&&INTEGER.equals(call.owner)&&"valueOf".equals(call.name)&&"(I)Ljava/lang/Integer;".equals(call.desc)
                || ITEM_STACK.equals(call.owner)&&DAMAGE_NAMES.contains(call.name)&&"()I".equals(call.desc);
    }

    private static boolean sameMap(FieldInsnNode field,LegacyVariantSnowballAnalyzer.Rule rule){
        return rule.selectorMapOwner().equals(field.owner)&&rule.selectorMapField().equals(field.name)
                &&("Ljava/util/Map;".equals(field.desc)||"Ljava/util/HashMap;".equals(field.desc));
    }
    private static boolean isMapApiOwner(String owner){return MAP.equals(owner)||HASH_MAP.equals(owner);}
    private static MethodNode find(ClassNode owner,String name,String desc){for(MethodNode method:owner.methods)if(name.equals(method.name)&&desc.equals(method.desc))return method;return null;}
    private static List<AbstractInsnNode> meaningful(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(!(insn instanceof LabelNode||insn instanceof LineNumberNode||insn instanceof FrameNode))out.add(insn);return out;}
    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node){while(node instanceof LabelNode||node instanceof LineNumberNode||node instanceof FrameNode)node=node.getNext();return node;}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball map-binding class "+entry.getName()+": "+malformed.getClass().getSimpleName());}}}
    }
}
