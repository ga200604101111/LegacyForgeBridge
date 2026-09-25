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
 * Fail-closed source proof for legacy {@code BlockLiquid} subclasses whose presentation can be
 * represented by the modern converted-block carrier plus a translucent metadata-level model.
 *
 * <p>The family is intentionally platform-semantic rather than mod-specific: a source class must
 * reach the exact 1.7 {@code BlockLiquid} base, prove render type 4, use the vanilla water/lava
 * Material singleton, remain non-opaque/non-normal, and expose an empty collision box.</p>
 */
public final class LegacyLiquidBlockAnalyzer {
    private static final String BLOCK_LIQUID="net/minecraft/block/BlockLiquid";
    private static final String MATERIAL="net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC="Lnet/minecraft/block/material/Material;";
    private static final String AABB_DESC="(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;";

    public enum Kind {
        WATER("minecraft:block/water_still","minecraft:block/water_flow"),
        LAVA("minecraft:block/lava_still","minecraft:block/lava_flow");
        private final String stillTexture,flowTexture;
        Kind(String stillTexture,String flowTexture){this.stillTexture=stillTexture;this.flowTexture=flowTexture;}
        public String stillTexture(){return stillTexture;}
        public String flowTexture(){return flowTexture;}
    }

    public record Rule(String registryName,String sourceClass,Kind kind,boolean collisionEmpty,
                       boolean nonOpaque,boolean nonNormal,int renderType) {
        public Rule {
            if(registryName==null||registryName.isBlank()||sourceClass==null||sourceClass.isBlank()
                    ||kind==null||!collisionEmpty||!nonOpaque||!nonNormal||renderType!=4)
                throw new IllegalArgumentException("Invalid legacy liquid proof");
        }
        /** 1.7 BlockLiquid#getLiquidHeightPercent: falling levels 8..15 use level zero. */
        public double height(int metadata){
            if(metadata<0||metadata>15)throw new IllegalArgumentException("Legacy liquid metadata outside 0..15");
            int level=metadata>=8?0:metadata;
            return 1.0D-(level+1)/9.0D;
        }
    }
    public record Skipped(String registryName,String sourceClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();
        for(var registration:registry.blocks()){
            String source=registration.implementationClass();if(source==null||!inherits(classes,source,BLOCK_LIQUID))continue;
            Kind kind=materialKind(classes,source);
            if(kind==null){skipped.add(new Skipped(registration.registryName(),source,"BlockLiquid Material is not one stable vanilla water/lava singleton"));continue;}
            Integer render=constantInt(effective(classes,source,Set.of("getRenderType","func_149645_b"),"()I"));
            if(render==null)render=4; // exact 1.7 platform base fact after the source lineage proved BlockLiquid.
            Boolean opaque=constantBoolean(effective(classes,source,Set.of("isOpaqueCube","func_149662_c"),"()Z"));
            Boolean normal=constantBoolean(effective(classes,source,Set.of("renderAsNormalBlock","func_149686_d"),"()Z"));
            boolean collision=collisionEmpty(effective(classes,source,
                    Set.of("getCollisionBoundingBoxFromPool","func_149668_a"),AABB_DESC));
            if(render!=4||Boolean.TRUE.equals(opaque)||Boolean.TRUE.equals(normal)||!collision){
                skipped.add(new Skipped(registration.registryName(),source,
                        "BlockLiquid presentation/collision overrides are outside the admitted renderType-4 empty-collision family"));
                continue;
            }
            rules.add(new Rule(registration.registryName(),source,kind,true,
                    opaque==null||!opaque,normal==null||!normal,4));
        }
        return new Analysis(rules,skipped,registry.diagnostics());
    }

    private static Kind materialKind(Map<String,ClassNode> classes,String source){
        String direct=directSourceExtending(classes,source,BLOCK_LIQUID);if(direct==null)return null;
        ClassNode node=classes.get(direct);if(node==null)return null;LinkedHashSet<Kind> kinds=new LinkedHashSet<>();
        int calls=0;
        for(MethodNode method:node.methods){
            if(!"<init>".equals(method.name))continue;
            for(AbstractInsnNode insn:method.instructions){
                if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL
                        ||!BLOCK_LIQUID.equals(call.owner)||!"<init>".equals(call.name)
                        ||!("("+MATERIAL_DESC+")V").equals(call.desc))continue;
                calls++;AbstractInsnNode previous=previousReal(insn);
                if(!(previous instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETSTATIC
                        ||!MATERIAL.equals(field.owner)||!MATERIAL_DESC.equals(field.desc))return null;
                Kind kind=switch(field.name){
                    case "water","field_151586_h"->Kind.WATER;
                    case "lava","field_151587_i"->Kind.LAVA;
                    default->null;
                };
                if(kind==null)return null;kinds.add(kind);
            }
        }
        return calls>0&&kinds.size()==1?kinds.getFirst():null;
    }

    private static boolean collisionEmpty(MethodNode method){
        if(method==null)return true; // exact BlockLiquid platform collision is empty.
        List<AbstractInsnNode> code=real(method);
        return code.size()==2&&code.get(0).getOpcode()==Opcodes.ACONST_NULL&&code.get(1).getOpcode()==Opcodes.ARETURN;
    }
    private static Boolean constantBoolean(MethodNode method){
        Integer value=constantInt(method);return value==null?null:value==0?Boolean.FALSE:value==1?Boolean.TRUE:null;
    }
    private static Integer constantInt(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);
        if(code.size()!=2||code.get(1).getOpcode()!=Opcodes.IRETURN)return null;return integer(code.get(0));
    }
    private static MethodNode effective(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>();
        while(owner!=null&&seen.add(owner)){
            ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;
            owner=node.superName;
        }
        return null;
    }
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){
        Set<String> seen=new HashSet<>();
        while(owner!=null&&seen.add(owner)){
            if(target.equals(owner))return true;
            ClassNode node=classes.get(owner);
            if(node==null)return false;
            if(target.equals(node.superName))return true;
            owner=node.superName;
        }
        return false;
    }
    private static String directSourceExtending(Map<String,ClassNode> classes,String owner,String target){
        Set<String> seen=new HashSet<>();
        while(owner!=null&&seen.add(owner)){
            ClassNode node=classes.get(owner);if(node==null)return null;
            if(target.equals(node.superName))return node.name;
            owner=node.superName;
        }
        return null;
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode insn){
        for(AbstractInsnNode current=insn==null?null:insn.getPrevious();current!=null;current=current.getPrevious())
            if(current.getOpcode()>=0)return current;
        return null;
    }
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;
    }
    private static Integer integer(AbstractInsnNode insn){
        if(insn==null)return null;
        return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;
            case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
            case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;
            default->null;
        };
    }
    private static Map<String,ClassNode> load(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=new LinkedHashMap<>();
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){
            var entries=jar.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
                try(InputStream input=jar.getInputStream(entry)){
                    ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);
                }catch(RuntimeException ignored){}
            }
        }
        return classes;
    }
}
