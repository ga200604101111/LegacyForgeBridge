package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

/**
 * Non-executing, interprocedural extractor for ordinary 1.7.x crafting/smelting/OreDictionary/fuel
 * registrations. Object[] recipe specifications are reconstructed from bytecode array stores; mod
 * static fields are resolved through {@link LegacyRegistryAnalyzer} evidence rather than names.
 */
public final class LegacyRecipeAnalyzer {
    private static final String GAME_REGISTRY="cpw/mods/fml/common/registry/GameRegistry";
    private static final String ORE_DICTIONARY="net/minecraftforge/oredict/OreDictionary";
    private static final String SHAPED_ORE="net/minecraftforge/oredict/ShapedOreRecipe";
    private static final String SHAPELESS_ORE="net/minecraftforge/oredict/ShapelessOreRecipe";
    private static final int MAX_ROUNDS=64, MAX_TEMPLATES=65_536;

    public enum Kind { SHAPED, SHAPELESS, SMELTING, ORE_REGISTER, FUEL_HANDLER }

    public sealed interface Value permits TextValue, NumberValue, CharacterValue, RegistryValue,
            FieldValue, ObjectValue, ArrayValue, ParamValue, NullValue, UnknownValue { }
    public record TextValue(String value) implements Value { }
    public record NumberValue(Number value) implements Value { }
    public record CharacterValue(char value) implements Value { }
    /** A source static field proven to be a registered mod Item/Block. */
    public record RegistryValue(LegacyRegistryAnalyzer.Kind kind,String registryName,String legacyNamespace,
                                String sourceOwner,String sourceField) implements Value { }
    public record FieldValue(String owner,String name,String descriptor) implements Value { }
    public record ObjectValue(String internalName,String constructorDescriptor,List<Value> constructorArguments) implements Value {
        public ObjectValue { constructorArguments=List.copyOf(constructorArguments); }
    }
    public record ArrayValue(List<Value> elements) implements Value { public ArrayValue { elements=List.copyOf(elements); } }
    public record ParamValue(int local) implements Value { }
    public enum NullValue implements Value { INSTANCE }
    public enum UnknownValue implements Value { INSTANCE }

    public record Registration(Kind kind,List<Value> arguments,String sourceOwner,String sourceMethod,String sourceDescriptor){
        public Registration { arguments=List.copyOf(arguments); }
    }
    public record Analysis(List<Registration> registrations,List<String> diagnostics){
        public Analysis { registrations=List.copyOf(registrations);diagnostics=List.copyOf(diagnostics); }
        public List<Registration> of(Kind kind){return registrations.stream().filter(v->v.kind()==kind).toList();}
    }

    private sealed interface Symbol permits TextSymbol,NumberSymbol,CharacterSymbol,RegistrySymbol,FieldSymbol,ObjectSymbol,
            ArraySymbol,ParamSymbol,NullSymbol,UnknownSymbol { }
    private record TextSymbol(String value) implements Symbol { }
    private record NumberSymbol(Number value) implements Symbol { }
    private record CharacterSymbol(char value) implements Symbol { }
    private record RegistrySymbol(LegacyRegistryAnalyzer.FieldBinding binding) implements Symbol { }
    private record FieldSymbol(String owner,String name,String descriptor) implements Symbol { }
    private record ObjectSymbol(String internalName,String constructorDescriptor,List<Symbol> args) implements Symbol {
        ObjectSymbol(String internalName){this(internalName,null,List.of());} ObjectSymbol{args=List.copyOf(args);}
    }
    private record ArraySymbol(String identity,List<Symbol> elements) implements Symbol { ArraySymbol{elements=List.copyOf(elements);} }
    private record ParamSymbol(int local) implements Symbol { }
    private enum NullSymbol implements Symbol { INSTANCE }
    private enum UnknownSymbol implements Symbol { INSTANCE }

