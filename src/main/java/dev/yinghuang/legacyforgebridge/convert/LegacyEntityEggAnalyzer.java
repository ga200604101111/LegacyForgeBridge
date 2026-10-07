package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

/**
 * Proves the common legacy entity-egg helper shape without loading source classes.
 *
 * <p>An admitted helper must use the same class/name/id tuple in registerModEntity and in a
 * source-static Map entry keyed by that id, whose value is constructed as
 * new Info(id, primaryColor, secondaryColor). The template is propagated through source helpers
 * to lifecycle roots. Names of mods, helpers, fields, entities and egg classes are not selectors.</p>
 */
public final class LegacyEntityEggAnalyzer {
    private static final String ENTITY_REGISTRY="cpw/mods/fml/common/registry/EntityRegistry";
    private static final String REGISTER_DESC="(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V";
    private static final int MAX_ROUNDS=64, MAX_TEMPLATES=32768;

    public record Rule(String registryName,String sourceClass,int numericId,int primaryColor,int secondaryColor,
                       String sourceOwner,String sourceMethod) {}
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules); diagnostics=List.copyOf(diagnostics); }
    }

    private sealed interface S permits Txt,Num,Typ,Fld,Obj,Par,Unk {}
    private record Txt(String v) implements S {}
    private record Num(Number v) implements S {}
    private record Typ(String v) implements S {}
    private record Fld(String owner,String name,String desc) implements S {}
    private record Obj(String type,String desc,List<S> args) implements S {
        Obj { args=List.copyOf(args); }
    }
    private record Par(int local) implements S {}
    private enum Unk implements S { X }

    private record K(String owner,String name,String desc) {}
    private record T(S type,S name,S id,S primary,S secondary,K source) {}
    private record C(ClassNode owner,MethodNode method,Frame<SourceValue>[] frames,
                     Map<AbstractInsnNode,Integer> index,Set<Integer> params) {}

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final Map<K,C> methods=new LinkedHashMap<>();
    private final Map<K,LinkedHashSet<T>> templates=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jar)throws IOException {
        classes.clear();methods.clear();templates.clear();diagnostics.clear();
        load(jar); frames(); direct(); propagate();
        var lifecycle=new LegacyLifecycleAnalyzer().analyze(jar);
        LinkedHashSet<Rule> out=new LinkedHashSet<>();
        for(var e:methods.entrySet()){
            if(!root(e.getValue())&&!e.getKey().name().equals("<clinit>"))continue;
            for(T t:templates.getOrDefault(e.getKey(),new LinkedHashSet<>())){
                Rule r=materialize(t,lifecycle);
                if(r!=null)out.add(r);
            }
        }
        return new Analysis(List.copyOf(out),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private void load(Path path)throws IOException {
        try(JarFile jar=new JarFile(path.toFile())){
            var es=jar.entries();
            while(es.hasMoreElements()){
                JarEntry e=es.nextElement();
                if(e.isDirectory()||!e.getName().endsWith(".class"))continue;
                try(InputStream in=jar.getInputStream(e)){
                    ClassNode n=new ClassNode(Opcodes.ASM9);
                    new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    classes.put(n.name,n);
                }catch(RuntimeException bad){
                    diagnostics.add("Unreadable entity-egg class "+e.getName());
                }
            }
        }
    }

    private void frames(){
        for(ClassNode o:classes.values())for(MethodNode m:o.methods){
            K k=new K(o.name,m.name,m.desc);
            try{
                Frame<SourceValue>[] f=new Analyzer<>(new SourceInterpreter()).analyze(o.name,m);
                Map<AbstractInsnNode,Integer> ix=new IdentityHashMap<>();
                for(int i=0;i<m.instructions.size();i++)ix.put(m.instructions.get(i),i);
                methods.put(k,new C(o,m,f,ix,params(m)));
                templates.put(k,new LinkedHashSet<>());
            }catch(AnalyzerException|RuntimeException bad){
                diagnostics.add("Entity-egg dataflow unavailable for "+o.name+"."+m.name+m.desc);
            }
        }
    }

    private static Set<Integer> params(MethodNode m){
        LinkedHashSet<Integer> s=new LinkedHashSet<>();int l=0;
        if((m.access&Opcodes.ACC_STATIC)==0)s.add(l++);
        for(Type t:Type.getArgumentTypes(m.desc)){s.add(l);l+=t.getSize();}
        return s;
    }

    private void direct(){
        for(var e:methods.entrySet()){
            K k=e.getKey();C c=e.getValue();
            for(int i=0;i<c.method().instructions.size();i++){
                AbstractInsnNode n=c.method().instructions.get(i);
                if(!(n instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                        ||!ENTITY_REGISTRY.equals(call.owner)||!"registerModEntity".equals(call.name)
                        ||!REGISTER_DESC.equals(call.desc))continue;
                Frame<SourceValue> f=c.frames()[i];
                if(f==null||f.getStackSize()<7)continue;
                int p=f.getStackSize()-7;
                S type=value(c,f.getStack(p),i,0,new HashSet<>());
                S name=value(c,f.getStack(p+1),i,0,new HashSet<>());
                S id=value(c,f.getStack(p+2),i,0,new HashSet<>());
                T t=eggWrite(c,type,name,id,k);
                if(t!=null)templates.get(k).add(t);
            }
        }
    }

    private T eggWrite(C c,S type,S name,S id,K source){
        T found=null;
        for(int i=0;i<c.method().instructions.size();i++){
            AbstractInsnNode n=c.method().instructions.get(i);
            if(!(n instanceof MethodInsnNode call)
                    ||!Set.of("java/util/Map","java/util/HashMap","java/util/LinkedHashMap").contains(call.owner)
                    ||!"put".equals(call.name)||!"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(call.desc))continue;
            Frame<SourceValue> f=c.frames()[i];
            if(f==null||f.getStackSize()<3)continue;
            int p=f.getStackSize()-3;
            S map=value(c,f.getStack(p),i,0,new HashSet<>());
            S key=value(c,f.getStack(p+1),i,0,new HashSet<>());
            S val=value(c,f.getStack(p+2),i,0,new HashSet<>());
            if(!(map instanceof Fld field)||!Set.of("Ljava/util/Map;","Ljava/util/HashMap;","Ljava/util/LinkedHashMap;").contains(field.desc())
                    ||!Objects.equals(key,id)||!(val instanceof Obj obj)||!"(III)V".equals(obj.desc())
                    ||obj.args().size()!=3||!Objects.equals(obj.args().get(0),id)
                    ||!storesArgs(obj.type()))continue;
            T candidate=new T(type,name,id,obj.args().get(1),obj.args().get(2),source);
            if(found!=null&&!found.equals(candidate))return null;
            found=candidate;
        }
        return found;
    }

    private boolean storesArgs(String owner){
        ClassNode n=classes.get(owner);if(n==null)return false;
        MethodNode ctor=n.methods.stream().filter(m->m.name.equals("<init>")&&m.desc.equals("(III)V")).findFirst().orElse(null);
        if(ctor==null)return false;
        Set<Integer> seen=new HashSet<>();
        for(AbstractInsnNode x:ctor.instructions){
            if(!(x instanceof FieldInsnNode f)||x.getOpcode()!=Opcodes.PUTFIELD||!f.owner.equals(owner)||!f.desc.equals("I"))continue;
            AbstractInsnNode v=prev(x),self=prev(v);
            if(!(self instanceof VarInsnNode a)||a.getOpcode()!=Opcodes.ALOAD||a.var!=0
                    ||!(v instanceof VarInsnNode q)||q.getOpcode()!=Opcodes.ILOAD||q.var<1||q.var>3)return false;
            seen.add(q.var);
        }
        return seen.equals(Set.of(1,2,3));
    }

    private void propagate(){
        for(int round=0;round<MAX_ROUNDS;round++){
            boolean changed=false;
            for(var e:methods.entrySet()){
                K caller=e.getKey();C c=e.getValue();
                for(int i=0;i<c.method().instructions.size();i++){
                    if(!(c.method().instructions.get(i) instanceof MethodInsnNode call))continue;
                    K callee=new K(call.owner,call.name,call.desc);
                    var ts=templates.get(callee);C cc=methods.get(callee);
                    if(ts==null||ts.isEmpty()||cc==null)continue;
                    Frame<SourceValue> f=c.frames()[i];if(f==null)continue;
                    List<S> actual=allArgs(c,i,call,f);if(actual==null)continue;
                    Map<Integer,S> m=substitutions(cc.method(),actual);
                    for(T t:ts){
                        T v=new T(sub(t.type(),m),sub(t.name(),m),sub(t.id(),m),sub(t.primary(),m),sub(t.secondary(),m),t.source());
                        if(templates.get(caller).add(v)){
                            changed=true;
                            if(count()>MAX_TEMPLATES)throw new IllegalArgumentException("Entity-egg propagation budget exceeded");
                        }
                    }
                }
            }
            if(!changed)return;
        }
        diagnostics.add("Entity-egg helper propagation reached round budget.");
    }

    private int count(){return templates.values().stream().mapToInt(Set::size).sum();}

    private static Map<Integer,S> substitutions(MethodNode method,List<S> actual){
        LinkedHashMap<Integer,S> r=new LinkedHashMap<>();int a=0,l=0;
        if((method.access&Opcodes.ACC_STATIC)==0){if(a>=actual.size())return Map.of();r.put(l++,actual.get(a++));}
        for(Type t:Type.getArgumentTypes(method.desc)){if(a>=actual.size())return Map.of();r.put(l,actual.get(a++));l+=t.getSize();}
        return r;
    }

    private static S sub(S s,Map<Integer,S> m){
        if(s instanceof Par p)return m.getOrDefault(p.local(),Unk.X);
        if(s instanceof Obj o)return new Obj(o.type(),o.desc(),o.args().stream().map(v->sub(v,m)).toList());
        return s;
    }

    private Rule materialize(T t,LegacyLifecycleAnalyzer.Analysis lifecycle){
        if(!(t.type() instanceof Typ type)||!(t.name() instanceof Txt name)||name.v().isBlank())return null;
        Integer p=integer(t.primary()),q=integer(t.secondary());
        if(p==null||q==null||p<0||p>0xFFFFFF||q<0||q>0xFFFFFF)return null;
        var matches=lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY).stream()
                .filter(r->r.arguments().size()>=3)
                .filter(r->r.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue v&&v.internalName().equals(type.v()))
                .filter(r->r.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue v&&v.value().equals(name.v()))
                .toList();
        if(matches.size()!=1||!(matches.getFirst().arguments().get(2) instanceof LegacyLifecycleAnalyzer.NumberValue id))return null;
        Integer numeric=exact(id.value());if(numeric==null)return null;
        Integer sourceId=integer(t.id());if(sourceId!=null&&!sourceId.equals(numeric))return null;
        return new Rule(name.v(),type.v(),numeric,p,q,t.source().owner(),t.source().name());
    }

    private static Integer integer(S s){return s instanceof Num n?exact(n.v()):null;}
    private static Integer exact(Number n){double d=n.doubleValue();int i=n.intValue();return Double.isFinite(d)&&d==i?i:null;}

    private S value(C c,SourceValue sv,int current,int depth,Set<String> guard){
        if(sv==null||sv.insns==null||sv.insns.isEmpty()||depth>32)return Unk.X;
        LinkedHashSet<S> out=new LinkedHashSet<>();
        for(AbstractInsnNode x:sv.insns){
            Integer i=c.index().get(x);if(i==null)continue;
            String g=c.owner().name+":"+c.method().name+c.method().desc+":"+i;
            if(!guard.add(g))continue;
            try{out.add(producer(c,x,i,current,depth+1,guard));}finally{guard.remove(g);}
        }
        out.remove(Unk.X);return out.size()==1?out.getFirst():Unk.X;
    }

    private S producer(C c,AbstractInsnNode x,int i,int current,int depth,Set<String> guard){
        if(x instanceof LdcInsnNode l){
            if(l.cst instanceof String s)return new Txt(s);
            if(l.cst instanceof Number n)return new Num(n);
            if(l.cst instanceof Type t&&t.getSort()==Type.OBJECT)return new Typ(t.getInternalName());
        }
        if(x instanceof IntInsnNode n&&(x.getOpcode()==Opcodes.BIPUSH||x.getOpcode()==Opcodes.SIPUSH))return new Num(n.operand);
        if(x.getOpcode()>=Opcodes.ICONST_M1&&x.getOpcode()<=Opcodes.ICONST_5)return new Num(x.getOpcode()-Opcodes.ICONST_0);
        if(x instanceof VarInsnNode v&&load(v.getOpcode())){
            if(c.params().contains(v.var))return new Par(v.var);
            Frame<SourceValue> f=c.frames()[i];if(f!=null&&v.var<f.getLocals())return value(c,f.getLocal(v.var),current,depth+1,guard);
        }
        if(x instanceof FieldInsnNode f&&x.getOpcode()==Opcodes.GETSTATIC)return new Fld(f.owner,f.name,f.desc);
        if(x instanceof TypeInsnNode t){
            if(x.getOpcode()==Opcodes.NEW)return object(c,t.desc,i,current,depth,guard);
            if(x.getOpcode()==Opcodes.CHECKCAST){
                Frame<SourceValue> f=c.frames()[i];if(f!=null&&f.getStackSize()>0)return value(c,f.getStack(f.getStackSize()-1),current,depth+1,guard);
            }
        }
        if(x.getOpcode()==Opcodes.DUP){
            Frame<SourceValue> f=c.frames()[i];if(f!=null&&f.getStackSize()>0)return value(c,f.getStack(f.getStackSize()-1),current,depth+1,guard);
        }
        if(x instanceof MethodInsnNode call){
            Frame<SourceValue> f=c.frames()[i];if(f==null)return Unk.X;
            List<S> args=args(c,i,call,f);
            if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/lang/Integer")&&call.name.equals("valueOf")
                    &&call.desc.equals("(I)Ljava/lang/Integer;")&&args!=null&&args.size()==1)return args.getFirst();
            Type result=Type.getReturnType(call.desc);int ac=Type.getArgumentTypes(call.desc).length;
            if(call.getOpcode()!=Opcodes.INVOKESTATIC&&result.getSort()==Type.OBJECT&&f.getStackSize()>=ac+1){
                S receiver=value(c,f.getStack(f.getStackSize()-ac-1),current,depth+1,guard);
                if(receiver!=Unk.X)return receiver;
            }
        }
        return Unk.X;
    }

    private Obj object(C c,String type,int allocation,int current,int depth,Set<String> guard){
        int limit=Math.min(current,Math.min(c.method().instructions.size(),allocation+160));
        for(int i=allocation+1;i<limit;i++){
            if(!(c.method().instructions.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL
                    ||!call.name.equals("<init>")||!call.owner.equals(type))continue;
            Frame<SourceValue> f=c.frames()[i];if(f==null)break;
            List<S> a=args(c,i,call,f);return new Obj(type,call.desc,a==null?List.of():a);
        }
        return new Obj(type,null,List.of());
    }

    private List<S> args(C c,int index,MethodInsnNode call,Frame<SourceValue> f){
        int n=Type.getArgumentTypes(call.desc).length;if(f.getStackSize()<n)return null;
        int p=f.getStackSize()-n;List<S> r=new ArrayList<>();
        for(int i=0;i<n;i++)r.add(value(c,f.getStack(p+i),index,0,new HashSet<>()));
        return r;
    }

    private List<S> allArgs(C c,int index,MethodInsnNode call,Frame<SourceValue> f){
        int n=Type.getArgumentTypes(call.desc).length+(call.getOpcode()==Opcodes.INVOKESTATIC?0:1);
        if(f.getStackSize()<n)return null;
        int p=f.getStackSize()-n;List<S> r=new ArrayList<>();
        for(int i=0;i<n;i++)r.add(value(c,f.getStack(p+i),index,0,new HashSet<>()));
        return r;
    }

    private static boolean root(C c){
        MethodNode m=c.method();
        return annotation(m.visibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")
                ||annotation(m.invisibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")
                ||(m.desc.contains("Lcpw/mods/fml/common/event/FML")&&m.desc.endsWith(")V"));
    }
    private static boolean annotation(List<AnnotationNode> a,String d){return a!=null&&a.stream().anyMatch(v->d.equals(v.desc));}
    private static boolean load(int op){return op==Opcodes.ALOAD||op==Opcodes.ILOAD||op==Opcodes.LLOAD||op==Opcodes.FLOAD||op==Opcodes.DLOAD;}
    private static AbstractInsnNode prev(AbstractInsnNode n){for(AbstractInsnNode x=n==null?null:n.getPrevious();x!=null;x=x.getPrevious())if(x.getOpcode()>=0)return x;return null;}
}
