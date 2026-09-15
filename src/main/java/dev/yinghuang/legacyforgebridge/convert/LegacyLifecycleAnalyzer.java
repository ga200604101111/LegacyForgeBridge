package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bounded, non-executing extractor for Forge/FML lifecycle registrations that are not Item/Block
 * GameRegistry content. The same symbolic templates are propagated through source helper methods,
 * so a mod-specific wrapper around EntityRegistry/GameRegistry does not require a mod-specific
 * converter. Ambiguous values are retained as unknown rather than guessed.
 */
public final class LegacyLifecycleAnalyzer {
    private static final int MAX_ROUNDS = 64;
    private static final int MAX_TEMPLATES = 32_768;
    private static final String GAME_REGISTRY = "cpw/mods/fml/common/registry/GameRegistry";
    private static final String ENTITY_REGISTRY = "cpw/mods/fml/common/registry/EntityRegistry";
    private static final String NETWORK_REGISTRY = "cpw/mods/fml/common/network/NetworkRegistry";
    private static final String DIMENSION_MANAGER = "net/minecraftforge/common/DimensionManager";

    public enum Kind {
        ENTITY, TILE_ENTITY, ENTITY_SPAWN, GUI_HANDLER, WORLD_GENERATOR,
        DIMENSION_PROVIDER, DIMENSION, DIMENSION_UNREGISTER, DIMENSION_PROVIDER_UNREGISTER
    }

    public sealed interface Value permits TextValue, NumberValue, BooleanValue, TypeValue,
            ObjectValue, FieldValue, ParamValue, NullValue, UnknownValue { }
    public record TextValue(String value) implements Value { }
    public record NumberValue(Number value) implements Value { }
    public record BooleanValue(boolean value) implements Value { }
    public record TypeValue(String internalName) implements Value { }
    public record ObjectValue(String internalName) implements Value { }
    /** Source static field identity is preserved even when its runtime value is configuration-driven. */
    public record FieldValue(String owner, String name, String descriptor) implements Value { }
    private record ParamValue(int local) implements Value { }
    public enum NullValue implements Value { INSTANCE }
    public enum UnknownValue implements Value { INSTANCE }

    public record Registration(Kind kind, List<Value> arguments, String sourceOwner,
                               String sourceMethod, String sourceDescriptor) {
        public Registration { arguments = List.copyOf(arguments); }
    }
    public record Analysis(List<Registration> registrations, List<String> diagnostics) {
        public Analysis { registrations = List.copyOf(registrations); diagnostics = List.copyOf(diagnostics); }
        public List<Registration> of(Kind kind) { return registrations.stream().filter(v -> v.kind() == kind).toList(); }
    }

