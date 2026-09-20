package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

/**
 * Proves a deliberately tiny family of legacy custom block renderers that are exactly expressible
 * by native generated/cross/crop block models. Complex or stateful custom renderers remain closed.
 */
public final class LegacySimpleBlockRendererAnalyzer {
    public enum Mode { CROSS, CROP, META_ZERO_CROP_ELSE_STANDARD, HELD_ITEM_CROSS }
    public record Rule(String registryName,String sourceBlockClass,String sourceRendererClass,Mode mode) { }
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics); }
    }
    private static final String RENDER_DESC="(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;III)V";

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);var identities=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(jarPath);
        Set<String> heldItemVisibleClasses=new HashSet<>();for(var rule:new LegacyHeldItemVisibilityAnalyzer().analyze(jarPath).rules())heldItemVisibleClasses.add(rule.sourceBlockClass());
        List<Rule> rules=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        for(var block:identities.rules()){
            var id=block.renderIdentity();if(id.fieldOwner()==null)continue;
            String renderer=rendererForField(classes,id.fieldOwner(),id.fieldName());if(renderer==null)continue;
            Mode mode=classify(classes,renderer,block.sourceBlockClass(),heldItemVisibleClasses.contains(block.sourceBlockClass()));
            if(mode!=null)rules.add(new Rule(block.registryName(),block.sourceBlockClass(),renderer,mode));
            else diagnostics.add("Custom block renderer is outside the simple native cross/crop family: "+block.registryName()+" renderer="+renderer);
        }
        return new Analysis(rules,List.copyOf(diagnostics));
    }

    private static String rendererForField(Map<String,ClassNode> classes,String fieldOwner,String fieldName){
        LinkedHashSet<String> renderers=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods){List<AbstractInsnNode> code=real(method);for(int i=0;i<code.size();i++){
            AbstractInsnNode key=code.get(i);if(!(key instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETSTATIC||!field.owner.equals(fieldOwner)||!field.name.equals(fieldName)||!"I".equals(field.desc))continue;
            for(int j=i+1;j<Math.min(code.size(),i+12);j++){
                AbstractInsnNode insn=code.get(j);if(insn instanceof MethodInsnNode call&&call.name.equals("put")&&call.desc.startsWith("(Ljava/lang/Object;Ljava/lang/Object;)"))break;
                if(insn instanceof TypeInsnNode allocation&&allocation.getOpcode()==Opcodes.NEW&&classes.containsKey(allocation.desc))renderers.add(allocation.desc);
                if(insn instanceof FieldInsnNode singleton&&singleton.getOpcode()==Opcodes.GETSTATIC&&singleton.desc.startsWith("L")&&singleton.desc.endsWith(";")){
                    String type=Type.getType(singleton.desc).getInternalName();if(classes.containsKey(type))renderers.add(type);
                }
            }
        }}
        renderers.remove(fieldOwner);return renderers.size()==1?renderers.getFirst():null;
    }

    private static Mode classify(Map<String,ClassNode> classes,String renderer,String blockClass,boolean heldItemVisible){
        ClassNode node=classes.get(renderer);if(node==null)return null;MethodNode render=null;
        for(MethodNode method:node.methods)if(method.desc.equals(RENDER_DESC)){if(render!=null)return null;render=method;}
        if(render==null)return null;Set<String> renderCalls=new LinkedHashSet<>();List<MethodInsnNode> sourceCalls=new ArrayList<>();
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call){
            if(call.owner.equals("net/minecraft/client/renderer/RenderBlocks"))renderCalls.add(call.name);
            else if(classes.containsKey(call.owner)&&!call.name.equals("<init>"))sourceCalls.add(call);
        }
        boolean cross=renderCalls.stream().anyMatch(n->Set.of("drawCrossedSquares","renderCrossedSquares").contains(n));
        boolean crop=renderCalls.stream().anyMatch(n->Set.of("renderBlockCrops","renderBlockCropsImpl").contains(n));
        boolean standard=renderCalls.contains("renderStandardBlock");
        if(crop&&standard&&!cross&&sourceCalls.isEmpty()&&callsMetadata(render))return Mode.META_ZERO_CROP_ELSE_STANDARD;
        if(heldItemVisible&&cross&&!crop&&!standard&&sourceCalls.size()==1&&sourceCalls.getFirst().desc.equals("()Z"))return Mode.HELD_ITEM_CROSS;
        if(cross&&!crop&&!standard&&sourceCalls.isEmpty())return Mode.CROSS;
        if(crop&&!cross&&!standard&&sourceCalls.isEmpty())return Mode.CROP;
        if(cross&&crop&&!standard&&sourceCalls.size()==1){
            MethodInsnNode selector=sourceCalls.getFirst();if(!selector.desc.equals("()I"))return null;Integer value=effectiveConstant(classes,blockClass,selector.name,selector.desc);
            if(value==null)return null;return value==0?Mode.CROSS:value==1?Mode.CROP:null;
        }
        return null;
    }
    private static boolean callsMetadata(MethodNode method){for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&Set.of("getBlockMetadata","func_72805_g").contains(call.name))return true;return false;}
    private static Integer effectiveConstant(Map<String,ClassNode> classes,String owner,String name,String desc){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc)){List<AbstractInsnNode> code=real(method);return code.size()==2&&code.get(1).getOpcode()==Opcodes.IRETURN?integer(code.get(0)):null;}owner=node.superName;}return null;}
    private static Integer integer(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
