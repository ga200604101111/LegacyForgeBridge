package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Non-executing extractor for calls to one source-owned static API that are reachable from FML
 * lifecycle roots. Arguments are reduced to the conservative recipe-value vocabulary; ambiguous
 * dataflow remains UnknownValue rather than being guessed.
 */
public final class LegacyReachableCallAnalyzer {
    public record Call(String targetOwner, String targetName, String targetDescriptor,
                       List<LegacyRecipeAnalyzer.Value> arguments,
                       String sourceOwner, String sourceMethod, String sourceDescriptor) {
        public Call { arguments = List.copyOf(arguments); }
    }
    public record Analysis(List<Call> calls, List<String> diagnostics) {
        public Analysis { calls = List.copyOf(calls); diagnostics = List.copyOf(diagnostics); }
    }

    private sealed interface Symbol permits TextSymbol, NumberSymbol, RegistrySymbol, FieldSymbol,
            ObjectSymbol, ParamSymbol, NullSymbol, UnknownSymbol { }
    private record TextSymbol(String value) implements Symbol { }
    private record NumberSymbol(Number value) implements Symbol { }
    private record RegistrySymbol(LegacyRegistryAnalyzer.FieldBinding binding) implements Symbol { }
    private record FieldSymbol(String owner, String name, String descriptor) implements Symbol { }
    private record ObjectSymbol(String internalName, String constructorDescriptor, List<Symbol> arguments) implements Symbol {
        ObjectSymbol(String internalName) { this(internalName, null, List.of()); }
        ObjectSymbol { arguments = List.copyOf(arguments); }
    }
    private record ParamSymbol(int local) implements Symbol { }
    private enum NullSymbol implements Symbol { INSTANCE }
    private enum UnknownSymbol implements Symbol { INSTANCE }

