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
    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {
        public Bounds {
            if(!finite(minX,minY,minZ,maxX,maxY,maxZ)||minX<0F||minY<0F||minZ<0F||maxX>1F||maxY>1F||maxZ>1F
                    ||minX>=maxX||minY>=maxY||minZ>=maxZ)throw new IllegalArgumentException("Invalid simple renderer bounds");
        }
    }
    public record Rule(String registryName,String sourceBlockClass,String sourceRendererClass,Mode mode,
                       Bounds bounds,boolean emptyCollision) { }
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics); }
    }
    private static final String RENDER_DESC="(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;III)V";
    private static final Set<String> CROSS_RENDER_NAMES=Set.of("drawCrossedSquares","renderCrossedSquares","func_147765_a","func_147746_l");
    private static final Set<String> CROP_RENDER_NAMES=Set.of("renderBlockCrops","renderBlockCropsImpl","func_147796_n","func_147795_a");
    private static final Set<String> STANDARD_RENDER_NAMES=Set.of("renderStandardBlock","func_147784_q");

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);var identities=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        Map<String,LegacyRegistryAnalyzer.Registration> registrations=new HashMap<>();
        for(var registration:registry.blocks())registrations.put(registration.registryName(),registration);
        Set<String> heldItemVisibleClasses=new HashSet<>();for(var rule:new LegacyHeldItemVisibilityAnalyzer().analyze(jarPath).rules())heldItemVisibleClasses.add(rule.sourceBlockClass());
        List<Rule> rules=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        for(var block:identities.rules()){
            var id=block.renderIdentity();if(id.fieldOwner()==null)continue;
            String renderer=rendererForField(classes,id.fieldOwner(),id.fieldName());if(renderer==null)continue;
            Mode mode=classify(classes,renderer,block.sourceBlockClass(),registrations.get(block.registryName()),heldItemVisibleClasses.contains(block.sourceBlockClass()));
            if(mode!=null)rules.add(new Rule(block.registryName(),block.sourceBlockClass(),renderer,mode,
                    uniqueSourceBounds(classes,block.sourceBlockClass()),provesEmptyCollision(classes,block.sourceBlockClass())));
            else diagnostics.add("Custom block renderer is outside the simple native cross/crop family: "+block.registryName()+" renderer="+renderer);
        }
        return new Analysis(rules,List.copyOf(diagnostics));
    }

    private static Bounds uniqueSourceBounds(Map<String,ClassNode> classes,String source){
        LinkedHashSet<Bounds> values=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=source;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)if(method.name.equals("<init>")){
                List<AbstractInsnNode> code=real(method);
                for(int i=6;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call
                        &&Set.of("setBlockBounds","func_149676_a").contains(call.name)&&call.desc.equals("(FFFFFF)V")){
                    Float a=floatValue(code.get(i-6)),b=floatValue(code.get(i-5)),d=floatValue(code.get(i-4)),
                            e=floatValue(code.get(i-3)),f=floatValue(code.get(i-2)),g=floatValue(code.get(i-1));
                    if(a!=null&&b!=null&&d!=null&&e!=null&&f!=null&&g!=null){
                        try{values.add(new Bounds(a,b,d,e,f,g));}catch(IllegalArgumentException ignored){}
                    }
                }
            }
            current=node.superName;
        }
        return values.size()==1?values.getFirst():null;
    }

    private static boolean provesEmptyCollision(Map<String,ClassNode> classes,String source){
        MethodNode method=findHierarchy(classes,source,Set.of("getCollisionBoundingBoxFromPool","func_149668_a"),
                "(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;");
        if(method==null)return false;List<AbstractInsnNode> code=real(method);
        return code.size()==2&&code.get(0).getOpcode()==Opcodes.ACONST_NULL&&code.get(1).getOpcode()==Opcodes.ARETURN;
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

    private static Mode classify(Map<String,ClassNode> classes,String renderer,String blockClass,LegacyRegistryAnalyzer.Registration registration,boolean heldItemVisible){
        ClassNode node=classes.get(renderer);if(node==null)return null;MethodNode render=null;
        for(MethodNode method:node.methods)if(method.desc.equals(RENDER_DESC)){if(render!=null)return null;render=method;}
        if(render==null)return null;Set<String> renderCalls=new LinkedHashSet<>();List<MethodInsnNode> sourceCalls=new ArrayList<>();
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call){
            if(call.owner.equals("net/minecraft/client/renderer/RenderBlocks"))renderCalls.add(call.name);
            else if(classes.containsKey(call.owner)&&!call.name.equals("<init>"))sourceCalls.add(call);
        }
        boolean cross=renderCalls.stream().anyMatch(CROSS_RENDER_NAMES::contains);
        boolean crop=renderCalls.stream().anyMatch(CROP_RENDER_NAMES::contains);
        boolean standard=renderCalls.stream().anyMatch(STANDARD_RENDER_NAMES::contains);
        if(crop&&standard&&!cross&&sourceCalls.isEmpty()&&provesMetadataBranch(node,render))return Mode.META_ZERO_CROP_ELSE_STANDARD;
        if(heldItemVisible&&cross&&!crop&&!standard&&sourceCalls.size()==1&&sourceCalls.getFirst().desc.equals("()Z"))return Mode.HELD_ITEM_CROSS;
        if(cross&&!crop&&!standard&&sourceCalls.isEmpty())return Mode.CROSS;
        if(crop&&!cross&&!standard&&sourceCalls.isEmpty())return Mode.CROP;
        if(cross&&crop&&!standard&&sourceCalls.size()==1){
            MethodInsnNode selector=sourceCalls.getFirst();if(!selector.desc.equals("()I"))return null;Integer value=effectiveSelectorConstant(classes,blockClass,selector.name,selector.desc,registration);
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
        if(CROP_RENDER_NAMES.contains(call.name))return "crop";
        if(STANDARD_RENDER_NAMES.contains(call.name))return "standard";
        if(CROSS_RENDER_NAMES.contains(call.name))return "cross";
        return null;
    }
    private record InstanceIntField(String owner,String name) { }
    private record ConstructorContext(Frame<SourceValue>[] frames,Map<AbstractInsnNode,Integer> indices) { }

    /** Resolves either a literal selector or a constructor-bound int getter without loading source classes. */
    private static Integer effectiveSelectorConstant(Map<String,ClassNode> classes,String owner,String name,String desc,
                                                     LegacyRegistryAnalyzer.Registration registration){
        Set<String> seen=new HashSet<>();
        while(owner!=null&&seen.add(owner)){
            ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc)){
                List<AbstractInsnNode> code=real(method);
                if(code.size()==2&&code.get(1).getOpcode()==Opcodes.IRETURN){Integer literal=integer(code.get(0));if(literal!=null)return literal;}
                InstanceIntField field=directIntGetter(method);
                if(field==null||registration==null||registration.implementationClass()==null
                        ||!registration.implementationClass().equals(owner)&&!registration.implementationClass().equals(node.name))return null;
                List<Integer> arguments=new ArrayList<>();
                for(var argument:registration.constructorArguments())arguments.add(exactInt(argument.value()));
                return constructorFieldValue(classes,registration.implementationClass(),registration.constructorDescriptor(),arguments,field,0,new HashSet<>());
            }
            owner=node.superName;
        }
        return null;
    }

    private static InstanceIntField directIntGetter(MethodNode method){
        List<AbstractInsnNode> code=real(method);
        if(code.size()!=3||!(code.get(0) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ALOAD||load.var!=0
                ||!(code.get(1) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD||!"I".equals(field.desc)
                ||code.get(2).getOpcode()!=Opcodes.IRETURN)return null;
        return new InstanceIntField(field.owner,field.name);
    }

    private static Integer constructorFieldValue(Map<String,ClassNode> classes,String owner,String descriptor,List<Integer> arguments,
                                                 InstanceIntField target,int depth,Set<String> guard){
        if(owner==null||descriptor==null||depth>16||!guard.add(owner+descriptor))return null;
        ClassNode node=classes.get(owner);MethodNode ctor=null;if(node!=null)for(MethodNode method:node.methods)if(method.name.equals("<init>")&&method.desc.equals(descriptor)){ctor=method;break;}
        if(ctor==null){guard.remove(owner+descriptor);return null;}
        Type[] types=Type.getArgumentTypes(descriptor);if(types.length!=arguments.size()){guard.remove(owner+descriptor);return null;}
        Map<Integer,Integer> locals=new HashMap<>();int local=1;
        for(int i=0;i<types.length;i++){if(types[i].getSort()!=Type.INT){guard.remove(owner+descriptor);return null;}Integer value=arguments.get(i);if(value!=null)locals.put(local,value);local+=types[i].getSize();}
        ConstructorContext context;
        try{Analyzer<SourceValue> analyzer=new Analyzer<>(new SourceInterpreter());Frame<SourceValue>[] frames=analyzer.analyze(owner,ctor);Map<AbstractInsnNode,Integer> indices=new IdentityHashMap<>();for(int i=0;i<ctor.instructions.size();i++)indices.put(ctor.instructions.get(i),i);context=new ConstructorContext(frames,indices);}
        catch(AnalyzerException|RuntimeException failure){guard.remove(owner+descriptor);return null;}
        LinkedHashSet<Integer> proven=new LinkedHashSet<>();
        for(int i=0;i<ctor.instructions.size();i++){
            AbstractInsnNode insn=ctor.instructions.get(i);Frame<SourceValue> frame=context.frames()[i];if(frame==null)continue;
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(target.owner())&&field.name.equals(target.name())&&"I".equals(field.desc)){
                if(frame.getStackSize()<2||!thisValue(context,frame.getStack(frame.getStackSize()-2),0,new HashSet<>())){guard.remove(owner+descriptor);return null;}
                Integer value=intValue(context,frame.getStack(frame.getStackSize()-1),locals,0,new HashSet<>());if(value==null){guard.remove(owner+descriptor);return null;}proven.add(value);
            }
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")&&call.owner.equals(owner)){
                Type[] nestedTypes=Type.getArgumentTypes(call.desc);if(frame.getStackSize()<nestedTypes.length+1)continue;int start=frame.getStackSize()-nestedTypes.length;
                if(!thisValue(context,frame.getStack(start-1),0,new HashSet<>()))continue;List<Integer> nested=new ArrayList<>();boolean supported=true;
                for(int arg=0;arg<nestedTypes.length;arg++){if(nestedTypes[arg].getSort()!=Type.INT){supported=false;break;}nested.add(intValue(context,frame.getStack(start+arg),locals,0,new HashSet<>()));}
                if(supported){Integer delegated=constructorFieldValue(classes,owner,call.desc,nested,target,depth+1,guard);if(delegated!=null)proven.add(delegated);}
            }
        }
        guard.remove(owner+descriptor);return proven.size()==1?proven.getFirst():null;
    }

    private static boolean thisValue(ConstructorContext context,SourceValue value,int depth,Set<AbstractInsnNode> guard){
        if(value==null||depth>16||value.insns==null||value.insns.isEmpty())return false;
        for(AbstractInsnNode producer:value.insns){if(!guard.add(producer))return false;boolean ok=producer instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ALOAD&&load.var==0;
            if(!ok&&producer instanceof InsnNode copy&&copy.getOpcode()==Opcodes.DUP){Integer index=context.indices().get(producer);Frame<SourceValue> frame=index==null?null:context.frames()[index];ok=frame!=null&&frame.getStackSize()>0&&thisValue(context,frame.getStack(frame.getStackSize()-1),depth+1,guard);}
            guard.remove(producer);if(!ok)return false;}return true;
    }

    private static Integer intValue(ConstructorContext context,SourceValue value,Map<Integer,Integer> locals,int depth,Set<AbstractInsnNode> guard){
        if(value==null||depth>16||value.insns==null||value.insns.isEmpty())return null;Integer result=null;
        for(AbstractInsnNode producer:value.insns){if(!guard.add(producer))return null;Integer candidate=integer(producer);
            if(candidate==null&&producer instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD){candidate=locals.get(load.var);if(candidate==null){Integer index=context.indices().get(producer);Frame<SourceValue> frame=index==null?null:context.frames()[index];if(frame!=null&&load.var<frame.getLocals())candidate=intValue(context,frame.getLocal(load.var),locals,depth+1,guard);}}
            guard.remove(producer);if(candidate==null)return null;if(result==null)result=candidate;else if(!result.equals(candidate))return null;}return result;
    }

    private static Integer exactInt(Object value){
        if(!(value instanceof Number number))return null;double raw=number.doubleValue();
        return Double.isFinite(raw)&&raw==Math.rint(raw)&&raw>=Integer.MIN_VALUE&&raw<=Integer.MAX_VALUE?(int)raw:null;
    }
    private static Integer integer(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static Float floatValue(AbstractInsnNode insn){
        if(insn==null)return null;return switch(insn.getOpcode()){
            case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Number n?n.floatValue():null;default->null;};
    }
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
