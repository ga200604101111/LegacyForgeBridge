package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
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
        if(crop&&standard&&!cross&&sourceCalls.isEmpty()&&provesMetadataBranch(node,render))return Mode.META_ZERO_CROP_ELSE_STANDARD;
        if(heldItemVisible&&cross&&!crop&&!standard&&sourceCalls.size()==1&&sourceCalls.getFirst().desc.equals("()Z"))return Mode.HELD_ITEM_CROSS;
        if(cross&&!crop&&!standard&&sourceCalls.isEmpty())return Mode.CROSS;
        if(crop&&!cross&&!standard&&sourceCalls.isEmpty())return Mode.CROP;
        if(cross&&crop&&!standard&&sourceCalls.size()==1){
            MethodInsnNode selector=sourceCalls.getFirst();if(!selector.desc.equals("()I"))return null;Integer value=effectiveConstant(classes,blockClass,selector.name,selector.desc);
            if(value==null)return null;return value==0?Mode.CROSS:value==1?Mode.CROP:null;
        }
        return null;
    }
    private record SourceContext(Frame<SourceValue>[] frames,Map<AbstractInsnNode,Integer> indices) { }

    /** Proves the exact source branch: legacy metadata 0 -> crop, nonzero -> standard. */
    private static boolean provesMetadataBranch(ClassNode owner,MethodNode method){
        SourceContext context;
        try{
            Analyzer<SourceValue> analyzer=new Analyzer<>(new SourceInterpreter());
            Frame<SourceValue>[] frames=analyzer.analyze(owner.name,method);
            Map<AbstractInsnNode,Integer> indices=new IdentityHashMap<>();
            for(int i=0;i<method.instructions.size();i++)indices.put(method.instructions.get(i),i);
            context=new SourceContext(frames,indices);
        }catch(AnalyzerException|RuntimeException ignored){return false;}
        for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof JumpInsnNode jump)||(jump.getOpcode()!=Opcodes.IFEQ&&jump.getOpcode()!=Opcodes.IFNE))continue;
            Integer index=context.indices().get(jump);Frame<SourceValue> frame=index==null?null:context.frames()[index];
            if(frame==null||frame.getStackSize()<1||!metadataOrigin(context,frame.getStack(frame.getStackSize()-1),0,new HashSet<>()))continue;
            List<String> targetFamilies=reachableRenderFamilies(jump.label,jump);
            List<String> fallthroughFamilies=reachableRenderFamilies(jump.getNext(),jump);
            List<String> zero=jump.getOpcode()==Opcodes.IFEQ?targetFamilies:fallthroughFamilies;
            List<String> nonzero=jump.getOpcode()==Opcodes.IFEQ?fallthroughFamilies:targetFamilies;
            if(zero.equals(List.of("crop"))&&nonzero.equals(List.of("standard")))return true;
        }
        return false;
    }

    private static boolean metadataOrigin(SourceContext context,SourceValue value,int depth,Set<AbstractInsnNode> guard){
        if(value==null||depth>24||value.insns==null||value.insns.isEmpty())return false;
        for(AbstractInsnNode producer:value.insns){
            if(!guard.add(producer))return false;
            boolean proven;
            if(producer instanceof MethodInsnNode call){
                proven=Set.of("net/minecraft/world/World","net/minecraft/world/IBlockAccess").contains(call.owner)
                        &&Set.of("getBlockMetadata","func_72805_g").contains(call.name)
                        &&Type.INT_TYPE.equals(Type.getReturnType(call.desc));
            }else if(producer instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD){
                Integer index=context.indices().get(producer);Frame<SourceValue> frame=index==null?null:context.frames()[index];
                proven=frame!=null&&load.var<frame.getLocals()&&metadataOrigin(context,frame.getLocal(load.var),depth+1,guard);
            }else proven=false;
            guard.remove(producer);if(!proven)return false;
        }
        return true;
    }

    private static List<String> reachableRenderFamilies(AbstractInsnNode start,AbstractInsnNode barrier){
        if(start==null)return List.of();
        Set<AbstractInsnNode> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<AbstractInsnNode> queue=new ArrayDeque<>();queue.add(start);List<String> families=new ArrayList<>();
        while(!queue.isEmpty()){
            AbstractInsnNode current=queue.removeFirst();if(current==barrier||!seen.add(current))continue;
            String family=renderFamily(current);if(family!=null)families.add(family);
            int opcode=current.getOpcode();
            if(opcode==Opcodes.IRETURN||opcode==Opcodes.LRETURN||opcode==Opcodes.FRETURN||opcode==Opcodes.DRETURN
                    ||opcode==Opcodes.ARETURN||opcode==Opcodes.RETURN||opcode==Opcodes.ATHROW)continue;
            if(current instanceof JumpInsnNode jump){
                if(jump.label!=barrier)queue.addLast(jump.label);
                if(opcode!=Opcodes.GOTO&&opcode!=Opcodes.JSR&&current.getNext()!=null)queue.addLast(current.getNext());
            }else if(current instanceof TableSwitchInsnNode table){
                queue.addLast(table.dflt);for(LabelNode label:table.labels)queue.addLast(label);
            }else if(current instanceof LookupSwitchInsnNode lookup){
                queue.addLast(lookup.dflt);for(LabelNode label:lookup.labels)queue.addLast(label);
            }else if(current.getNext()!=null)queue.addLast(current.getNext());
        }
        return families;
    }

    private static String renderFamily(AbstractInsnNode insn){
        if(!(insn instanceof MethodInsnNode call)||!call.owner.equals("net/minecraft/client/renderer/RenderBlocks"))return null;
        if(Set.of("renderBlockCrops","renderBlockCropsImpl").contains(call.name))return "crop";
        if(call.name.equals("renderStandardBlock"))return "standard";
        if(Set.of("drawCrossedSquares","renderCrossedSquares").contains(call.name))return "cross";
        return null;
    }
    private static Integer effectiveConstant(Map<String,ClassNode> classes,String owner,String name,String desc){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc)){List<AbstractInsnNode> code=real(method);return code.size()==2&&code.get(1).getOpcode()==Opcodes.IRETURN?integer(code.get(0)):null;}owner=node.superName;}return null;}
    private static Integer integer(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