    private record MethodKey(String owner, String name, String descriptor) { }
    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices, Set<Integer> parameterLocals) { }

    private static final int MAX_REACHABLE_METHODS = 65_536;
    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> methods = new LinkedHashMap<>();
    private final Map<String,LegacyRegistryAnalyzer.FieldBinding> fields = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath, String targetOwner, Set<String> targetNames) throws IOException {
        if (targetOwner == null || targetOwner.isBlank()) throw new IllegalArgumentException("targetOwner");
        if (targetNames == null || targetNames.isEmpty()) throw new IllegalArgumentException("targetNames");
        classes.clear(); methods.clear(); fields.clear(); diagnostics.clear();
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        for (var binding : registry.fieldBindings())
            fields.put(binding.owner()+"."+binding.name()+binding.descriptor(), binding);
        load(jarPath); analyzeFrames();

        LinkedHashSet<Call> output = new LinkedHashSet<>();
        for (MethodKey key : reachableMethods()) {
            if (key.owner().equals(targetOwner)) continue; // target overload delegation is implementation, not registration.
            MethodContext context = methods.get(key); if (context == null) continue;
            for (int i=0;i<context.method().instructions.size();i++) {
                AbstractInsnNode instruction = context.method().instructions.get(i);
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode()!=Opcodes.INVOKESTATIC
                        || !call.owner.equals(targetOwner) || !targetNames.contains(call.name)) continue;
                Frame<SourceValue> frame = context.frames()[i]; if (frame==null) continue;
                List<Symbol> args = invocationArgs(context,i,call,frame);
                if (args==null) {
                    diagnostics.add("Unable to recover reachable call arguments at "+key+" -> "+call.owner+"."+call.name+call.desc);
                    continue;
                }
                output.add(new Call(call.owner,call.name,call.desc,args.stream().map(this::publicValue).toList(),
                        key.owner(),key.name(),key.descriptor()));
            }
        }
        return new Analysis(List.copyOf(output),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private void load(Path jarPath) throws IOException {
        try(JarFile jar=new JarFile(jarPath.toFile(),false)) {
            var entries=jar.entries(); while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement(); if(entry.isDirectory()||!entry.getName().endsWith(".class")) continue;
                try(InputStream input=jar.getInputStream(entry)) {
                    ClassNode node=new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    if(classes.put(node.name,node)!=null) throw new IOException("Duplicate source class "+node.name);
                } catch(RuntimeException malformed) {
                    diagnostics.add("Unreadable reachable-call class "+entry.getName()+": "+malformed.getClass().getSimpleName());
                }
            }
        }
    }

    private void analyzeFrames() {
        for(ClassNode owner:classes.values()) for(MethodNode method:owner.methods) {
            MethodKey key=new MethodKey(owner.name,method.name,method.desc);
            try {
                Analyzer<SourceValue> analyzer=new Analyzer<>(new SourceInterpreter());
                Frame<SourceValue>[] frames=analyzer.analyze(owner.name,method);
                Map<AbstractInsnNode,Integer> indices=new HashMap<>();
                for(int i=0;i<method.instructions.size();i++) indices.put(method.instructions.get(i),i);
                methods.put(key,new MethodContext(owner,method,frames,indices,parameterLocals(method)));
            } catch(AnalyzerException|RuntimeException unsupported) {
                diagnostics.add("Reachable-call dataflow unavailable for "+owner.name+"."+method.name+method.desc+": "+unsupported.getMessage());
            }
        }
    }

    private Set<MethodKey> reachableMethods() {
        LinkedHashSet<MethodKey> reachable=new LinkedHashSet<>(); ArrayDeque<MethodKey> queue=new ArrayDeque<>();
        methods.forEach((key,context)->{if(isLifecycleRoot(context))queue.add(key);});
        while(!queue.isEmpty()) {
            MethodKey key=queue.removeFirst(); if(!reachable.add(key))continue;
            if(reachable.size()>MAX_REACHABLE_METHODS) throw new IllegalArgumentException("Reachable source call graph budget exceeded");
            MethodContext context=methods.get(key); if(context==null)continue;
            for(AbstractInsnNode instruction:context.method().instructions) if(instruction instanceof MethodInsnNode call) {
                MethodKey target=new MethodKey(call.owner,call.name,call.desc);
                if(methods.containsKey(target)&&!reachable.contains(target)) queue.add(target);
            }
        }
        return Collections.unmodifiableSet(reachable);
    }

    private static boolean isLifecycleRoot(MethodContext context) {
        MethodNode method=context.method();
        if(hasAnnotation(method.visibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")
                ||hasAnnotation(method.invisibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")) return true;
        return method.desc.contains("Lcpw/mods/fml/common/event/FML")&&method.desc.endsWith(")V");
    }
    private static boolean hasAnnotation(List<AnnotationNode> annotations,String descriptor) {
        return annotations!=null&&annotations.stream().anyMatch(value->descriptor.equals(value.desc));
    }
    private static Set<Integer> parameterLocals(MethodNode method) {
        LinkedHashSet<Integer> result=new LinkedHashSet<>(); int local=0;
        if((method.access&Opcodes.ACC_STATIC)==0) result.add(local++);
        for(Type type:Type.getArgumentTypes(method.desc)){result.add(local);local+=type.getSize();}
        return Collections.unmodifiableSet(result);
    }

    private List<Symbol> invocationArgs(MethodContext context,int index,MethodInsnNode call,Frame<SourceValue> frame) {
        Type[] types=Type.getArgumentTypes(call.desc); int count=types.length;
        if(frame.getStackSize()<count)return null;
        List<Symbol> result=new ArrayList<>(count); int start=frame.getStackSize()-count;
        for(int i=0;i<count;i++)result.add(resolve(context,frame.getStack(start+i),index,0,new LinkedHashSet<>()));
        return result;
    }

    private Symbol resolve(MethodContext context,SourceValue value,int current,int depth,Set<String> guard) {
        if(value==null||depth>40||value.insns==null||value.insns.isEmpty())return UnknownSymbol.INSTANCE;
        LinkedHashSet<Symbol> options=new LinkedHashSet<>();
        for(AbstractInsnNode producer:value.insns) {
            Integer index=context.indices().get(producer); if(index==null)continue;
            String key=context.owner().name+":"+context.method().name+context.method().desc+":"+index;
            if(!guard.add(key))continue;
            options.add(resolveProducer(context,producer,index,current,depth+1,guard)); guard.remove(key);
        }
        options.remove(UnknownSymbol.INSTANCE); return options.size()==1?options.getFirst():UnknownSymbol.INSTANCE;
    }

    private Symbol resolveProducer(MethodContext context,AbstractInsnNode producer,int index,int current,int depth,Set<String> guard) {
        if(producer instanceof LdcInsnNode ldc){if(ldc.cst instanceof String text)return new TextSymbol(text);if(ldc.cst instanceof Number number)return new NumberSymbol(number);}
        if(producer instanceof IntInsnNode integer&&(integer.getOpcode()==Opcodes.BIPUSH||integer.getOpcode()==Opcodes.SIPUSH))return new NumberSymbol(integer.operand);
        if(producer instanceof InsnNode instruction){int op=instruction.getOpcode();if(op==Opcodes.ACONST_NULL)return NullSymbol.INSTANCE;
            if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return new NumberSymbol(op-Opcodes.ICONST_0);
            if(op==Opcodes.LCONST_0)return new NumberSymbol(0L);if(op==Opcodes.LCONST_1)return new NumberSymbol(1L);
            if(op==Opcodes.FCONST_0)return new NumberSymbol(0F);if(op==Opcodes.FCONST_1)return new NumberSymbol(1F);if(op==Opcodes.FCONST_2)return new NumberSymbol(2F);
            if(op==Opcodes.DCONST_0)return new NumberSymbol(0D);if(op==Opcodes.DCONST_1)return new NumberSymbol(1D);
            if(op==Opcodes.DUP){Frame<SourceValue> f=context.frames()[index];if(f!=null&&f.getStackSize()>0)return resolve(context,f.getStack(f.getStackSize()-1),current,depth+1,guard);}}
        if(producer instanceof VarInsnNode variable&&isLoad(variable.getOpcode())){if(context.parameterLocals().contains(variable.var))return new ParamSymbol(variable.var);Frame<SourceValue> f=context.frames()[index];if(f!=null&&variable.var<f.getLocals())return resolve(context,f.getLocal(variable.var),current,depth+1,guard);}
        if(producer instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC){var binding=fields.get(field.owner+"."+field.name+field.desc);if(binding!=null)return new RegistrySymbol(binding);ClassNode owner=classes.get(field.owner);if(owner!=null)for(FieldNode declared:owner.fields)if(declared.name.equals(field.name)&&declared.desc.equals(field.desc)&&declared.value!=null){if(declared.value instanceof String text)return new TextSymbol(text);if(declared.value instanceof Number number)return new NumberSymbol(number);}return new FieldSymbol(field.owner,field.name,field.desc);}
        if(producer instanceof TypeInsnNode type){if(type.getOpcode()==Opcodes.NEW)return constructorObject(context,type.desc,index,current,depth,guard);if(type.getOpcode()==Opcodes.CHECKCAST){Frame<SourceValue> f=context.frames()[index];if(f!=null&&f.getStackSize()>0)return resolve(context,f.getStack(f.getStackSize()-1),current,depth+1,guard);}}
        if(producer instanceof MethodInsnNode call){Frame<SourceValue> f=context.frames()[index];if(f==null)return UnknownSymbol.INSTANCE;int argc=Type.getArgumentTypes(call.desc).length;Type rt=Type.getReturnType(call.desc);if(call.getOpcode()!=Opcodes.INVOKESTATIC&&rt.getSort()==Type.OBJECT&&f.getStackSize()>=argc+1){Symbol receiver=resolve(context,f.getStack(f.getStackSize()-argc-1),current,depth+1,guard);if(receiver!=UnknownSymbol.INSTANCE)return receiver;}}
        return UnknownSymbol.INSTANCE;
    }

    private Symbol constructorObject(MethodContext context,String type,int newIndex,int current,int depth,Set<String> guard) {
        int limit=Math.min(context.method().instructions.size(),newIndex+240);
        for(int i=newIndex+1;i<limit;i++){AbstractInsnNode instruction=context.method().instructions.get(i);if(!(instruction instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL||!call.name.equals("<init>")||!call.owner.equals(type))continue;Frame<SourceValue> frame=context.frames()[i];if(frame==null)break;List<Symbol> args=invocationArgs(context,i,call,frame);return new ObjectSymbol(type,call.desc,args==null?List.of():args);}return new ObjectSymbol(type);
    }

    private LegacyRecipeAnalyzer.Value publicValue(Symbol symbol) {
        if(symbol instanceof TextSymbol value)return new LegacyRecipeAnalyzer.TextValue(value.value());
        if(symbol instanceof NumberSymbol value)return new LegacyRecipeAnalyzer.NumberValue(value.value());
        if(symbol instanceof RegistrySymbol value){var b=value.binding();return new LegacyRecipeAnalyzer.RegistryValue(b.kind(),b.registryName(),b.legacyNamespace(),b.owner(),b.name());}
        if(symbol instanceof FieldSymbol value)return new LegacyRecipeAnalyzer.FieldValue(value.owner(),value.name(),value.descriptor());
        if(symbol instanceof ObjectSymbol value)return new LegacyRecipeAnalyzer.ObjectValue(value.internalName(),value.constructorDescriptor(),value.arguments().stream().map(this::publicValue).toList());
        if(symbol instanceof ParamSymbol value)return new LegacyRecipeAnalyzer.ParamValue(value.local());
        if(symbol==NullSymbol.INSTANCE)return LegacyRecipeAnalyzer.NullValue.INSTANCE;
        return LegacyRecipeAnalyzer.UnknownValue.INSTANCE;
    }
    private static boolean isLoad(int opcode){return opcode==Opcodes.ALOAD||opcode==Opcodes.ILOAD||opcode==Opcodes.LLOAD||opcode==Opcodes.FLOAD||opcode==Opcodes.DLOAD;}
}