    private record MethodKey(String owner,String name,String descriptor) { }
    private record Template(Kind kind,List<Symbol> args,MethodKey directSource){Template{args=List.copyOf(args);}}
    private record MethodContext(ClassNode owner,MethodNode method,Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices,Set<Integer> parameterLocals){ }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> methods=new LinkedHashMap<>();
    private final Map<MethodKey,LinkedHashSet<Template>> templates=new LinkedHashMap<>();
    private final Map<String,LegacyRegistryAnalyzer.FieldBinding> fields=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();methods.clear();templates.clear();fields.clear();diagnostics.clear();
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        for(var binding:registry.fieldBindings())fields.put(binding.owner()+"."+binding.name()+binding.descriptor(),binding);
        load(jarPath);analyzeFrames();collectDirect();propagate();
        LinkedHashSet<Registration> out=new LinkedHashSet<>();
        for(var entry:methods.entrySet()){
            if(!isRoot(entry.getValue())&&!entry.getKey().name().equals("<clinit>")&&!isConstructedFromRoot(entry.getKey()))continue;
            for(Template template:templates.getOrDefault(entry.getKey(),new LinkedHashSet<>())){
                Registration r=materialize(template);if(r!=null)out.add(r);
            }
        }
        return new Analysis(List.copyOf(out),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;
            try(InputStream in=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException e){diagnostics.add("Unreadable recipe-analysis class "+entry.getName());}
        }}
    }
    private void analyzeFrames(){for(ClassNode owner:classes.values())for(MethodNode method:owner.methods){MethodKey key=new MethodKey(owner.name,method.name,method.desc);try{
        Analyzer<SourceValue>a=new Analyzer<>(new SourceInterpreter());Frame<SourceValue>[]f=a.analyze(owner.name,method);Map<AbstractInsnNode,Integer>idx=new HashMap<>();for(int i=0;i<method.instructions.size();i++)idx.put(method.instructions.get(i),i);
        methods.put(key,new MethodContext(owner,method,f,idx,parameterLocals(method)));templates.put(key,new LinkedHashSet<>());
    }catch(AnalyzerException|RuntimeException e){diagnostics.add("Recipe dataflow unavailable for "+owner.name+"."+method.name+method.desc+": "+e.getMessage());}}}
    private static Set<Integer> parameterLocals(MethodNode method){LinkedHashSet<Integer>s=new LinkedHashSet<>();int local=0;if((method.access&Opcodes.ACC_STATIC)==0)s.add(local++);for(Type t:Type.getArgumentTypes(method.desc)){s.add(local);local+=t.getSize();}return s;}

    private void collectDirect(){for(var entry:methods.entrySet()){MethodKey key=entry.getKey();MethodContext c=entry.getValue();for(int i=0;i<c.method().instructions.size();i++){
        AbstractInsnNode insn=c.method().instructions.get(i);if(!(insn instanceof MethodInsnNode call))continue;Frame<SourceValue>frame=c.frames()[i];if(frame==null)continue;
        Kind kind=null;List<Symbol> args=null;
        if(call.owner.equals(GAME_REGISTRY)){
            if(call.name.equals("addShapelessRecipe")){kind=Kind.SHAPELESS;args=invocationArgs(c,i,call,frame,true);}
            else if(call.name.equals("addSmelting")){kind=Kind.SMELTING;args=invocationArgs(c,i,call,frame,true);}
            else if(call.name.equals("registerFuelHandler")){kind=Kind.FUEL_HANDLER;args=invocationArgs(c,i,call,frame,true);}
            else if(call.name.equals("addRecipe")){
                args=invocationArgs(c,i,call,frame,true);if(call.desc.equals("(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V")&&args!=null&&args.size()==2)kind=Kind.SHAPED;
                else if(args!=null&&args.size()==1&&args.getFirst() instanceof ObjectSymbol object){
                    if(object.internalName().equals(SHAPED_ORE)){kind=Kind.SHAPED;args=object.args();}
                    else if(object.internalName().equals(SHAPELESS_ORE)){kind=Kind.SHAPELESS;args=object.args();}
                }
            }
        }else if(call.owner.equals(ORE_DICTIONARY)&&call.name.equals("registerOre")){kind=Kind.ORE_REGISTER;args=invocationArgs(c,i,call,frame,true);}
        if(kind!=null&&args!=null)templates.get(key).add(new Template(kind,args,key));
    }}}

    private void propagate(){for(int round=0;round<MAX_ROUNDS;round++){boolean changed=false;for(var entry:methods.entrySet()){MethodKey caller=entry.getKey();MethodContext c=entry.getValue();for(int i=0;i<c.method().instructions.size();i++){
        AbstractInsnNode insn=c.method().instructions.get(i);if(!(insn instanceof MethodInsnNode call))continue;MethodKey callee=new MethodKey(call.owner,call.name,call.desc);var source=templates.get(callee);MethodContext cc=methods.get(callee);if(source==null||source.isEmpty()||cc==null)continue;
        Frame<SourceValue>frame=c.frames()[i];if(frame==null)continue;List<Symbol>actual=invocationValuesIncludingReceiver(c,i,call,frame,true);if(actual==null)continue;Map<Integer,Symbol>subst=parameterSubstitution(cc.method(),actual);
        for(Template t:source){List<Symbol>a=t.args().stream().map(v->substitute(v,subst)).toList();if(templates.get(caller).add(new Template(t.kind(),a,t.directSource()))){changed=true;if(totalTemplates()>MAX_TEMPLATES)throw new IllegalArgumentException("Recipe template propagation budget exceeded");}}
    }}if(!changed)return;}diagnostics.add("Recipe helper propagation reached its round budget.");}
    private int totalTemplates(){return templates.values().stream().mapToInt(Set::size).sum();}
    private static Map<Integer,Symbol> parameterSubstitution(MethodNode method,List<Symbol>actual){LinkedHashMap<Integer,Symbol>m=new LinkedHashMap<>();int ai=0,local=0;if((method.access&Opcodes.ACC_STATIC)==0){if(ai>=actual.size())return Map.of();m.put(local++,actual.get(ai++));}for(Type t:Type.getArgumentTypes(method.desc)){if(ai>=actual.size())return Map.of();m.put(local,actual.get(ai++));local+=t.getSize();}return m;}
    private static Symbol substitute(Symbol v,Map<Integer,Symbol>m){
        if(v instanceof ParamSymbol p)return m.getOrDefault(p.local(),UnknownSymbol.INSTANCE);
        if(v instanceof ObjectSymbol o)return new ObjectSymbol(o.internalName(),o.constructorDescriptor(),o.args().stream().map(x->substitute(x,m)).toList());
        if(v instanceof ArraySymbol a)return new ArraySymbol(a.identity(),a.elements().stream().map(x->substitute(x,m)).toList());return v;
    }

    private Registration materialize(Template t){List<Value>values=t.args().stream().map(this::publicValue).toList();MethodKey s=t.directSource();return new Registration(t.kind(),values,s.owner(),s.name(),s.descriptor());}
    private Value publicValue(Symbol s){
        if(s instanceof TextSymbol v)return new TextValue(v.value());if(s instanceof NumberSymbol v)return new NumberValue(v.value());if(s instanceof CharacterSymbol v)return new CharacterValue(v.value());
        if(s instanceof RegistrySymbol v){var b=v.binding();return new RegistryValue(b.kind(),b.registryName(),b.legacyNamespace(),b.owner(),b.name());}
        if(s instanceof FieldSymbol v)return new FieldValue(v.owner(),v.name(),v.descriptor());
        if(s instanceof ObjectSymbol v)return new ObjectValue(v.internalName(),v.constructorDescriptor(),v.args().stream().map(this::publicValue).toList());
        if(s instanceof ArraySymbol v)return new ArrayValue(v.elements().stream().map(this::publicValue).toList());if(s instanceof ParamSymbol v)return new ParamValue(v.local());if(s==NullSymbol.INSTANCE)return NullValue.INSTANCE;return UnknownValue.INSTANCE;
    }

    private boolean isRoot(MethodContext c){MethodNode m=c.method();if(hasAnnotation(m.visibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")||hasAnnotation(m.invisibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;"))return true;return m.desc.contains("Lcpw/mods/fml/common/event/FML")&&m.desc.endsWith(")V");}
    private static boolean hasAnnotation(List<AnnotationNode>a,String d){return a!=null&&a.stream().anyMatch(x->d.equals(x.desc));}
    /** Constructors instantiated by reachable lifecycle roots (e.g. new RecipeRegistrar()) are roots. */
    private boolean isConstructedFromRoot(MethodKey candidate){
        if(!candidate.name().equals("<init>"))return false;
        for(var entry:methods.entrySet())if(isRoot(entry.getValue()))for(AbstractInsnNode insn:entry.getValue().method().instructions)if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")&&call.owner.equals(candidate.owner()))return true;
        return false;
    }

    private List<Symbol> invocationArgs(MethodContext c,int index,MethodInsnNode call,Frame<SourceValue>frame,boolean arrays){int n=Type.getArgumentTypes(call.desc).length;if(frame.getStackSize()<n)return null;List<Symbol>r=new ArrayList<>(n);int start=frame.getStackSize()-n;for(int i=0;i<n;i++)r.add(resolve(c,frame.getStack(start+i),index,0,new LinkedHashSet<>(),arrays));return r;}
    private List<Symbol> invocationValuesIncludingReceiver(MethodContext c,int index,MethodInsnNode call,Frame<SourceValue>frame,boolean arrays){int argc=Type.getArgumentTypes(call.desc).length;boolean stat=call.getOpcode()==Opcodes.INVOKESTATIC;int n=argc+(stat?0:1);if(frame.getStackSize()<n)return null;List<Symbol>r=new ArrayList<>(n);int start=frame.getStackSize()-n;for(int i=0;i<n;i++)r.add(resolve(c,frame.getStack(start+i),index,0,new LinkedHashSet<>(),arrays));return r;}

    private Symbol resolve(MethodContext c,SourceValue value,int current,int depth,Set<String>guard,boolean arrays){
        if(value==null||depth>40||value.insns==null||value.insns.isEmpty())return UnknownSymbol.INSTANCE;LinkedHashSet<Symbol>opts=new LinkedHashSet<>();for(AbstractInsnNode p:value.insns){Integer pi=c.indices().get(p);if(pi==null)continue;String g=c.owner().name+":"+c.method().name+c.method().desc+":"+pi+":"+arrays;if(!guard.add(g))continue;opts.add(resolveProducer(c,p,pi,current,depth+1,guard,arrays));guard.remove(g);}opts.remove(UnknownSymbol.INSTANCE);return opts.size()==1?opts.getFirst():UnknownSymbol.INSTANCE;
    }
    private Symbol resolveProducer(MethodContext c,AbstractInsnNode p,int pi,int current,int depth,Set<String>guard,boolean arrays){
        if(p instanceof LdcInsnNode ldc){if(ldc.cst instanceof String s)return new TextSymbol(s);if(ldc.cst instanceof Number n)return new NumberSymbol(n);if(ldc.cst instanceof Type t&&t.getSort()==Type.OBJECT)return new ObjectSymbol(t.getInternalName());}
        if(p instanceof IntInsnNode n&&(n.getOpcode()==Opcodes.BIPUSH||n.getOpcode()==Opcodes.SIPUSH))return new NumberSymbol(n.operand);
        if(p instanceof InsnNode n){int op=n.getOpcode();if(op==Opcodes.ACONST_NULL)return NullSymbol.INSTANCE;if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)return new NumberSymbol(op-Opcodes.ICONST_0);if(op==Opcodes.LCONST_0)return new NumberSymbol(0L);if(op==Opcodes.LCONST_1)return new NumberSymbol(1L);if(op==Opcodes.FCONST_0)return new NumberSymbol(0F);if(op==Opcodes.FCONST_1)return new NumberSymbol(1F);if(op==Opcodes.FCONST_2)return new NumberSymbol(2F);if(op==Opcodes.DCONST_0)return new NumberSymbol(0D);if(op==Opcodes.DCONST_1)return new NumberSymbol(1D);if(op==Opcodes.DUP){Frame<SourceValue>f=c.frames()[pi];if(f!=null&&f.getStackSize()>0)return resolve(c,f.getStack(f.getStackSize()-1),current,depth+1,guard,arrays);}}
        if(p instanceof VarInsnNode v&&isLoad(v.getOpcode())){if(c.parameterLocals().contains(v.var))return new ParamSymbol(v.var);Frame<SourceValue>f=c.frames()[pi];if(f!=null&&v.var<f.getLocals())return resolve(c,f.getLocal(v.var),current,depth+1,guard,arrays);}
        if(p instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC){var binding=fields.get(f.owner+"."+f.name+f.desc);if(binding!=null)return new RegistrySymbol(binding);ClassNode owner=classes.get(f.owner);if(owner!=null)for(FieldNode cf:owner.fields)if(cf.name.equals(f.name)&&cf.desc.equals(f.desc)&&cf.value!=null){if(cf.value instanceof String s)return new TextSymbol(s);if(cf.value instanceof Number n)return new NumberSymbol(n);}return new FieldSymbol(f.owner,f.name,f.desc);}
        if(p instanceof TypeInsnNode t){if(t.getOpcode()==Opcodes.NEW)return constructorObject(c,t.desc,pi,current,depth,guard,arrays);if(t.getOpcode()==Opcodes.ANEWARRAY)return arrays?array(c,t,pi,current,depth,guard):new ArraySymbol(c.owner().name+":"+pi,List.of());if(t.getOpcode()==Opcodes.CHECKCAST){Frame<SourceValue>f=c.frames()[pi];if(f!=null&&f.getStackSize()>0)return resolve(c,f.getStack(f.getStackSize()-1),current,depth+1,guard,arrays);}}
        if(p instanceof MethodInsnNode call){Frame<SourceValue>f=c.frames()[pi];if(f==null)return UnknownSymbol.INSTANCE;List<Symbol>a=invocationArgs(c,pi,call,f,arrays);if(call.owner.equals("java/lang/Character")&&call.name.equals("valueOf")&&a!=null&&a.size()==1&&a.getFirst() instanceof NumberSymbol n)return new CharacterSymbol((char)n.value().intValue());Type rt=Type.getReturnType(call.desc);int argc=Type.getArgumentTypes(call.desc).length;if(call.getOpcode()!=Opcodes.INVOKESTATIC&&rt.getSort()==Type.OBJECT&&f.getStackSize()>=argc+1){Symbol receiver=resolve(c,f.getStack(f.getStackSize()-argc-1),current,depth+1,guard,arrays);if(!(receiver instanceof UnknownSymbol))return receiver;}}
        return UnknownSymbol.INSTANCE;
    }
    private Symbol constructorObject(MethodContext c,String type,int newIndex,int current,int depth,Set<String>guard,boolean arrays){int limit=Math.min(c.method().instructions.size(),newIndex+240);for(int i=newIndex+1;i<limit;i++){AbstractInsnNode n=c.method().instructions.get(i);if(!(n instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL||!call.name.equals("<init>")||!call.owner.equals(type))continue;Frame<SourceValue>f=c.frames()[i];if(f==null)break;List<Symbol>a=invocationArgs(c,i,call,f,arrays);return new ObjectSymbol(type,call.desc,a==null?List.of():a);}return new ObjectSymbol(type);}
    private Symbol array(MethodContext c,TypeInsnNode allocation,int allocationIndex,int current,int depth,Set<String>guard){
        Frame<SourceValue>af=c.frames()[allocationIndex];int size=-1;if(af!=null&&af.getStackSize()>0){Symbol n=resolve(c,af.getStack(af.getStackSize()-1),allocationIndex,depth+1,guard,false);if(n instanceof NumberSymbol v)size=v.value().intValue();}if(size<0||size>512)return new ArraySymbol(c.owner().name+":"+allocationIndex,List.of());
        ArrayList<Symbol>elements=new ArrayList<>(Collections.nCopies(size,UnknownSymbol.INSTANCE));int limit=Math.min(current,c.method().instructions.size());
        for(int i=allocationIndex+1;i<limit;i++){AbstractInsnNode insn=c.method().instructions.get(i);if(!(insn instanceof InsnNode op)||op.getOpcode()!=Opcodes.AASTORE)continue;Frame<SourceValue>f=c.frames()[i];if(f==null||f.getStackSize()<3)continue;SourceValue arrayRef=f.getStack(f.getStackSize()-3);if(!originatesFrom(c,arrayRef,allocation,0,new LinkedHashSet<>()))continue;Symbol idx=resolve(c,f.getStack(f.getStackSize()-2),i,depth+1,new LinkedHashSet<>(guard),false);if(!(idx instanceof NumberSymbol ni))continue;int slot=ni.value().intValue();if(slot<0||slot>=size)continue;elements.set(slot,resolve(c,f.getStack(f.getStackSize()-1),i,depth+1,new LinkedHashSet<>(guard),true));}
        return new ArraySymbol(c.owner().name+":"+allocationIndex,elements);
    }
    private boolean originatesFrom(MethodContext c,SourceValue value,AbstractInsnNode target,int depth,Set<Integer>guard){
        if(value==null||value.insns==null||depth>32)return false;
        for(AbstractInsnNode producer:value.insns){
            if(producer==target)return true;Integer pi=c.indices().get(producer);if(pi==null||!guard.add(pi))continue;
            try{
                if(producer instanceof InsnNode n&&n.getOpcode()==Opcodes.DUP){Frame<SourceValue>f=c.frames()[pi];if(f!=null&&f.getStackSize()>0&&originatesFrom(c,f.getStack(f.getStackSize()-1),target,depth+1,guard))return true;}
                if(producer instanceof VarInsnNode v&&isLoad(v.getOpcode())){Frame<SourceValue>f=c.frames()[pi];if(f!=null&&v.var<f.getLocals()&&originatesFrom(c,f.getLocal(v.var),target,depth+1,guard))return true;}
                if(producer instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.CHECKCAST){Frame<SourceValue>f=c.frames()[pi];if(f!=null&&f.getStackSize()>0&&originatesFrom(c,f.getStack(f.getStackSize()-1),target,depth+1,guard))return true;}
            }finally{guard.remove(pi);}
        }
        return false;
    }
    private static boolean isLoad(int op){return op==Opcodes.ALOAD||op==Opcodes.ILOAD||op==Opcodes.LLOAD||op==Opcodes.FLOAD||op==Opcodes.DLOAD;}
}
