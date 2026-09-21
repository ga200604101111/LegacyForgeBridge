package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves the legacy renderer family "standard block + one metadata-derived UV quarter-turn applied
 * to all six faces". The proof is structural and namespace-agnostic: no block or renderer names are
 * admitted by identity.
 */
public final class LegacyUvRotatedBoxAnalyzer {
    private static final String RENDER_DESC="(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;III)V";
    private static final Set<String> STANDARD=Set.of("renderStandardBlock","func_147784_q");
    private static final Set<String> UV_FIELDS=Set.of(
            "uvRotateSouth","field_147871_s","uvRotateEast","field_147875_q",
            "uvRotateWest","field_147873_r","uvRotateNorth","field_147869_t",
            "uvRotateTop","field_147867_u","uvRotateBottom","field_147865_v");

    public record Rule(String registryName,String sourceBlockClass,String sourceRendererClass,int metadataShift) {
        public Rule {
            if(registryName==null||registryName.isBlank()||sourceBlockClass==null||sourceBlockClass.isBlank()
                    ||sourceRendererClass==null||sourceRendererClass.isBlank()||metadataShift<0||metadataShift>3)
                throw new IllegalArgumentException("Invalid UV-rotated box rule");
        }
        public int quarterTurns(int metadata){return (metadata>>>metadataShift)&3;}
    }
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis {rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics);}
    }

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        Map<String,LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity> identities=new HashMap<>();
        for(var rule:new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(jarPath).rules())
            identities.put(rule.registryName(),rule.renderIdentity());
        List<Rule> rules=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        for(var block:registry.blocks()){
            var identity=identities.get(block.registryName());
            if(identity==null||identity.constant()!=null||block.implementationClass()==null)continue;
            String renderer=rendererForField(classes,identity.fieldOwner(),identity.fieldName());
            if(renderer==null)continue;
            Integer shift=prove(classes,renderer,block.implementationClass());
            if(shift!=null)rules.add(new Rule(block.registryName(),block.implementationClass(),renderer,shift));
            else diagnostics.add("Custom standard-block renderer is not the admitted six-face UV rotation family: "
                    +block.registryName()+" renderer="+renderer);
        }
        return new Analysis(rules,List.copyOf(diagnostics));
    }

    private static Integer prove(Map<String,ClassNode> classes,String rendererName,String blockClass){
        ClassNode renderer=classes.get(rendererName);if(renderer==null)return null;
        MethodNode render=uniqueByDesc(renderer,RENDER_DESC);if(render==null)return null;
        boolean standard=false;Map<String,String> callbacks=new LinkedHashMap<>();
        for(AbstractInsnNode insn:render.instructions){
            if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/client/renderer/RenderBlocks")
                    &&STANDARD.contains(call.name))standard=true;
            if(!(insn instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD
                    ||!field.owner.equals("net/minecraft/client/renderer/RenderBlocks")||!"I".equals(field.desc)
                    ||!UV_FIELDS.contains(field.name))continue;
            AbstractInsnNode previous=previousReal(insn);
            if(previous instanceof MethodInsnNode call&&call.desc.equals("(I)I"))
                callbacks.put(canonicalField(field.name),call.name);
        }
        if(!standard||callbacks.size()!=6)return null;
        String delegated=null;
        for(String callback:callbacks.values()){
            MethodNode method=effective(classes,blockClass,callback,"(I)I");if(method==null)return null;
            String target=directIntDelegate(method);if(target==null)return null;
            if(delegated==null)delegated=target;else if(!delegated.equals(target))return null;
        }
        MethodNode rotate=effective(classes,blockClass,delegated,"(I)I");if(rotate==null)return null;
        return shiftProof(classes,blockClass,rotate);
    }

    private static String canonicalField(String name){
        return switch(name){
            case "uvRotateSouth","field_147871_s"->"south";
            case "uvRotateEast","field_147875_q"->"east";
            case "uvRotateWest","field_147873_r"->"west";
            case "uvRotateNorth","field_147869_t"->"north";
            case "uvRotateTop","field_147867_u"->"up";
            case "uvRotateBottom","field_147865_v"->"down";
            default->name;
        };
    }

    private static String directIntDelegate(MethodNode method){
        List<AbstractInsnNode> code=real(method);
        if(code.size()!=4||!(code.get(0) instanceof VarInsnNode self)||self.getOpcode()!=Opcodes.ALOAD||self.var!=0
                ||!(code.get(1) instanceof VarInsnNode meta)||meta.getOpcode()!=Opcodes.ILOAD||meta.var!=1
                ||!(code.get(2) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL
                ||!call.desc.equals("(I)I")||code.get(3).getOpcode()!=Opcodes.IRETURN)return null;
        return call.name;
    }

    private static Integer shiftProof(Map<String,ClassNode> classes,String blockClass,MethodNode method){
        List<AbstractInsnNode> code=real(method);
        int shiftIndex=-1;
        for(int i=0;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.ISHR){
            if(shiftIndex>=0)return null;shiftIndex=i;
        }
        if(shiftIndex<2||shiftIndex+1>=code.size()||code.get(shiftIndex+1).getOpcode()!=Opcodes.IRETURN)return null;
        if(!(code.get(0) instanceof VarInsnNode meta)||meta.getOpcode()!=Opcodes.ILOAD||meta.var!=1)return null;
        AbstractInsnNode rhs=code.get(shiftIndex-1);Integer value=integer(rhs);
        if(value==null&&rhs instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.desc.equals("()I")){
            MethodNode getter=effective(classes,blockClass,call.name,"()I");value=constantReturn(getter);
        }
        return value!=null&&value>=0&&value<=3?value:null;
    }

    private static Integer constantReturn(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);
        return code.size()==2&&code.get(1).getOpcode()==Opcodes.IRETURN?integer(code.get(0)):null;
    }

    private static String rendererForField(Map<String,ClassNode> classes,String owner,String fieldName){
        LinkedHashSet<String> renderers=new LinkedHashSet<>();
        for(ClassNode node:classes.values())for(MethodNode method:node.methods){
            List<AbstractInsnNode> code=real(method);
            for(int i=0;i<code.size();i++){
                AbstractInsnNode key=code.get(i);
                if(!(key instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETSTATIC
                        ||!field.owner.equals(owner)||!field.name.equals(fieldName)||!"I".equals(field.desc))continue;
                for(int j=i+1;j<Math.min(code.size(),i+14);j++){
                    AbstractInsnNode next=code.get(j);
                    if(next instanceof MethodInsnNode call&&call.name.equals("put")
                            &&call.desc.startsWith("(Ljava/lang/Object;Ljava/lang/Object;)"))break;
                    if(next instanceof TypeInsnNode allocation&&allocation.getOpcode()==Opcodes.NEW&&classes.containsKey(allocation.desc))
                        renderers.add(allocation.desc);
                    if(next instanceof FieldInsnNode singleton&&singleton.getOpcode()==Opcodes.GETSTATIC
                            &&singleton.desc.startsWith("L")&&singleton.desc.endsWith(";")){
                        String type=Type.getType(singleton.desc).getInternalName();if(classes.containsKey(type))renderers.add(type);
                    }
                }
            }
        }
        renderers.remove(owner);return renderers.size()==1?renderers.getFirst():null;
    }

    private static MethodNode effective(Map<String,ClassNode> classes,String owner,String name,String desc){
        Set<String> seen=new HashSet<>();
        while(owner!=null&&seen.add(owner)){
            ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc))return method;
            owner=node.superName;
        }
        return null;
    }
    private static MethodNode uniqueByDesc(ClassNode node,String desc){
        MethodNode found=null;for(MethodNode method:node.methods)if(method.desc.equals(desc)){if(found!=null)return null;found=method;}return found;
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode insn){
        for(AbstractInsnNode current=insn==null?null:insn.getPrevious();current!=null;current=current.getPrevious())if(current.getOpcode()>=0)return current;return null;
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
