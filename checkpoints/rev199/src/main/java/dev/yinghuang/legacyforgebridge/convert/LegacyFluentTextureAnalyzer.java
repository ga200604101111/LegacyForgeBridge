package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Recovers a literal texture setter on the exact allocation stored in a static field.
 * This is property-level source evidence, NOT an icon/rendering/registration admission.
 * In particular it neither executes a constructor nor invents its material arguments.
 * Callers must separately join registry fields and prove lifecycle order, absence of later
 * mutations, icon callbacks, resource materialization and runtime presentation.
 */
public final class LegacyFluentTextureAnalyzer implements Opcodes {
    private static final String ITEM="net/minecraft/item/Item", BLOCK="net/minecraft/block/Block";
    private static final Set<String> ITEM_BASES=Set.of(ITEM,ITEM+"Sword",ITEM+"Tool",ITEM+"Pickaxe",
            ITEM+"Axe",ITEM+"Spade",ITEM+"Hoe",ITEM+"Armor",ITEM+"Bow",ITEM+"Food",ITEM+"Seeds",
            ITEM+"SeedFood",ITEM+"Reed",ITEM+"SimpleFoiled");
    private static final Set<String> BLOCK_BASES=Set.of(BLOCK,BLOCK+"Ore",BLOCK+"Container");
    public record FieldKey(String owner,String name,String descriptor) { }
    public record Evidence(FieldKey field,String implementationClass,boolean block,String texture,
                           String resourcePath,boolean resourcePresent,String sourceOwner,
                           String sourceMethod,String sourceDescriptor,int storeInstruction) { }
    public record Exclusion(FieldKey field,String reason) { }
    public record Result(List<Evidence> evidence,List<Exclusion> exclusions) {
        public Result { evidence=List.copyOf(evidence);exclusions=List.copyOf(exclusions); }
    }
    private record Site(ClassNode owner,MethodNode method,FieldInsnNode store,int index) { }
    private record Chain(TypeInsnNode allocation,String texture,Set<AbstractInsnNode> calls) { }
    private static final class Unproven extends RuntimeException {
        Unproven(String message){super(message,null,false,false);}
    }
    private final Map<String,ClassNode> classes=new TreeMap<>();
    private final Map<MethodNode,Frame<SourceValue>[]> frames=new IdentityHashMap<>();
    private final Set<String> resources=new HashSet<>();