    private record MethodKey(String owner, String name, String descriptor) { }
    private record Template(Kind kind, List<Value> args, MethodKey directSource) {
        private Template { args = List.copyOf(args); }
    }
    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices, Set<Integer> parameterLocals) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> methods = new LinkedHashMap<>();
    private final Map<MethodKey,LinkedHashSet<Template>> templates = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); methods.clear(); templates.clear(); diagnostics.clear();
        load(jarPath); analyzeFrames(); collectDirect(); propagate();
        LinkedHashSet<Registration> output = new LinkedHashSet<>();
        for (var entry : methods.entrySet()) {
            if (!isRoot(entry.getValue()) && !entry.getKey().name().equals("<clinit>")) continue;
            for (Template template : templates.getOrDefault(entry.getKey(), new LinkedHashSet<>())) {
                Registration registration = materialize(template);
                if (registration != null) output.add(registration);
            }
        }
        return new Analysis(List.copyOf(output), List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable lifecycle-analysis class " + entry.getName() + ": " + malformed.getClass().getSimpleName());
                }
            }
        }
    }

    private void analyzeFrames() {
        for (ClassNode owner : classes.values()) for (MethodNode method : owner.methods) {
            MethodKey key = new MethodKey(owner.name, method.name, method.desc);
            try {
                Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
                Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
                Map<AbstractInsnNode,Integer> indices = new HashMap<>();
                for (int i=0;i<method.instructions.size();i++) indices.put(method.instructions.get(i),i);
                methods.put(key,new MethodContext(owner,method,frames,indices,parameterLocals(method)));
                templates.put(key,new LinkedHashSet<>());
            } catch (AnalyzerException|RuntimeException error) {
                diagnostics.add("Lifecycle dataflow unavailable for " + owner.name + "." + method.name + method.desc + ": " + error.getMessage());
            }
        }
    }

    private static Set<Integer> parameterLocals(MethodNode method) {
        LinkedHashSet<Integer> slots=new LinkedHashSet<>(); int local=0;
        if ((method.access & Opcodes.ACC_STATIC)==0) slots.add(local++);
        for(Type type:Type.getArgumentTypes(method.desc)){slots.add(local);local+=type.getSize();}
        return Collections.unmodifiableSet(slots);
    }

    private void collectDirect() {
        for (var entry : methods.entrySet()) {
            MethodKey key=entry.getKey(); MethodContext context=entry.getValue();
            for(int i=0;i<context.method().instructions.size();i++) {
                AbstractInsnNode instruction=context.method().instructions.get(i);
                if(!(instruction instanceof MethodInsnNode call)) continue;
                Kind kind=kind(call); if(kind==null) continue;
                Frame<SourceValue> frame=context.frames()[i]; if(frame==null) continue;
                List<Value> args=invocationArgs(context,i,call,frame);
                if(args==null){diagnostics.add("Unable to recover lifecycle registration arguments at "+key);continue;}
                templates.get(key).add(new Template(kind,args,key));
            }
        }
    }

    private static Kind kind(MethodInsnNode call) {
        if(call.owner.equals(GAME_REGISTRY)) return switch(call.name){
            case "registerTileEntity" -> Kind.TILE_ENTITY;
            case "registerWorldGenerator" -> Kind.WORLD_GENERATOR;
            default -> null;
        };
        if(call.owner.equals(ENTITY_REGISTRY)) return switch(call.name){
            case "registerModEntity" -> Kind.ENTITY;
            case "addSpawn" -> Kind.ENTITY_SPAWN;
            default -> null;
        };
        if(call.owner.equals(NETWORK_REGISTRY) && call.name.equals("registerGuiHandler")) return Kind.GUI_HANDLER;
        if(call.owner.equals(DIMENSION_MANAGER)) return switch(call.name){
            case "registerProviderType" -> Kind.DIMENSION_PROVIDER;
            case "registerDimension" -> Kind.DIMENSION;
            case "unregisterDimension" -> Kind.DIMENSION_UNREGISTER;
            case "unregisterProviderType" -> Kind.DIMENSION_PROVIDER_UNREGISTER;
            default -> null;
        };
        return null;
    }

    private void propagate() {
        for(int round=0;round<MAX_ROUNDS;round++){
            boolean changed=false;
            for(var entry:methods.entrySet()){
                MethodKey caller=entry.getKey();MethodContext context=entry.getValue();LinkedHashSet<Template> target=templates.get(caller);
                for(int i=0;i<context.method().instructions.size();i++){
                    AbstractInsnNode instruction=context.method().instructions.get(i);
                    if(!(instruction instanceof MethodInsnNode call))continue;
                    MethodKey callee=new MethodKey(call.owner,call.name,call.desc);
                    LinkedHashSet<Template> source=templates.get(callee);MethodContext calleeContext=methods.get(callee);
                    if(source==null||source.isEmpty()||calleeContext==null)continue;
                    Frame<SourceValue> frame=context.frames()[i];if(frame==null)continue;
                    List<Value> actual=invocationValuesIncludingReceiver(context,i,call,frame);if(actual==null)continue;
                    Map<Integer,Value> substitutions=parameterSubstitution(calleeContext.method(),actual);
                    for(Template template:source){
                        List<Value> args=template.args().stream().map(value->substitute(value,substitutions)).toList();
                        if(target.add(new Template(template.kind(),args,template.directSource()))){
                            changed=true;if(totalTemplates()>MAX_TEMPLATES)throw new IllegalArgumentException("Lifecycle template propagation budget exceeded");
                        }
                    }
                }
            }
            if(!changed)return;
        }
        diagnostics.add("Lifecycle helper propagation reached its round budget; recursive helpers were not trusted.");
    }

    private int totalTemplates(){return templates.values().stream().mapToInt(Set::size).sum();}

    private static Map<Integer,Value> parameterSubstitution(MethodNode method,List<Value> actual){
        LinkedHashMap<Integer,Value> result=new LinkedHashMap<>();int ai=0,local=0;
        if((method.access&Opcodes.ACC_STATIC)==0){if(ai>=actual.size())return Map.of();result.put(local++,actual.get(ai++));}
        for(Type type:Type.getArgumentTypes(method.desc)){if(ai>=actual.size())return Map.of();result.put(local,actual.get(ai++));local+=type.getSize();}
        return result;
    }
    private static Value substitute(Value value,Map<Integer,Value> map){return value instanceof ParamValue p?map.getOrDefault(p.local(),UnknownValue.INSTANCE):value;}

    private Registration materialize(Template template){
        // Registration identity must have a concrete primary class/handler whenever the API takes one.
        int classIndex=switch(template.kind()){
            case ENTITY,TILE_ENTITY,ENTITY_SPAWN -> 0;
            case WORLD_GENERATOR -> 0;
            case GUI_HANDLER -> template.args().size()>1?1:-1;
            case DIMENSION_PROVIDER -> 1;
            default -> -1;
        };
        if(classIndex>=0 && classIndex<template.args().size()){
            Value value=template.args().get(classIndex);
            if(!(value instanceof TypeValue||value instanceof ObjectValue||value instanceof FieldValue))return null;
        }
        MethodKey source=template.directSource();
        return new Registration(template.kind(),template.args(),source.owner(),source.name(),source.descriptor());
    }

    private boolean isRoot(MethodContext context){
        MethodNode method=context.method();
        if(hasAnnotation(method.visibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")||hasAnnotation(method.invisibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;"))return true;
        return method.desc.contains("Lcpw/mods/fml/common/event/FML")&&method.desc.endsWith(")V");
    }
    private static boolean hasAnnotation(List<AnnotationNode> annotations,String descriptor){return annotations!=null&&annotations.stream().anyMatch(a->descriptor.equals(a.desc));}

    private List<Value> invocationArgs(MethodContext context,int index,MethodInsnNode call,Frame<SourceValue> frame){
        int count=Type.getArgumentTypes(call.desc).length;if(frame.getStackSize()<count)return null;List<Value> result=new ArrayList<>(count);int start=frame.getStackSize()-count;
        for(int i=0;i<count;i++)result.add(resolve(context,frame.getStack(start+i),index,0,new LinkedHashSet<>()));return result;
    }
    private List<Value> invocationValuesIncludingReceiver(MethodContext context,int index,MethodInsnNode call,Frame<SourceValue> frame){
        int argCount=Type.getArgumentTypes(call.desc).length;boolean isStatic=call.getOpcode()==Opcodes.INVOKESTATIC;int count=argCount+(isStatic?0:1);
        if(frame.getStackSize()<count)return null;List<Value> result=new ArrayList<>(count);int start=frame.getStackSize()-count;
        for(int i=0;i<count;i++)result.add(resolve(context,frame.getStack(start+i),index,0,new LinkedHashSet<>()));return result;
    }

    private Value resolve(MethodContext context,SourceValue value,int currentIndex,int depth,Set<String> guard){
        if(value==null||depth>32||value.insns==null||value.insns.isEmpty())return UnknownValue.INSTANCE;
        LinkedHashSet<Value> options=new LinkedHashSet<>();
        for(AbstractInsnNode producer:value.insns){Integer pi=context.indices().get(producer);if(pi==null)continue;String key=context.owner().name+":"+context.method().name+context.method().desc+":"+pi;if(!guard.add(key))continue;
            options.add(resolveProducer(context,producer,pi,depth+1,guard));guard.remove(key);}
        options.remove(UnknownValue.INSTANCE);return options.size()==1?options.getFirst():UnknownValue.INSTANCE;
    }

    private Value resolveProducer(MethodContext context,AbstractInsnNode producer,int pi,int depth,Set<String> guard){
        if(producer instanceof LdcInsnNode ldc){
            if(ldc.cst instanceof String s)return new TextValue(s);if(ldc.cst instanceof Number n)return new NumberValue(n);
            if(ldc.cst instanceof Type t&&t.getSort()==Type.OBJECT)return new TypeValue(t.getInternalName());
        }
        if(producer instanceof IntInsnNode integer&&(integer.getOpcode()==Opcodes.BIPUSH||integer.getOpcode()==Opcodes.SIPUSH))return new NumberValue(integer.operand);
        if(producer instanceof InsnNode insn){int op=insn.getOpcode();
            if(op==Opcodes.ACONST_NULL)return NullValue.INSTANCE;
            if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return new NumberValue(op-Opcodes.ICONST_0);
            if(op==Opcodes.LCONST_0)return new NumberValue(0L);if(op==Opcodes.LCONST_1)return new NumberValue(1L);
            if(op==Opcodes.FCONST_0)return new NumberValue(0F);if(op==Opcodes.FCONST_1)return new NumberValue(1F);if(op==Opcodes.FCONST_2)return new NumberValue(2F);
            if(op==Opcodes.DCONST_0)return new NumberValue(0D);if(op==Opcodes.DCONST_1)return new NumberValue(1D);
            if(op==Opcodes.DUP){Frame<SourceValue> f=context.frames()[pi];if(f!=null&&f.getStackSize()>0)return resolve(context,f.getStack(f.getStackSize()-1),pi,depth+1,guard);}
        }
        if(producer instanceof TypeInsnNode typeInsn){
            if(typeInsn.getOpcode()==Opcodes.NEW)return new ObjectValue(typeInsn.desc);
            if(typeInsn.getOpcode()==Opcodes.CHECKCAST){Frame<SourceValue> f=context.frames()[pi];if(f!=null&&f.getStackSize()>0)return resolve(context,f.getStack(f.getStackSize()-1),pi,depth+1,guard);}
        }
        if(producer instanceof VarInsnNode var&&isLoad(var.getOpcode())){
            if(context.parameterLocals().contains(var.var))return new ParamValue(var.var);Frame<SourceValue> f=context.frames()[pi];
            if(f!=null&&var.var<f.getLocals())return resolve(context,f.getLocal(var.var),pi,depth+1,guard);
        }
        if(producer instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC){
            ClassNode owner=classes.get(field.owner);if(owner!=null)for(FieldNode candidate:owner.fields)if(candidate.name.equals(field.name)&&candidate.desc.equals(field.desc)){
                if(candidate.value instanceof String s)return new TextValue(s);if(candidate.value instanceof Number n)return new NumberValue(n);}
            return new FieldValue(field.owner,field.name,field.desc);
        }
        if(producer instanceof MethodInsnNode call){
            Type result=Type.getReturnType(call.desc);Frame<SourceValue> frame=context.frames()[pi];
            if(result.getSort()==Type.VOID||frame==null)return UnknownValue.INSTANCE;
            int argc=Type.getArgumentTypes(call.desc).length;boolean isStatic=call.getOpcode()==Opcodes.INVOKESTATIC;
            if(!isStatic&&result.getSort()==Type.OBJECT&&frame.getStackSize()>=argc+1){Value receiver=resolve(context,frame.getStack(frame.getStackSize()-argc-1),pi,depth+1,guard);if(receiver instanceof ObjectValue||receiver instanceof ParamValue||receiver instanceof FieldValue)return receiver;}
        }
        return UnknownValue.INSTANCE;
    }

    private static boolean isLoad(int opcode){
        return opcode==Opcodes.ALOAD||opcode==Opcodes.ILOAD||opcode==Opcodes.LLOAD||opcode==Opcodes.FLOAD||opcode==Opcodes.DLOAD;
    }
}
