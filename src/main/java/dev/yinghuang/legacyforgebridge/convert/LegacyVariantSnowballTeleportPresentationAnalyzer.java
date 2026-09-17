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

/** Bounded proof for success-only portal presentation in source-mapped variant-snowball teleports. */
public final class LegacyVariantSnowballTeleportPresentationAnalyzer {
    private static final String ENTITY="net/minecraft/entity/Entity",ENTITY_LIVING="net/minecraft/entity/EntityLiving",WORLD="net/minecraft/world/World",RANDOM="java/util/Random";
    private static final Set<String> POS_X=Set.of("posX","field_70165_t"),POS_Y=Set.of("posY","field_70163_u"),POS_Z=Set.of("posZ","field_70161_v"),WORLD_FIELD=Set.of("worldObj","field_70170_p"),RAND_FIELD=Set.of("rand","field_70146_Z"),WIDTH_FIELD=Set.of("width","field_70130_N"),HEIGHT_FIELD=Set.of("height","field_70131_O");
    private static final Set<String> SET_POSITION=Set.of("setPosition","func_70107_b"),SPAWN_PARTICLE=Set.of("spawnParticle","func_72869_a"),PLAY_SOUND_EFFECT=Set.of("playSoundEffect","func_72908_a"),PLAY_SOUND_ENTITY=Set.of("playSoundAtEntity","func_72956_a");
    private static final String CORE_DESC="(L"+ENTITY_LIVING+";DDD)Z",PARTICLE_DESC="(Ljava/lang/String;DDDDDD)V",ORIGIN_SOUND_DESC="(DDDLjava/lang/String;FF)V",ENTITY_SOUND_DESC="(L"+ENTITY+";Ljava/lang/String;FF)V";

    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,String enumField,int selectorId,String teleportMethod,
                        boolean portalParticleLoopProven,boolean interpolationProven,boolean randomizationProven,
                        boolean originPortalSoundProven,boolean entityPortalSoundProven,boolean successReturnAfterPresentationProven,
                        boolean presentationProven,List<String> blockers){public Proof{blockers=List.copyOf(blockers);}}
    public record Analysis(List<Proof> proofs,List<String> diagnostics){public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}}
    private record Saved(int x,int y,int z){ }
    private record Loop(int count,int index,int ratio,int start,int end,LabelNode loopLabel,LabelNode afterLabel){ }
    private record Velocity(int x,int y,int z){ }
    private record ParticleCoordinates(int x,int y,int z){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);var safety=new LegacyVariantSnowballTeleportSafetyAnalyzer().analyze(jarPath);List<Proof> proofs=new ArrayList<>();
        for(var safe:safety.proofs())if(safe.gameplaySafetyCoreProven())proofs.add(prove(safe));diagnostics.addAll(safety.diagnostics());return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballTeleportSafetyAnalyzer.Proof safe){
        ClassNode projectile=classes.get(safe.projectileClass());MethodNode core=projectile==null?null:find(projectile,safe.teleportMethod(),CORE_DESC);if(core==null)return empty(safe,"teleport-core-method-missing");
        List<AbstractInsnNode> code=meaningful(core);Map<AbstractInsnNode,Integer> idx=indices(code);Saved saved=savedPosition(code);Integer successStart=saved==null?null:successStart(code,idx,saved);List<String> blockers=new ArrayList<>();
        Loop loop=successStart==null?null:portalLoop(code,idx,successStart);boolean loopOk=loop!=null;Velocity velocity=loopOk?velocities(code,loop):null;ParticleCoordinates coords=loopOk?particleCoordinates(code,loop,saved):null;boolean interpolation=coords!=null,randomization=velocity!=null&&coords!=null;
        boolean particle=loopOk&&velocity!=null&&coords!=null&&particleCall(code,loop,velocity,coords);boolean origin=loopOk&&originSound(code,idx,loop.afterLabel(),saved);boolean entity=loopOk&&entitySound(code,idx,loop.afterLabel());boolean returnAfter=origin&&entity&&trueReturnAfterSounds(code,idx,loop.afterLabel());
        if(!particle)blockers.add("exact-128-portal-particle-loop-not-proven");if(!interpolation)blockers.add("saved-to-target-portal-interpolation-not-proven");if(!randomization)blockers.add("portal-rng-velocity-and-position-jitter-not-proven");if(!origin)blockers.add("origin-coordinate-portal-sound-not-proven");if(!entity)blockers.add("entity-path-portal-sound-not-proven");if(!returnAfter)blockers.add("true-return-after-portal-presentation-not-proven");
        return new Proof(safe.registryName(),safe.itemClass(),safe.projectileClass(),safe.selectorClass(),safe.enumField(),safe.selectorId(),safe.teleportMethod(),particle,interpolation,randomization,origin,entity,returnAfter,particle&&interpolation&&randomization&&origin&&entity&&returnAfter,blockers);
    }
    private static Proof empty(LegacyVariantSnowballTeleportSafetyAnalyzer.Proof s,String blocker){return new Proof(s.registryName(),s.itemClass(),s.projectileClass(),s.selectorClass(),s.enumField(),s.selectorId(),s.teleportMethod(),false,false,false,false,false,false,false,List.of(blocker));}

    private static Saved savedPosition(List<AbstractInsnNode> code){Integer x=null,y=null,z=null;for(int i=0;i+2<code.size();i++){if(!aload(code.get(i),1)||!(code.get(i+1) instanceof FieldInsnNode f)||f.getOpcode()!=Opcodes.GETFIELD||!"D".equals(f.desc)||!(code.get(i+2) instanceof VarInsnNode s)||s.getOpcode()!=Opcodes.DSTORE)continue;if(POS_X.contains(f.name)){if(x!=null)return null;x=s.var;}else if(POS_Y.contains(f.name)){if(y!=null)return null;y=s.var;}else if(POS_Z.contains(f.name)){if(z!=null)return null;z=s.var;}}return x!=null&&y!=null&&z!=null?new Saved(x,y,z):null;}

    private static Integer successStart(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,Saved saved){for(int i=0;i+8<code.size();i++){
        if(!(code.get(i) instanceof VarInsnNode flag)||flag.getOpcode()!=Opcodes.ILOAD||!(code.get(i+1) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFNE)continue;
        if(!aload(code.get(i+2),1)||!dload(code.get(i+3),saved.x())||!dload(code.get(i+4),saved.y())||!dload(code.get(i+5),saved.z()))continue;
        if(!(code.get(i+6) instanceof MethodInsnNode set)||set.getOpcode()!=Opcodes.INVOKEVIRTUAL||!SET_POSITION.contains(set.name)||!"(DDD)V".equals(set.desc)||intConstant(code.get(i+7))!=0||code.get(i+8).getOpcode()!=Opcodes.IRETURN)continue;
        Integer start=idx.get(nextMeaningful(jump.label));if(start!=null&&start>i+8)return start;
    }return null;}

    private static Loop portalLoop(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,int successStart){for(int i=successStart;i+7<code.size();i++){
        if(intConstant(code.get(i))!=128||!(code.get(i+1) instanceof VarInsnNode countStore)||countStore.getOpcode()!=Opcodes.ISTORE)continue;int count=countStore.var;
        if(intConstant(code.get(i+2))!=0||!(code.get(i+3) instanceof VarInsnNode indexStore)||indexStore.getOpcode()!=Opcodes.ISTORE)continue;int index=indexStore.var;
        if(!(code.get(i+4) instanceof VarInsnNode indexLoad)||indexLoad.getOpcode()!=Opcodes.ILOAD||indexLoad.var!=index||!(code.get(i+5) instanceof VarInsnNode countLoad)||countLoad.getOpcode()!=Opcodes.ILOAD||countLoad.var!=count||!(code.get(i+6) instanceof JumpInsnNode exit)||exit.getOpcode()!=Opcodes.IF_ICMPGE)continue;
        LabelNode loopLabel=previousLabel(code.get(i+4));Integer end=idx.get(nextMeaningful(exit.label));if(loopLabel==null||end==null||end<=i+7)continue;
        Integer ratio=ratioLocal(code,index,count,i+7,end);if(ratio==null)continue;boolean inc=false,back=false;for(int j=i+7;j<end;j++){if(code.get(j) instanceof IincInsnNode n&&n.var==index&&n.incr==1)inc=true;if(code.get(j) instanceof JumpInsnNode g&&g.getOpcode()==Opcodes.GOTO&&nextMeaningful(g.label)==code.get(i+4))back=true;}
        if(inc&&back)return new Loop(count,index,ratio,i+4,end,loopLabel,exit.label);
    }return null;}
    private static Integer ratioLocal(List<AbstractInsnNode> code,int index,int count,int from,int to){for(int i=from;i+7<to;i++)if(iload(code.get(i),index)&&code.get(i+1).getOpcode()==Opcodes.I2D&&iload(code.get(i+2),count)&&code.get(i+3).getOpcode()==Opcodes.I2D&&code.get(i+4).getOpcode()==Opcodes.DCONST_1&&code.get(i+5).getOpcode()==Opcodes.DSUB&&code.get(i+6).getOpcode()==Opcodes.DDIV&&code.get(i+7) instanceof VarInsnNode s&&s.getOpcode()==Opcodes.DSTORE)return s.var;return null;}

    private static Velocity velocities(List<AbstractInsnNode> code,Loop loop){List<Integer> locals=new ArrayList<>();for(int i=loop.start();i+7<loop.end();i++)if(aload(code.get(i),0)&&randomField(code.get(i+1))&&code.get(i+2) instanceof MethodInsnNode next&&next.getOpcode()==Opcodes.INVOKEVIRTUAL&&RANDOM.equals(next.owner)&&"nextFloat".equals(next.name)&&"()F".equals(next.desc)&&floatConstant(code.get(i+3))==0.5F&&code.get(i+4).getOpcode()==Opcodes.FSUB&&floatConstant(code.get(i+5))==0.2F&&code.get(i+6).getOpcode()==Opcodes.FMUL&&code.get(i+7) instanceof VarInsnNode s&&s.getOpcode()==Opcodes.FSTORE)locals.add(s.var);return locals.size()==3?new Velocity(locals.get(0),locals.get(1),locals.get(2)):null;}

    private static ParticleCoordinates particleCoordinates(List<AbstractInsnNode> code,Loop loop,Saved saved){Integer x=axisWithWidth(code,loop,saved.x(),POS_X),y=axisWithHeight(code,loop,saved.y()),z=axisWithWidth(code,loop,saved.z(),POS_Z);return x!=null&&y!=null&&z!=null?new ParticleCoordinates(x,y,z):null;}
    private static Integer axisWithWidth(List<AbstractInsnNode> code,Loop loop,int saved,Set<String> position){for(int i=loop.start();i+18<loop.end();i++){
        if(!dload(code.get(i),saved)||!aload(code.get(i+1),1)||!positionField(code.get(i+2),position)||!dload(code.get(i+3),saved)||code.get(i+4).getOpcode()!=Opcodes.DSUB||!dload(code.get(i+5),loop.ratio())||code.get(i+6).getOpcode()!=Opcodes.DMUL||code.get(i+7).getOpcode()!=Opcodes.DADD)continue;
        if(!aload(code.get(i+8),0)||!randomField(code.get(i+9))||!(code.get(i+10) instanceof MethodInsnNode next)||next.getOpcode()!=Opcodes.INVOKEVIRTUAL||!RANDOM.equals(next.owner)||!"nextDouble".equals(next.name)||!"()D".equals(next.desc)||doubleConstant(code.get(i+11))!=0.5D||code.get(i+12).getOpcode()!=Opcodes.DSUB)continue;
        if(!aload(code.get(i+13),1)||!floatField(code.get(i+14),WIDTH_FIELD)||code.get(i+15).getOpcode()!=Opcodes.F2D||code.get(i+16).getOpcode()!=Opcodes.DMUL||doubleConstant(code.get(i+17))!=2.0D||code.get(i+18).getOpcode()!=Opcodes.DMUL||i+19>=loop.end()||code.get(i+19).getOpcode()!=Opcodes.DADD||i+20>=loop.end()||!(code.get(i+20) instanceof VarInsnNode s)||s.getOpcode()!=Opcodes.DSTORE)continue;return s.var;
    }return null;}
    private static Integer axisWithHeight(List<AbstractInsnNode> code,Loop loop,int saved){for(int i=loop.start();i+15<loop.end();i++){
        if(!dload(code.get(i),saved)||!aload(code.get(i+1),1)||!positionField(code.get(i+2),POS_Y)||!dload(code.get(i+3),saved)||code.get(i+4).getOpcode()!=Opcodes.DSUB||!dload(code.get(i+5),loop.ratio())||code.get(i+6).getOpcode()!=Opcodes.DMUL||code.get(i+7).getOpcode()!=Opcodes.DADD)continue;
        if(!aload(code.get(i+8),0)||!randomField(code.get(i+9))||!(code.get(i+10) instanceof MethodInsnNode next)||next.getOpcode()!=Opcodes.INVOKEVIRTUAL||!RANDOM.equals(next.owner)||!"nextDouble".equals(next.name)||!"()D".equals(next.desc)||!aload(code.get(i+11),1)||!floatField(code.get(i+12),HEIGHT_FIELD)||code.get(i+13).getOpcode()!=Opcodes.F2D||code.get(i+14).getOpcode()!=Opcodes.DMUL||code.get(i+15).getOpcode()!=Opcodes.DADD||i+16>=loop.end()||!(code.get(i+16) instanceof VarInsnNode s)||s.getOpcode()!=Opcodes.DSTORE)continue;return s.var;
    }return null;}

    private static boolean particleCall(List<AbstractInsnNode> code,Loop loop,Velocity v,ParticleCoordinates p){for(int i=loop.start();i+14<loop.end();i++){
        if(!aload(code.get(i),1)||!worldField(code.get(i+1))||!(code.get(i+2) instanceof LdcInsnNode id)||!"portal".equals(id.cst)||!dload(code.get(i+3),p.x())||!dload(code.get(i+4),p.y())||!dload(code.get(i+5),p.z()))continue;
        if(!fload(code.get(i+6),v.x())||code.get(i+7).getOpcode()!=Opcodes.F2D||!fload(code.get(i+8),v.y())||code.get(i+9).getOpcode()!=Opcodes.F2D||!fload(code.get(i+10),v.z())||code.get(i+11).getOpcode()!=Opcodes.F2D)continue;
        if(code.get(i+12) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&SPAWN_PARTICLE.contains(call.name)&&PARTICLE_DESC.equals(call.desc))return true;
    }return false;}

    private static boolean originSound(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,LabelNode after,Saved saved){Integer start=idx.get(nextMeaningful(after));if(start==null)return false;for(int i=start;i+8<code.size();i++)if(aload(code.get(i),1)&&worldField(code.get(i+1))&&dload(code.get(i+2),saved.x())&&dload(code.get(i+3),saved.y())&&dload(code.get(i+4),saved.z())&&code.get(i+5) instanceof LdcInsnNode sound&&"mob.endermen.portal".equals(sound.cst)&&floatConstant(code.get(i+6))==1.0F&&floatConstant(code.get(i+7))==1.0F&&code.get(i+8) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&PLAY_SOUND_EFFECT.contains(call.name)&&ORIGIN_SOUND_DESC.equals(call.desc))return true;return false;}
    private static boolean entitySound(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,LabelNode after){Integer start=idx.get(nextMeaningful(after));if(start==null)return false;for(int i=start;i+6<code.size();i++)if(aload(code.get(i),1)&&worldField(code.get(i+1))&&aload(code.get(i+2),0)&&code.get(i+3) instanceof LdcInsnNode sound&&"mob.endermen.portal".equals(sound.cst)&&floatConstant(code.get(i+4))==1.0F&&floatConstant(code.get(i+5))==1.0F&&code.get(i+6) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&PLAY_SOUND_ENTITY.contains(call.name)&&ENTITY_SOUND_DESC.equals(call.desc))return true;return false;}
    private static boolean trueReturnAfterSounds(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,LabelNode after){Integer start=idx.get(nextMeaningful(after));if(start==null)return false;boolean origin=false,entity=false;for(int i=start;i<code.size();i++){if(code.get(i) instanceof MethodInsnNode call&&WORLD.equals(call.owner)&&PLAY_SOUND_EFFECT.contains(call.name)&&ORIGIN_SOUND_DESC.equals(call.desc))origin=true;if(code.get(i) instanceof MethodInsnNode call&&WORLD.equals(call.owner)&&PLAY_SOUND_ENTITY.contains(call.name)&&ENTITY_SOUND_DESC.equals(call.desc))entity=true;if(origin&&entity&&i+1<code.size()&&intConstant(code.get(i))==1&&code.get(i+1).getOpcode()==Opcodes.IRETURN)return true;}return false;}

    private static LabelNode previousLabel(AbstractInsnNode insn){for(AbstractInsnNode p=insn.getPrevious();p!=null&&(p instanceof LabelNode||p instanceof LineNumberNode||p instanceof FrameNode);p=p.getPrevious())if(p instanceof LabelNode l)return l;return null;}
    private static boolean aload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==l;}private static boolean dload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.DLOAD&&v.var==l;}private static boolean fload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.FLOAD&&v.var==l;}private static boolean iload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==l;}
    private static boolean worldField(AbstractInsnNode i){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&WORLD_FIELD.contains(f.name)&&("L"+WORLD+";").equals(f.desc);}private static boolean randomField(AbstractInsnNode i){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&RAND_FIELD.contains(f.name)&&("L"+RANDOM+";").equals(f.desc);}private static boolean positionField(AbstractInsnNode i,Set<String> names){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&names.contains(f.name)&&"D".equals(f.desc);}private static boolean floatField(AbstractInsnNode i,Set<String> names){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&names.contains(f.name)&&"F".equals(f.desc);}
    private static int intConstant(AbstractInsnNode i){int op=i.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;if(i instanceof IntInsnNode v&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return v.operand;if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v)return v;return Integer.MIN_VALUE;}private static double doubleConstant(AbstractInsnNode i){if(i.getOpcode()==Opcodes.DCONST_0)return 0D;if(i.getOpcode()==Opcodes.DCONST_1)return 1D;if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Double v)return v;return Double.NaN;}private static float floatConstant(AbstractInsnNode i){if(i.getOpcode()==Opcodes.FCONST_0)return 0F;if(i.getOpcode()==Opcodes.FCONST_1)return 1F;if(i.getOpcode()==Opcodes.FCONST_2)return 2F;if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Float v)return v;return Float.NaN;}
    private static Map<AbstractInsnNode,Integer> indices(List<AbstractInsnNode> code){Map<AbstractInsnNode,Integer> out=new IdentityHashMap<>();for(int i=0;i<code.size();i++)out.put(code.get(i),i);return out;}private static List<AbstractInsnNode> meaningful(MethodNode m){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(!(i instanceof LabelNode||i instanceof LineNumberNode||i instanceof FrameNode))out.add(i);return out;}private static AbstractInsnNode nextMeaningful(AbstractInsnNode n){while(n instanceof LabelNode||n instanceof LineNumberNode||n instanceof FrameNode)n=n.getNext();return n;}private static MethodNode find(ClassNode o,String n,String d){if(o==null)return null;for(MethodNode m:o.methods)if(n.equals(m.name)&&d.equals(m.desc))return m;return null;}
    private void load(Path jarPath)throws IOException{try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry e=entries.nextElement();if(e.isDirectory()||!e.getName().endsWith(".class")||e.getName().equals("module-info.class"))continue;try(InputStream in=jar.getInputStream(e)){ClassNode n=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(n.name,n);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball teleport-presentation class "+e.getName()+": "+malformed.getClass().getSimpleName());}}}}
}
