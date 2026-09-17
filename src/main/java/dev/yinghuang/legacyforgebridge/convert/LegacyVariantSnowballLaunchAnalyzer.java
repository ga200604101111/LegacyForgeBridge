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

/** Proves the bounded item-use launch shell around an admitted metadata-indexed snowball spawn. */
public final class LegacyVariantSnowballLaunchAnalyzer {
    private static final String ITEM="net/minecraft/item/Item",STACK="net/minecraft/item/ItemStack",WORLD="net/minecraft/world/World",ENTITY="net/minecraft/entity/Entity",PLAYER="net/minecraft/entity/player/EntityPlayer",CAPS="net/minecraft/entity/player/PlayerCapabilities",RANDOM="java/util/Random";
    private static final Set<String> USE_NAMES=Set.of("onItemRightClick","func_77659_a"),CAPABILITY_FIELDS=Set.of("capabilities","field_71075_bZ"),CREATIVE_FIELDS=Set.of("isCreativeMode","field_75098_d"),STACK_SIZE_FIELDS=Set.of("stackSize","field_77994_a"),REMOTE_FIELDS=Set.of("isRemote","field_72995_K"),ITEM_RAND_FIELDS=Set.of("itemRand","field_77697_d"),SOUND_NAMES=Set.of("playSoundAtEntity","func_72956_a"),SPAWN_NAMES=Set.of("spawnEntityInWorld","func_72838_d");
    private static final String USE_DESC="(L"+STACK+";L"+WORLD+";L"+PLAYER+";)L"+STACK+";",SOUND_DESC="(L"+ENTITY+";Ljava/lang/String;FF)V",SPAWN_DESC="(L"+ENTITY+";)Z";

    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,
                        boolean creativeConsumptionGuardProven,boolean serverOnlyLaunchGateProven,
                        boolean legacyBowSoundProven,boolean metadataProjectileSpawnInsideGateProven,
                        boolean originalStackReturnProven,boolean itemUseSemanticsComplete,List<String> blockers){public Proof{blockers=List.copyOf(blockers);}}
    public record Analysis(List<Proof> proofs,List<String> diagnostics){public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}}
    private record ServerGate(int bodyStart,int exit){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);var base=new LegacyVariantSnowballAnalyzer().analyze(jarPath);List<Proof> proofs=new ArrayList<>();for(var rule:base.rules())proofs.add(prove(rule));diagnostics.addAll(base.diagnostics());return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballAnalyzer.Rule rule){
        ClassNode item=classes.get(rule.itemClass());MethodNode use=item==null?null:find(item,USE_NAMES,USE_DESC);if(use==null)return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),false,false,false,false,false,false,List.of("custom-item-use-method-missing"));
        List<AbstractInsnNode> code=meaningful(use);Map<AbstractInsnNode,Integer> idx=indices(code);boolean creative=creativeConsumption(code,idx);ServerGate gate=serverGate(code,idx);boolean server=gate!=null;boolean sound=server&&legacyBowSound(code,gate);boolean spawn=server&&projectileSpawn(code,gate,rule.projectileClass(),rule.selectorClass());boolean returned=originalStackReturn(code,gate);
        List<String> blockers=new ArrayList<>();if(!creative)blockers.add("creative-mode-stack-consumption-guard-not-proven");if(!server)blockers.add("server-only-launch-gate-not-proven");if(!sound)blockers.add("legacy-random-bow-sound-not-proven");if(!spawn)blockers.add("metadata-projectile-spawn-not-contained-in-server-gate");if(!returned)blockers.add("original-item-stack-return-not-proven");
        return new Proof(rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass(),creative,server,sound,spawn,returned,creative&&server&&sound&&spawn&&returned,blockers);
    }

    private static boolean creativeConsumption(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx){
        for(int i=0;i+9<code.size();i++){
            if(!aload(code.get(i),3)||!(code.get(i+1) instanceof FieldInsnNode caps)||caps.getOpcode()!=Opcodes.GETFIELD||!PLAYER.equals(caps.owner)||!CAPABILITY_FIELDS.contains(caps.name)||!("L"+CAPS+";").equals(caps.desc))continue;
            if(!(code.get(i+2) instanceof FieldInsnNode creative)||creative.getOpcode()!=Opcodes.GETFIELD||!CAPS.equals(creative.owner)||!CREATIVE_FIELDS.contains(creative.name)||!"Z".equals(creative.desc))continue;
            if(!(code.get(i+3) instanceof JumpInsnNode skip)||skip.getOpcode()!=Opcodes.IFNE)continue;
            if(!aload(code.get(i+4),1)||code.get(i+5).getOpcode()!=Opcodes.DUP||!stackSizeField(code.get(i+6),Opcodes.GETFIELD)||intConstant(code.get(i+7))!=1||code.get(i+8).getOpcode()!=Opcodes.ISUB||!stackSizeField(code.get(i+9),Opcodes.PUTFIELD))continue;
            Integer target=idx.get(nextMeaningful(skip.label));if(target!=null&&target>i+9)return true;
        }
        return false;
    }

    private static ServerGate serverGate(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx){for(int i=0;i+2<code.size();i++){
        if(!aload(code.get(i),2)||!(code.get(i+1) instanceof FieldInsnNode remote)||remote.getOpcode()!=Opcodes.GETFIELD||!WORLD.equals(remote.owner)||!REMOTE_FIELDS.contains(remote.name)||!"Z".equals(remote.desc))continue;
        if(!(code.get(i+2) instanceof JumpInsnNode exit)||exit.getOpcode()!=Opcodes.IFNE)continue;Integer end=idx.get(nextMeaningful(exit.label));if(end!=null&&end>i+3)return new ServerGate(i+3,end);
    }return null;}

    private boolean legacyBowSound(List<AbstractInsnNode> code,ServerGate gate){for(int i=gate.bodyStart();i+12<gate.exit();i++){
        if(!aload(code.get(i),2)||!aload(code.get(i+1),3)||!(code.get(i+2) instanceof LdcInsnNode sound)||!"random.bow".equals(sound.cst)||Float.compare(floatConstant(code.get(i+3)),0.5F)!=0||Float.compare(floatConstant(code.get(i+4)),0.4F)!=0)continue;
        if(!(code.get(i+5) instanceof FieldInsnNode rand)||rand.getOpcode()!=Opcodes.GETSTATIC||!ITEM_RAND_FIELDS.contains(rand.name)||!("L"+RANDOM+";").equals(rand.desc)||!reaches(rand.owner,ITEM))continue;
        if(!(code.get(i+6) instanceof MethodInsnNode next)||next.getOpcode()!=Opcodes.INVOKEVIRTUAL||!RANDOM.equals(next.owner)||!"nextFloat".equals(next.name)||!"()F".equals(next.desc)||Float.compare(floatConstant(code.get(i+7)),0.4F)!=0||code.get(i+8).getOpcode()!=Opcodes.FMUL||Float.compare(floatConstant(code.get(i+9)),0.8F)!=0||code.get(i+10).getOpcode()!=Opcodes.FADD||code.get(i+11).getOpcode()!=Opcodes.FDIV)continue;
        if(code.get(i+12) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&SOUND_NAMES.contains(call.name)&&SOUND_DESC.equals(call.desc))return true;
    }return false;}

    private static boolean projectileSpawn(List<AbstractInsnNode> code,ServerGate gate,String projectile,String selector){for(int i=gate.bodyStart();i+1<gate.exit();i++){
        if(!(code.get(i) instanceof MethodInsnNode ctor)||ctor.getOpcode()!=Opcodes.INVOKESPECIAL||!"<init>".equals(ctor.name)||!projectile.equals(ctor.owner)||!("(L"+WORLD+";Lnet/minecraft/entity/EntityLivingBase;L"+selector+";)V").equals(ctor.desc))continue;
        if(code.get(i+1) instanceof MethodInsnNode spawn&&spawn.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(spawn.owner)&&SPAWN_NAMES.contains(spawn.name)&&SPAWN_DESC.equals(spawn.desc))return true;
    }return false;}

    private static boolean originalStackReturn(List<AbstractInsnNode> code,ServerGate gate){int start=gate==null?0:Math.max(0,gate.exit());for(int i=start;i+1<code.size();i++)if(aload(code.get(i),1)&&code.get(i+1).getOpcode()==Opcodes.ARETURN)return true;return false;}
    private boolean reaches(String source,String target){String current=source;Set<String> seen=new HashSet<>();while(current!=null&&seen.add(current)){if(target.equals(current))return true;ClassNode node=classes.get(current);if(node==null)return false;current=node.superName;}return false;}
    private static boolean stackSizeField(AbstractInsnNode i,int opcode){return i instanceof FieldInsnNode f&&f.getOpcode()==opcode&&STACK.equals(f.owner)&&STACK_SIZE_FIELDS.contains(f.name)&&"I".equals(f.desc);}
    private static boolean aload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==l;}
    private static int intConstant(AbstractInsnNode i){int op=i.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;if(i instanceof IntInsnNode v&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return v.operand;if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v)return v;return Integer.MIN_VALUE;}
    private static float floatConstant(AbstractInsnNode i){if(i.getOpcode()==Opcodes.FCONST_0)return 0F;if(i.getOpcode()==Opcodes.FCONST_1)return 1F;if(i.getOpcode()==Opcodes.FCONST_2)return 2F;if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Float v)return v;return Float.NaN;}
    private static Map<AbstractInsnNode,Integer> indices(List<AbstractInsnNode> code){Map<AbstractInsnNode,Integer> out=new IdentityHashMap<>();for(int i=0;i<code.size();i++)out.put(code.get(i),i);return out;}private static List<AbstractInsnNode> meaningful(MethodNode m){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(!(i instanceof LabelNode||i instanceof LineNumberNode||i instanceof FrameNode))out.add(i);return out;}private static AbstractInsnNode nextMeaningful(AbstractInsnNode n){while(n instanceof LabelNode||n instanceof LineNumberNode||n instanceof FrameNode)n=n.getNext();return n;}
    private static MethodNode find(ClassNode owner,Set<String> names,String desc){if(owner==null)return null;for(MethodNode method:owner.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private void load(Path jarPath)throws IOException{try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry e=entries.nextElement();if(e.isDirectory()||!e.getName().endsWith(".class")||e.getName().equals("module-info.class"))continue;try(InputStream in=jar.getInputStream(e)){ClassNode n=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(n.name,n);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball launch class "+e.getName()+": "+malformed.getClass().getSimpleName());}}}}
}
