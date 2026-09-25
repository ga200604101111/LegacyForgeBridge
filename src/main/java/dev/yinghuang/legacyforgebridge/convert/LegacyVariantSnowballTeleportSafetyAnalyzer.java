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

/** Bounded proof tying a source-mapped teleport success flag to ground and collision/liquid safety. */
public final class LegacyVariantSnowballTeleportSafetyAnalyzer {
    private static final String ENTITY="net/minecraft/entity/Entity",ENTITY_LIVING="net/minecraft/entity/EntityLiving",WORLD="net/minecraft/world/World",BLOCK="net/minecraft/block/Block",BLOCKS="net/minecraft/init/Blocks",MATERIAL="net/minecraft/block/material/Material",AABB="net/minecraft/util/AxisAlignedBB",MATH="net/minecraft/util/MathHelper";
    private static final Set<String> POS_X=Set.of("posX","field_70165_t"),POS_Y=Set.of("posY","field_70163_u"),POS_Z=Set.of("posZ","field_70161_v"),WORLD_FIELD=Set.of("worldObj","field_70170_p"),BOX_FIELD=Set.of("boundingBox","field_70121_D");
    private static final Set<String> FLOOR=Set.of("floor_double","func_76128_c"),BLOCK_EXISTS=Set.of("blockExists","func_72899_e"),GET_BLOCK=Set.of("getBlock","func_147439_a"),GET_MATERIAL=Set.of("getMaterial","func_149688_o"),BLOCKS_MOVEMENT=Set.of("blocksMovement","func_76230_c"),COLLISIONS=Set.of("getCollidingBoundingBoxes","func_72945_a"),ANY_LIQUID=Set.of("isAnyLiquid","func_72953_d"),SET_POSITION=Set.of("setPosition","func_70107_b"),AIR_FIELDS=Set.of("air","field_150350_a");
    private static final String CORE_DESC="(L"+ENTITY_LIVING+";DDD)Z";

    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,String enumField,int selectorId,String teleportMethod,
                        boolean flooredCoordinatesProven,boolean blockExistsGateProven,boolean downwardGroundSearchProven,
                        boolean groundGuardedRepositionProven,boolean collisionEmptyGateProven,boolean nonLiquidGateProven,
                        boolean successFlagBindingProven,boolean gameplaySafetyCoreProven,List<String> blockers){public Proof{blockers=List.copyOf(blockers);}}
    public record Analysis(List<Proof> proofs,List<String> diagnostics){public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}}
    private record Saved(int x,int y,int z){ }
    private record Floors(int x,int y,int z,int lastIndex){ }
    private record SuccessGuard(int flag,LabelNode successLabel,AbstractInsnNode failureStart,int guardIndex){ }
    private record Ground(int flag,int blockLocal,LabelNode loop,LabelNode after,int startIndex){ }
    private record Reposition(int index){ }
    private record Safety(boolean collision,boolean nonLiquid,boolean flagSet){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);var states=new LegacyVariantSnowballTeleportStateAnalyzer().analyze(jarPath);List<Proof> proofs=new ArrayList<>();
        for(var state:states.proofs())if(state.stateSkeletonProven())proofs.add(prove(state));diagnostics.addAll(states.diagnostics());return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballTeleportStateAnalyzer.Proof state){
        List<String> blockers=new ArrayList<>();ClassNode projectile=classes.get(state.projectileClass());MethodNode core=projectile==null?null:find(projectile,state.teleportMethod(),CORE_DESC);
        if(core==null)return empty(state,"teleport-core-method-missing");
        List<AbstractInsnNode> code=meaningful(core);Map<AbstractInsnNode,Integer> idx=indices(code);Saved saved=savedPosition(code);Floors floors=floors(code);SuccessGuard success=saved==null?null:successGuard(code,idx,saved);
        boolean floor=floors!=null;int blockIndex=floor&&success!=null?blockExistsGate(code,success.failureStart(),floors):-1;boolean block=blockIndex>=0;
        Ground ground=block?groundSearch(code,success.failureStart(),floors,blockIndex):null;boolean groundOk=ground!=null;
        Reposition reposition=groundOk?groundGuardedReposition(code,idx,success.failureStart(),ground):null;boolean repositionOk=reposition!=null;
        Safety safety=repositionOk?safetyGates(code,success.failureStart(),success.flag(),reposition.index(),success.guardIndex()):null;
        boolean collision=safety!=null&&safety.collision(),liquid=safety!=null&&safety.nonLiquid(),flag=safety!=null&&safety.flagSet();
        if(!floor)blockers.add("candidate-floor-coordinate-proof-missing");if(!block)blockers.add("block-exists-failure-gate-proof-missing");if(!groundOk)blockers.add("downward-solid-ground-search-proof-missing");if(!repositionOk)blockers.add("ground-guarded-target-reposition-proof-missing");if(!collision)blockers.add("collision-empty-success-gate-proof-missing");if(!liquid)blockers.add("non-liquid-success-gate-proof-missing");if(!flag)blockers.add("success-flag-binding-proof-missing");
        return new Proof(state.registryName(),state.itemClass(),state.projectileClass(),state.selectorClass(),state.enumField(),state.selectorId(),state.teleportMethod(),floor,block,groundOk,repositionOk,collision,liquid,flag,floor&&block&&groundOk&&repositionOk&&collision&&liquid&&flag,blockers);
    }
    private static Proof empty(LegacyVariantSnowballTeleportStateAnalyzer.Proof s,String blocker){return new Proof(s.registryName(),s.itemClass(),s.projectileClass(),s.selectorClass(),s.enumField(),s.selectorId(),s.teleportMethod(),false,false,false,false,false,false,false,false,List.of(blocker));}

    private static Saved savedPosition(List<AbstractInsnNode> code){Integer x=null,y=null,z=null;for(int i=0;i+2<code.size();i++){
        if(!aload(code.get(i),1)||!(code.get(i+1) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD||!"D".equals(field.desc)||!(code.get(i+2) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.DSTORE)continue;
        if(POS_X.contains(field.name)){if(x!=null)return null;x=store.var;}else if(POS_Y.contains(field.name)){if(y!=null)return null;y=store.var;}else if(POS_Z.contains(field.name)){if(z!=null)return null;z=store.var;}
    }return x!=null&&y!=null&&z!=null&&x!=y&&x!=z&&y!=z?new Saved(x,y,z):null;}

    private static Floors floors(List<AbstractInsnNode> code){Integer x=null,y=null,z=null;int last=-1;for(int i=0;i+3<code.size();i++){
        if(!aload(code.get(i),1)||!(code.get(i+1) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD||!"D".equals(field.desc))continue;
        if(!(code.get(i+2) instanceof MethodInsnNode floor)||floor.getOpcode()!=Opcodes.INVOKESTATIC||!MATH.equals(floor.owner)||!FLOOR.contains(floor.name)||!"(D)I".equals(floor.desc))continue;
        if(!(code.get(i+3) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.ISTORE)continue;
        if(POS_X.contains(field.name))x=store.var;else if(POS_Y.contains(field.name))y=store.var;else if(POS_Z.contains(field.name))z=store.var;else continue;last=Math.max(last,i+3);
    }return x!=null&&y!=null&&z!=null&&x!=y&&x!=z&&y!=z?new Floors(x,y,z,last):null;}

    private static SuccessGuard successGuard(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,Saved saved){for(int i=0;i+8<code.size();i++){
        if(!(code.get(i) instanceof VarInsnNode flag)||flag.getOpcode()!=Opcodes.ILOAD||!(code.get(i+1) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFNE)continue;
        if(!aload(code.get(i+2),1)||!dload(code.get(i+3),saved.x())||!dload(code.get(i+4),saved.y())||!dload(code.get(i+5),saved.z()))continue;
        if(!(code.get(i+6) instanceof MethodInsnNode set)||set.getOpcode()!=Opcodes.INVOKEVIRTUAL||!SET_POSITION.contains(set.name)||!"(DDD)V".equals(set.desc)||intConstant(code.get(i+7))!=0||code.get(i+8).getOpcode()!=Opcodes.IRETURN)continue;
        Integer success=idx.get(nextMeaningful(jump.label));if(success!=null&&success>i+8)return new SuccessGuard(flag.var,jump.label,code.get(i),i);
    }return null;}

    private static int blockExistsGate(List<AbstractInsnNode> code,AbstractInsnNode failure,Floors f){for(int i=f.lastIndex()+1;i+6<code.size();i++){
        if(!aload(code.get(i),1)||!worldField(code.get(i+1))||!iload(code.get(i+2),f.x())||!iload(code.get(i+3),f.y())||!iload(code.get(i+4),f.z()))continue;
        if(!(code.get(i+5) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL||!WORLD.equals(call.owner)||!BLOCK_EXISTS.contains(call.name)||!"(III)Z".equals(call.desc))continue;
        if(code.get(i+6) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFEQ&&nextMeaningful(jump.label)==failure)return i;
    }return -1;}

    private static Ground groundSearch(List<AbstractInsnNode> code,AbstractInsnNode failure,Floors f,int afterIndex){for(int i=Math.max(afterIndex+7,0);i+5<code.size();i++){
        if(intConstant(code.get(i))!=0||!(code.get(i+1) instanceof VarInsnNode init)||init.getOpcode()!=Opcodes.ISTORE)continue;int ground=init.var;
        if(!(code.get(i+2) instanceof VarInsnNode g)||g.getOpcode()!=Opcodes.ILOAD||g.var!=ground||!(code.get(i+3) instanceof JumpInsnNode done1)||done1.getOpcode()!=Opcodes.IFNE)continue;
        if(!(code.get(i+4) instanceof VarInsnNode y)||y.getOpcode()!=Opcodes.ILOAD||y.var!=f.y()||!(code.get(i+5) instanceof JumpInsnNode done2)||done2.getOpcode()!=Opcodes.IFLE||done1.label!=done2.label)continue;
        LabelNode loop=previousLabel(code.get(i+2));int body=i+6;if(loop==null||body+8>=code.size())continue;
        if(!aload(code.get(body),1)||!worldField(code.get(body+1))||!iload(code.get(body+2),f.x())||!iload(code.get(body+3),f.y())||intConstant(code.get(body+4))!=1||code.get(body+5).getOpcode()!=Opcodes.ISUB||!iload(code.get(body+6),f.z()))continue;
        if(!(code.get(body+7) instanceof MethodInsnNode get)||get.getOpcode()!=Opcodes.INVOKEVIRTUAL||!WORLD.equals(get.owner)||!GET_BLOCK.contains(get.name)||!"(III)Lnet/minecraft/block/Block;".equals(get.desc)||!(code.get(body+8) instanceof VarInsnNode bs)||bs.getOpcode()!=Opcodes.ASTORE)continue;
        int blockLocal=bs.var;boolean solid=false,decrement=false,setsGround=false,back=false;
        for(int j=body+9;j<code.size()&&code.get(j)!=nextMeaningful(done1.label);j++){
            if(j+6<code.size()&&aload(code.get(j),blockLocal)&&code.get(j+1) instanceof FieldInsnNode air&&air.getOpcode()==Opcodes.GETSTATIC&&BLOCKS.equals(air.owner)&&AIR_FIELDS.contains(air.name)&&("L"+BLOCK+";").equals(air.desc)&&code.get(j+2) instanceof JumpInsnNode airJump&&airJump.getOpcode()==Opcodes.IF_ACMPEQ&&aload(code.get(j+3),blockLocal)&&code.get(j+4) instanceof MethodInsnNode mat&&mat.getOpcode()==Opcodes.INVOKEVIRTUAL&&BLOCK.equals(mat.owner)&&GET_MATERIAL.contains(mat.name)&&("()L"+MATERIAL+";").equals(mat.desc)&&code.get(j+5) instanceof MethodInsnNode move&&move.getOpcode()==Opcodes.INVOKEVIRTUAL&&MATERIAL.equals(move.owner)&&BLOCKS_MOVEMENT.contains(move.name)&&"()Z".equals(move.desc)&&code.get(j+6) instanceof JumpInsnNode movementJump&&movementJump.getOpcode()==Opcodes.IFEQ&&movementJump.label==airJump.label)solid=true;
            if(j+1<code.size()&&intConstant(code.get(j))==1&&code.get(j+1) instanceof VarInsnNode set&&set.getOpcode()==Opcodes.ISTORE&&set.var==ground)setsGround=true;
            if(j+6<code.size()&&aload(code.get(j),1)&&code.get(j+1).getOpcode()==Opcodes.DUP&&code.get(j+2) instanceof FieldInsnNode py&&py.getOpcode()==Opcodes.GETFIELD&&POS_Y.contains(py.name)&&"D".equals(py.desc)&&code.get(j+3).getOpcode()==Opcodes.DCONST_1&&code.get(j+4).getOpcode()==Opcodes.DSUB&&code.get(j+5) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&POS_Y.contains(put.name)&&"D".equals(put.desc)&&code.get(j+6) instanceof IincInsnNode inc&&inc.var==f.y()&&inc.incr==-1)decrement=true;
            if(code.get(j) instanceof JumpInsnNode backJump&&backJump.getOpcode()==Opcodes.GOTO&&nextMeaningful(backJump.label)==code.get(i+2))back=true;
        }
        if(solid&&setsGround&&decrement&&back)return new Ground(ground,blockLocal,loop,done1.label,i);
    }return null;}

    private static Reposition groundGuardedReposition(List<AbstractInsnNode> code,Map<AbstractInsnNode,Integer> idx,AbstractInsnNode failure,Ground g){Integer start=idx.get(nextMeaningful(g.after()));if(start==null)return null;for(int i=start;i+9<code.size()&&i<start+24;i++){
        if(!(code.get(i) instanceof VarInsnNode flag)||flag.getOpcode()!=Opcodes.ILOAD||flag.var!=g.flag()||!(code.get(i+1) instanceof JumpInsnNode skip)||skip.getOpcode()!=Opcodes.IFEQ||nextMeaningful(skip.label)!=failure)continue;
        if(!aload(code.get(i+2),1)||!aload(code.get(i+3),1)||!positionField(code.get(i+4),POS_X)||!aload(code.get(i+5),1)||!positionField(code.get(i+6),POS_Y)||!aload(code.get(i+7),1)||!positionField(code.get(i+8),POS_Z))continue;
        if(code.get(i+9) instanceof MethodInsnNode set&&set.getOpcode()==Opcodes.INVOKEVIRTUAL&&SET_POSITION.contains(set.name)&&"(DDD)V".equals(set.desc))return new Reposition(i+9);
    }return null;}

    private static Safety safetyGates(List<AbstractInsnNode> code,AbstractInsnNode failure,int successFlag,int afterIndex,int beforeIndex){int collisionAt=-1,liquidAt=-1,setAt=-1;for(int i=Math.max(0,afterIndex+1);i<Math.min(code.size(),beforeIndex);i++){
        if(i+7<beforeIndex&&aload(code.get(i),1)&&worldField(code.get(i+1))&&aload(code.get(i+2),0)&&aload(code.get(i+3),1)&&boxField(code.get(i+4))&&code.get(i+5) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&COLLISIONS.contains(call.name)&&("(L"+ENTITY+";L"+AABB+";)Ljava/util/List;").equals(call.desc)&&code.get(i+6) instanceof MethodInsnNode size&&size.getOpcode()==Opcodes.INVOKEINTERFACE&&"java/util/List".equals(size.owner)&&"size".equals(size.name)&&"()I".equals(size.desc)&&code.get(i+7) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFNE&&nextMeaningful(jump.label)==failure)collisionAt=i;
        if(i+6<beforeIndex&&aload(code.get(i),1)&&worldField(code.get(i+1))&&aload(code.get(i+2),0)&&aload(code.get(i+3),1)&&boxField(code.get(i+4))&&code.get(i+5) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&COLLISIONS.contains(call.name)&&("(L"+ENTITY+";L"+AABB+";)Ljava/util/List;").equals(call.desc)&&code.get(i+6) instanceof MethodInsnNode empty&&empty.getOpcode()==Opcodes.INVOKEINTERFACE&&"java/util/List".equals(empty.owner)&&"isEmpty".equals(empty.name)&&"()Z".equals(empty.desc)&&i+7<beforeIndex&&code.get(i+7) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFEQ&&nextMeaningful(jump.label)==failure)collisionAt=i;
        if(i+5<beforeIndex&&aload(code.get(i),1)&&worldField(code.get(i+1))&&aload(code.get(i+2),1)&&boxField(code.get(i+3))&&code.get(i+4) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&WORLD.equals(call.owner)&&ANY_LIQUID.contains(call.name)&&("(L"+AABB+";)Z").equals(call.desc)&&code.get(i+5) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFNE&&nextMeaningful(jump.label)==failure)liquidAt=i;
        if(i+1<beforeIndex&&intConstant(code.get(i))==1&&code.get(i+1) instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ISTORE&&store.var==successFlag)setAt=i;
    }
    boolean collision=collisionAt>=0,liquid=liquidAt>collisionAt,flagSet=setAt>liquidAt;return new Safety(collision,liquid,flagSet);
    }

    private static LabelNode previousLabel(AbstractInsnNode insn){for(AbstractInsnNode p=insn.getPrevious();p!=null&&(p instanceof LabelNode||p instanceof LineNumberNode||p instanceof FrameNode);p=p.getPrevious())if(p instanceof LabelNode l)return l;return null;}
    private static boolean aload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==l;}private static boolean dload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.DLOAD&&v.var==l;}private static boolean iload(AbstractInsnNode i,int l){return i instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==l;}
    private static boolean worldField(AbstractInsnNode i){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&WORLD_FIELD.contains(f.name)&&("L"+WORLD+";").equals(f.desc);}private static boolean boxField(AbstractInsnNode i){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&BOX_FIELD.contains(f.name)&&("L"+AABB+";").equals(f.desc);}private static boolean positionField(AbstractInsnNode i,Set<String> names){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&names.contains(f.name)&&"D".equals(f.desc);}
    private static int intConstant(AbstractInsnNode i){int op=i.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;if(i instanceof IntInsnNode v&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return v.operand;if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v)return v;return Integer.MIN_VALUE;}
    private static Map<AbstractInsnNode,Integer> indices(List<AbstractInsnNode> code){Map<AbstractInsnNode,Integer> out=new IdentityHashMap<>();for(int i=0;i<code.size();i++)out.put(code.get(i),i);return out;}private static List<AbstractInsnNode> meaningful(MethodNode m){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(!(i instanceof LabelNode||i instanceof LineNumberNode||i instanceof FrameNode))out.add(i);return out;}private static AbstractInsnNode nextMeaningful(AbstractInsnNode n){while(n instanceof LabelNode||n instanceof LineNumberNode||n instanceof FrameNode)n=n.getNext();return n;}private static MethodNode find(ClassNode o,String n,String d){if(o==null)return null;for(MethodNode m:o.methods)if(n.equals(m.name)&&d.equals(m.desc))return m;return null;}
    private void load(Path jarPath)throws IOException{try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry e=entries.nextElement();if(e.isDirectory()||!e.getName().endsWith(".class")||e.getName().equals("module-info.class"))continue;try(InputStream in=jar.getInputStream(e)){ClassNode n=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(n.name,n);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball teleport-safety class "+e.getName()+": "+malformed.getClass().getSimpleName());}}}}
}
