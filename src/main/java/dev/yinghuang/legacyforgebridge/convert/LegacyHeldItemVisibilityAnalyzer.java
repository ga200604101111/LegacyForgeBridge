package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

/** Proves the legacy client pattern "show this block only while holding its own BlockItem". */
public final class LegacyHeldItemVisibilityAnalyzer {
    public record Rule(String registryName,String sourceBlockClass,int visibleOrMask,int hiddenAndMask,
                       boolean emptyCollision,boolean metaZeroSelectionElseEmpty) {
        public Rule {
            if(registryName==null||sourceBlockClass==null||visibleOrMask<=0||visibleOrMask>15
                    ||hiddenAndMask<0||hiddenAndMask>15||(visibleOrMask&hiddenAndMask)!=0)
                throw new IllegalArgumentException("Invalid held-item visibility rule");
        }
    }
    public record Analysis(List<Rule> rules,List<String> diagnostics) { public Analysis { rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics); } }
    private static final String RANDOM_DESC="(Lnet/minecraft/world/World;IIILjava/util/Random;)V";
    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);var registry=new LegacyRegistryAnalyzer().analyze(jarPath);List<Rule> rules=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        for(var registration:registry.blocks()){
            String source=registration.implementationClass();ClassNode node=classes.get(source);if(node==null)continue;
            MethodNode random=findHierarchy(classes,source,Set.of("randomDisplayTick","func_149734_b"),RANDOM_DESC);if(random==null)continue;
            Set<MethodNode> reachable=reachableSourceMethods(classes,random,24);Rule rule=null;
            for(MethodNode method:reachable){
                int[] masks=proveHeldOwnBlockToggle(method);if(masks==null)continue;
                if(rule!=null){rule=null;diagnostics.add("Multiple held-item visibility toggle methods are reachable for "+source);break;}
                boolean emptyCollision=provesEmptyCollision(classes,source);
                boolean metaZeroSelection=provesMetaZeroSelectionElseEmpty(classes,source);
                rule=new Rule(registration.registryName(),source,masks[0],masks[1],emptyCollision,metaZeroSelection);
            }
            if(rule!=null)rules.add(rule);
        }
        return new Analysis(rules,List.copyOf(diagnostics));
    }
    private static boolean provesEmptyCollision(Map<String,ClassNode> classes,String source){
        MethodNode method=findHierarchy(classes,source,Set.of("getCollisionBoundingBoxFromPool","func_149668_a"),
                "(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;");
        if(method==null)return false;List<AbstractInsnNode> code=real(method);
        return code.size()==2&&code.get(0).getOpcode()==Opcodes.ACONST_NULL&&code.get(1).getOpcode()==Opcodes.ARETURN;
    }

    private static boolean provesMetaZeroSelectionElseEmpty(Map<String,ClassNode> classes,String source){
        MethodNode method=findHierarchy(classes,source,Set.of("getSelectedBoundingBoxFromPool","func_149633_g"),
                "(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;");
        if(method==null)return false;List<AbstractInsnNode> code=real(method);
        boolean metadata=false,branch=false,superBox=false,zeroBox=false,sixZeros=false;
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof MethodInsnNode call){
                if(call.owner.equals("net/minecraft/world/World")
                        &&Set.of("getBlockMetadata","func_72805_g").contains(call.name)
                        &&call.desc.equals("(III)I"))metadata=true;
                if(call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals("net/minecraft/block/Block")
                        &&Set.of("getSelectedBoundingBoxFromPool","func_149633_g").contains(call.name)
                        &&call.desc.equals("(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;"))superBox=true;
                if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("net/minecraft/util/AxisAlignedBB")
                        &&Set.of("getBoundingBox","func_72330_a").contains(call.name)
                        &&call.desc.equals("(DDDDDD)Lnet/minecraft/util/AxisAlignedBB;"))zeroBox=true;
            }
            if(insn instanceof JumpInsnNode jump&&(jump.getOpcode()==Opcodes.IFEQ||jump.getOpcode()==Opcodes.IFNE))branch=true;
        }
        int zeroDoubles=0;for(AbstractInsnNode insn:code)if(insn.getOpcode()==Opcodes.DCONST_0)zeroDoubles++;
        sixZeros=zeroDoubles>=6;
        return metadata&&branch&&superBox&&zeroBox&&sixZeros;
    }

    private static int[] proveHeldOwnBlockToggle(MethodNode method){
        boolean client=false,held=false,item=false,blockFromItem=false,compare=false;int setBlock=0,bounds=0;Integer orMask=null,andMask=null;List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);if(insn instanceof MethodInsnNode call){
                if(call.owner.equals("cpw/mods/fml/client/FMLClientHandler")||call.owner.equals("net/minecraft/client/Minecraft"))client=true;
                if(Set.of("getCurrentEquippedItem","getHeldItem","func_71045_bC").contains(call.name))held=true;
                if(call.owner.equals("net/minecraft/item/ItemStack")&&Set.of("getItem","func_77973_b").contains(call.name))item=true;
                if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("net/minecraft/block/Block")&&Set.of("getBlockFromItem","func_149634_a").contains(call.name))blockFromItem=true;
                if(call.owner.equals("net/minecraft/world/World")&&Set.of("setBlock","func_147465_d").contains(call.name)&&call.desc.contains("Lnet/minecraft/block/Block;"))setBlock++;
                if(Set.of("setBlockBounds","func_149676_a").contains(call.name)&&call.desc.equals("(FFFFFF)V"))bounds++;
            }
            if(insn instanceof JumpInsnNode jump&&(jump.getOpcode()==Opcodes.IF_ACMPEQ||jump.getOpcode()==Opcodes.IF_ACMPNE))compare=true;
            if(insn.getOpcode()==Opcodes.IOR){Integer value=i>0?integer(code.get(i-1)):null;if(value!=null)orMask=value;}
            if(insn.getOpcode()==Opcodes.IAND){Integer value=i>0?integer(code.get(i-1)):null;if(value!=null)andMask=value;}
        }
        if(!client||!held||!item||!blockFromItem||!compare||setBlock<2||bounds<2||orMask==null||andMask==null)return null;
        if(orMask<=0||orMask>15||andMask<0||andMask>15||(orMask&andMask)!=0)return null;return new int[]{orMask,andMask};
    }
    private static Set<MethodNode> reachableSourceMethods(Map<String,ClassNode> classes,MethodNode root,int limit){LinkedHashSet<MethodNode> out=new LinkedHashSet<>();ArrayDeque<MethodNode> queue=new ArrayDeque<>();queue.add(root);while(!queue.isEmpty()&&out.size()<limit){MethodNode method=queue.removeFirst();if(!out.add(method))continue;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&classes.containsKey(call.owner)){ClassNode owner=classes.get(call.owner);for(MethodNode target:owner.methods)if(target.name.equals(call.name)&&target.desc.equals(call.desc))queue.addLast(target);}}return out;}
    private static MethodNode findHierarchy(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;for(MethodNode method:node.methods)if(names.contains(method.name)&&method.desc.equals(desc))return method;owner=node.superName;}return null;}
    private static Integer integer(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