    public Result analyze(Path sourceJar) throws IOException {
        Objects.requireNonNull(sourceJar,"sourceJar");
        classes.clear();frames.clear();resources.clear();
        try(JarFile jar=new JarFile(sourceJar.toFile())){
            var entries=jar.entries();
            while(entries.hasMoreElements()){
                var e=entries.nextElement();
                if(!resources.add(e.getName()))throw new IOException("Duplicate archive entry: "+e.getName());
                if(!e.getName().endsWith(".class"))continue;
                if(classes.size()>=20000||e.getSize()>4_194_304)throw new IOException("Source class budget exceeded");
                try(var in=jar.getInputStream(e)){
                    byte[] bytes=in.readNBytes(4_194_305);
                    if(bytes.length>4_194_304)throw new IOException("Source class budget exceeded");
                    ClassNode node=new ClassNode(ASM9);
                    new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    if(classes.putIfAbsent(node.name,node)!=null)throw new IOException("Duplicate class identity: "+node.name);
                }catch(IllegalArgumentException bad){throw new IOException("Cannot parse "+e.getName(),bad);}
            }
        }
        Map<FieldKey,List<Site>> writes=new LinkedHashMap<>();
        for(ClassNode c:classes.values())for(MethodNode m:c.methods){
            for(int i=0;i<m.instructions.size();i++){
                var n=m.instructions.get(i);
                if(n instanceof FieldInsnNode f&&f.getOpcode()==PUTSTATIC&&contentDescriptor(f.desc))
                    writes.computeIfAbsent(new FieldKey(f.owner,f.name,f.desc),k->new ArrayList<>()).add(new Site(c,m,f,i));
            }
        }
        List<Evidence> out=new ArrayList<>();List<Exclusion> excluded=new ArrayList<>();
        for(var entry:writes.entrySet()){
            FieldKey key=entry.getKey();
            try{
                if(entry.getValue().size()!=1)throw fail("multiple static writers; field identity is ambiguous");
                Site s=entry.getValue().getFirst();
                ClassNode declaring=classes.get(key.owner());
                if(declaring==null||declaring.fields.stream().noneMatch(f->f.name.equals(key.name())&&f.desc.equals(key.descriptor())&&(f.access&ACC_STATIC)!=0))
                    throw fail("static field declaration not proven (inherited owner aliases are not admitted)");
                if((s.method().access&ACC_STATIC)==0)throw fail("instance-dependent producer requires lifecycle context");
                if(!s.method().tryCatchBlocks.isEmpty())throw fail("exception-dependent producer");
                Frame<SourceValue>[] fs=frames(s.owner(),s.method());
                Frame<SourceValue> at=fs[s.index()];
                if(at==null||at.getStackSize()==0)throw fail("unreachable static store");
                Chain chain=chain(s,fs,at.getStack(at.getStackSize()-1),new HashSet<>(),0);
                if(chain.texture()==null)throw fail("no literal texture setter on stored allocation");
                String base=base(chain.allocation().desc);
                boolean block=BLOCK_BASES.contains(base);
                if(!assignableField(key.descriptor(),chain.allocation().desc))throw fail("field and allocation types disagree");
                checkSlice(s,fs,chain);
                String resource=resource(chain.texture(),block);
                out.add(new Evidence(key,chain.allocation().desc,block,chain.texture(),resource,
                        resources.contains(resource),s.owner().name,s.method().name,s.method().desc,s.index()));
            }catch(Unproven ex){excluded.add(new Exclusion(key,ex.getMessage()));}
        }
        return new Result(out,excluded);
    }
    private boolean contentDescriptor(String descriptor){
        if(!descriptor.startsWith("L")||!descriptor.endsWith(";"))return false;
        try{base(descriptor.substring(1,descriptor.length()-1));return true;}catch(Unproven no){return false;}
    }
    private boolean assignableField(String descriptor,String actual){
        String expected=Type.getType(descriptor).getInternalName();
        if(expected.equals(actual))return true;
        Set<String> seen=new HashSet<>();String c=actual;
        while(classes.containsKey(c)&&seen.add(c)){
            c=classes.get(c).superName;if(expected.equals(c))return true;
        }
        String base=base(actual);
        return expected.equals(ITEM)&&ITEM_BASES.contains(base)||expected.equals(BLOCK)&&BLOCK_BASES.contains(base);
    }
    private String base(String type){
        Set<String> seen=new HashSet<>();String c=type;
        while(classes.containsKey(c)){
            if(!seen.add(c)||seen.size()>128)throw fail("cyclic or excessive source hierarchy");
            c=classes.get(c).superName;
        }
        if(ITEM_BASES.contains(c)||BLOCK_BASES.contains(c))return c;
        throw fail("missing or unsupported source ancestry: "+c);
    }
    private Frame<SourceValue>[] frames(ClassNode c,MethodNode m){
        if(frames.containsKey(m))return frames.get(m);
        try{var f=new Analyzer<>(new SourceInterpreter()).analyze(c.name,m);frames.put(m,f);return f;}
        catch(AnalyzerException|RuntimeException bad){throw fail("source frame analysis failed: "+bad.getMessage());}
    }
    private Chain chain(Site s,Frame<SourceValue>[] fs,SourceValue value,Set<AbstractInsnNode> visiting,int depth){
        if(depth>128||value==null||value.insns.size()!=1)throw fail("merged, cyclic or external allocation expression");
        AbstractInsnNode n=value.insns.iterator().next();
        if(!visiting.add(n))throw fail("cyclic allocation expression");
        try{
            int i=s.method().instructions.indexOf(n);Frame<SourceValue> f=fs[i];int op=n.getOpcode();
            if(n instanceof TypeInsnNode t&&op==NEW){base(t.desc);return new Chain(t,null,new HashSet<>());}
            if(op==DUP||op==CHECKCAST)return chain(s,fs,f.getStack(f.getStackSize()-1),visiting,depth+1);
            // Locals are intentionally excluded: an unused setter on an alias can mutate the object.
            if(n instanceof VarInsnNode)throw fail("reference-local alias requires mutation closure");
            if(n instanceof MethodInsnNode call&&(op==INVOKEVIRTUAL||op==INVOKESPECIAL)){
                Type[] args=Type.getArgumentTypes(call.desc);int receiver=f.getStackSize()-args.length-1;
                if(receiver<0)throw fail("invalid fluent stack");
                Chain prior=chain(s,fs,f.getStack(receiver),visiting,depth+1);
                String b=base(prior.allocation().desc);boolean block=BLOCK_BASES.contains(b);
                if(!vanillaSetter(prior.allocation().desc,call,block))throw fail("unknown or overridden fluent call: "+call.owner+"."+call.name+call.desc);
                String texture=prior.texture();
                if(textureSetter(call,block)){
                    SourceValue text=f.getStack(receiver+1);
                    if(text.insns.size()!=1||!(text.insns.iterator().next() instanceof LdcInsnNode lit)||!(lit.cst instanceof String str))
                        throw fail("texture argument is not one literal string");
                    texture=str;
                }
                prior.calls().add(call);return new Chain(prior.allocation(),texture,prior.calls());
            }
            throw fail("unsupported allocation expression opcode "+op);
        }finally{visiting.remove(n);}
    }
    private boolean textureSetter(MethodInsnNode call,boolean block){
        return (block?Set.of("setBlockTextureName","func_149658_d"):Set.of("setTextureName","func_111206_d")).contains(call.name)
                &&call.desc.equals("(Ljava/lang/String;)L"+(block?BLOCK:ITEM)+";");
    }
    private boolean vanillaSetter(String actual,MethodInsnNode call,boolean block){
        if(call.getOpcode()!=INVOKEVIRTUAL)return false;
        String parent=block?BLOCK:ITEM,ret="L"+parent+";";
        boolean known=textureSetter(call,block)
                ||Set.of(block?"setBlockName":"setUnlocalizedName",block?"func_149663_c":"func_77655_b").contains(call.name)&&call.desc.equals("(Ljava/lang/String;)"+ret)
                ||Set.of("setCreativeTab",block?"func_149647_a":"func_77637_a").contains(call.name)&&call.desc.equals("(Lnet/minecraft/creativetab/CreativeTabs;)"+ret)
                ||!block&&Set.of("setMaxDamage","func_77656_e").contains(call.name)&&call.desc.equals("(I)"+ret)
                ||!block&&Set.of("setMaxStackSize","func_77625_d").contains(call.name)&&call.desc.equals("(I)"+ret)
                ||!block&&Set.of("setHasSubtypes","func_77627_a").contains(call.name)&&call.desc.equals("(Z)"+ret)
                ||!block&&Set.of("setFull3D","func_77664_n").contains(call.name)&&call.desc.equals("()"+ret)
                ||block&&Set.of("setHardness","func_149711_c","setResistance","func_149752_b","setLightLevel","func_149715_a").contains(call.name)&&call.desc.equals("(F)"+ret);
        if(!known)return false;
        // The JVM call owner must be on the receiver's source/vanilla ancestry. Reject all
        // source overrides, including fluent methods returning a different receiver.
        boolean ownerFound=false;Set<String> seen=new HashSet<>();String c=actual;
        while(classes.containsKey(c)){
            if(!seen.add(c))return false;
            if(c.equals(call.owner))ownerFound=true;
            ClassNode cls=classes.get(c);
            if(cls.methods.stream().anyMatch(m->m.name.equals(call.name)&&m.desc.equals(call.desc)))return false;
            c=cls.superName;
        }
        if(c.equals(call.owner)||call.owner.equals(parent))ownerFound=true;
        return ownerFound&&(block?BLOCK_BASES:ITEM_BASES).contains(c);
    }
    private void checkSlice(Site s,Frame<SourceValue>[] fs,Chain chain){
        int start=s.method().instructions.indexOf(chain.allocation()),constructors=0;
        for(int i=start+1;i<s.index();i++){
            AbstractInsnNode n=s.method().instructions.get(i);
            if(n instanceof JumpInsnNode||n instanceof TableSwitchInsnNode||n instanceof LookupSwitchInsnNode)
                throw fail("control flow crosses allocation/configuration slice");
            if(n instanceof VarInsnNode)throw fail("local alias inside allocation/configuration slice");
            if(n instanceof FieldInsnNode f&&(f.getOpcode()==PUTFIELD||f.getOpcode()==PUTSTATIC))
                throw fail("allocation/configuration slice escapes through another field");
            if(n instanceof MethodInsnNode call&&!chain.calls().contains(call)){
                if(call.getOpcode()!=INVOKESPECIAL||!call.name.equals("<init>")||!call.owner.equals(chain.allocation().desc))
                    throw fail("extra call inside allocation/configuration slice");
                Frame<SourceValue> frame=fs[i];int pos=frame.getStackSize()-Type.getArgumentTypes(call.desc).length-1;
                if(pos<0||origin(s,fs,frame.getStack(pos),0)!=chain.allocation())throw fail("constructor receiver does not match allocation");
                constructors++;
            }
        }
        if(constructors!=1)throw fail("exactly one matching constructor invocation is required");
    }
    private AbstractInsnNode origin(Site s,Frame<SourceValue>[] fs,SourceValue v,int depth){
        if(depth>128||v==null||v.insns.size()!=1)throw fail("ambiguous constructor receiver");
        AbstractInsnNode n=v.insns.iterator().next();int op=n.getOpcode();
        if(op==DUP||op==CHECKCAST){Frame<SourceValue> f=fs[s.method().instructions.indexOf(n)];return origin(s,fs,f.getStack(f.getStackSize()-1),depth+1);}
        return n;
    }
    private static String resource(String texture,boolean block){
        if(!texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw fail("noncanonical texture identifier; do not guess normalization");
        String[] id=texture.split(":",2);
        if(id[1].startsWith("/")||id[1].endsWith("/")||Arrays.stream(id[1].split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))
            throw fail("unsafe or ambiguous texture resource path");
        return "assets/"+id[0]+"/textures/"+(block?"blocks/":"items/")+id[1]+".png";
    }
    private static Unproven fail(String reason){return new Unproven(reason);}
}
