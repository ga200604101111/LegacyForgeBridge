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

/** Bounded proof for selector-specific random-teleport wrapper helpers used by custom snowballs. */
public final class LegacyVariantSnowballTeleportWrapperAnalyzer {
    private static final String ENTITY_LIVING="net/minecraft/entity/EntityLiving";
    private static final String RANDOM="java/util/Random";
    private static final Set<String> POS_X=Set.of("posX","field_70165_t");
    private static final Set<String> POS_Y=Set.of("posY","field_70163_u");
    private static final Set<String> POS_Z=Set.of("posZ","field_70161_v");
    private static final Set<String> RAND=Set.of("rand","field_70146_Z");

    public record Proof(String registryName,String itemClass,String projectileClass,String selectorClass,
                        String enumField,int selectorId,String wrapperMethod,String teleportMethod,
                        boolean randomXProven,boolean randomYProven,boolean randomZProven,
                        boolean delegateProven,boolean wrapperProven,List<String> blockers){
        public Proof{blockers=List.copyOf(blockers);}
    }
    public record Analysis(List<Proof> proofs,List<String> diagnostics){
        public Analysis{proofs=List.copyOf(proofs);diagnostics=List.copyOf(diagnostics);}
    }
    private record CoordinateLocals(int x,int y,int z){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);
        var effects=new LegacyVariantSnowballSelectorEffectAnalyzer().analyze(jarPath);List<Proof> proofs=new ArrayList<>();
        for(var family:effects.proofs())for(var effect:family.effects())if("CUSTOM_HELPER_UNCOMPILED".equals(effect.impactEffect())&&effect.helperMethod()!=null)
            proofs.add(prove(family,effect));
        diagnostics.addAll(effects.diagnostics());return new Analysis(proofs,diagnostics);
    }

    private Proof prove(LegacyVariantSnowballSelectorEffectAnalyzer.Proof family,LegacyVariantSnowballSelectorEffectAnalyzer.VariantEffect effect){
        List<String> blockers=new ArrayList<>();ClassNode projectile=classes.get(family.projectileClass());String desc="(L"+ENTITY_LIVING+";)Z";
        MethodNode wrapper=projectile==null?null:find(projectile,effect.helperMethod(),desc);
        if(wrapper==null)return new Proof(family.registryName(),family.itemClass(),family.projectileClass(),family.selectorClass(),effect.enumField(),effect.selectorId(),effect.helperMethod(),null,false,false,false,false,false,List.of("custom-impact-helper-body-missing"));
        List<AbstractInsnNode> code=meaningful(wrapper);Integer x=axisRandomDouble(code,POS_X),y=axisRandomY(code),z=axisRandomDouble(code,POS_Z);
        String teleport=delegate(projectile,code,x,y,z);boolean delegate=teleport!=null;
        boolean xp=x!=null,yp=y!=null,zp=z!=null;if(!xp)blockers.add("random-x-plus-minus-16-wrapper-not-proven");if(!yp)blockers.add("random-y-plus-minus-4-wrapper-not-proven");if(!zp)blockers.add("random-z-plus-minus-16-wrapper-not-proven");if(!delegate)blockers.add("random-wrapper-teleport-delegate-not-proven");
        return new Proof(family.registryName(),family.itemClass(),family.projectileClass(),family.selectorClass(),effect.enumField(),effect.selectorId(),effect.helperMethod(),teleport,xp,yp,zp,delegate,xp&&yp&&zp&&delegate,blockers);
    }

    private static Integer axisRandomDouble(List<AbstractInsnNode> code,Set<String> positionNames){
        for(int i=0;i+9<code.size();i++){
            if(!aload(code.get(i),0)||!doubleField(code.get(i+1),positionNames)||!aload(code.get(i+2),0)||!randomField(code.get(i+3)))continue;
            if(!(code.get(i+4) instanceof MethodInsnNode next)||next.getOpcode()!=Opcodes.INVOKEVIRTUAL||!RANDOM.equals(next.owner)||!"nextDouble".equals(next.name)||!"()D".equals(next.desc))continue;
            if(doubleConstant(code.get(i+5))!=0.5D||code.get(i+6).getOpcode()!=Opcodes.DSUB||doubleConstant(code.get(i+7))!=32.0D||code.get(i+8).getOpcode()!=Opcodes.DMUL||code.get(i+9).getOpcode()!=Opcodes.DADD)continue;
            if(i+10<code.size()&&code.get(i+10) instanceof VarInsnNode store&&store.getOpcode()==Opcodes.DSTORE)return store.var;
        }
        return null;
    }

    private static Integer axisRandomY(List<AbstractInsnNode> code){
        for(int i=0;i+9<code.size();i++){
            if(!aload(code.get(i),0)||!doubleField(code.get(i+1),POS_Y)||!aload(code.get(i+2),0)||!randomField(code.get(i+3)))continue;
            if(intConstant(code.get(i+4))!=8)continue;
            if(!(code.get(i+5) instanceof MethodInsnNode next)||next.getOpcode()!=Opcodes.INVOKEVIRTUAL||!RANDOM.equals(next.owner)||!"nextInt".equals(next.name)||!"(I)I".equals(next.desc))continue;
            if(intConstant(code.get(i+6))!=4||code.get(i+7).getOpcode()!=Opcodes.ISUB||code.get(i+8).getOpcode()!=Opcodes.I2D||code.get(i+9).getOpcode()!=Opcodes.DADD)continue;
            if(i+10<code.size()&&code.get(i+10) instanceof VarInsnNode store&&store.getOpcode()==Opcodes.DSTORE)return store.var;
        }
        return null;
    }

    private static String delegate(ClassNode projectile,List<AbstractInsnNode> code,Integer x,Integer y,Integer z){
        if(x==null||y==null||z==null)return null;
        String desc="(L"+ENTITY_LIVING+";DDD)Z";
        for(int i=0;i+6<code.size();i++){
            if(!aload(code.get(i),0)||!aload(code.get(i+1),1)||!dload(code.get(i+2),x)||!dload(code.get(i+3),y)||!dload(code.get(i+4),z))continue;
            if(!(code.get(i+5) instanceof MethodInsnNode call)||(call.getOpcode()!=Opcodes.INVOKESPECIAL&&call.getOpcode()!=Opcodes.INVOKEVIRTUAL)||!projectile.name.equals(call.owner)||!desc.equals(call.desc))continue;
            if(code.get(i+6).getOpcode()==Opcodes.IRETURN)return call.name;
        }
        return null;
    }

    private static boolean aload(AbstractInsnNode insn,int local){return insn instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==local;}
    private static boolean dload(AbstractInsnNode insn,int local){return insn instanceof VarInsnNode v&&v.getOpcode()==Opcodes.DLOAD&&v.var==local;}
    private static boolean doubleField(AbstractInsnNode insn,Set<String> names){return insn instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&names.contains(f.name)&&"D".equals(f.desc);}
    private static boolean randomField(AbstractInsnNode insn){return insn instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&RAND.contains(f.name)&&("L"+RANDOM+";").equals(f.desc);}
    private static double doubleConstant(AbstractInsnNode insn){if(insn.getOpcode()==Opcodes.DCONST_0)return 0D;if(insn.getOpcode()==Opcodes.DCONST_1)return 1D;if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d)return d;return Double.NaN;}
    private static int intConstant(AbstractInsnNode insn){int op=insn.getOpcode();if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return op-Opcodes.ICONST_0;if(insn instanceof IntInsnNode v&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))return v.operand;if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v)return v;return Integer.MIN_VALUE;}
    private static List<AbstractInsnNode> meaningful(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(!(insn instanceof LabelNode||insn instanceof LineNumberNode||insn instanceof FrameNode))out.add(insn);return out;}
    private static MethodNode find(ClassNode owner,String name,String desc){if(owner==null)return null;for(MethodNode method:owner.methods)if(name.equals(method.name)&&desc.equals(method.desc))return method;return null;}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball teleport-wrapper class "+entry.getName()+": "+malformed.getClass().getSimpleName());}}}
    }
}
