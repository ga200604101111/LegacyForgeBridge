package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Bounded, non-executing interpretation of legacy icon registration and metadata selectors.
 * Source classes are never defined. Results are admitted only when concrete strings reach
 * IIconRegister and the selected getter returns that exact icon token. Unsupported control flow,
 * ambiguous allocations, external state and custom world renderers remain explicit exclusions.
 */
public final class LegacyIconTableAnalyzer implements Opcodes {
    public record Variant(int metadata, List<String> faceIcons, List<Double> bounds, List<Double> inventoryBounds, int renderType) {
        public Variant { faceIcons=List.copyOf(faceIcons); bounds=List.copyOf(bounds); inventoryBounds=List.copyOf(inventoryBounds); }
    }
    public record Result(String registryName, boolean block, List<Variant> variants, String limitation) {
        public Result { variants=List.copyOf(variants); }
    }
    private record Icon(String id) { }
    private record Symbol(String owner, String name) { }
    private enum Unknown { VALUE }
    private static final Object U=Unknown.VALUE;
    private static final class Obj {
        final String type; final Map<String,Object> fields=new HashMap<>();
        Obj(String type) { this.type=type; }
    }
    private record Method(ClassNode owner, MethodNode node, Frame<SourceValue>[] frames) { }
    private final Map<String,ClassNode> classes=new TreeMap<>();
    private final Map<MethodNode,Method> methods=new IdentityHashMap<>();
    private final Map<String,Object> statics=new HashMap<>();
    private final Set<String> initialized=new HashSet<>();
    private final Map<AbstractInsnNode,Object> values=new IdentityHashMap<>();
    private int budget;
    private boolean readOnly;
    private final Set<String> failedStatics=new HashSet<>();
    private static final class Unproven extends RuntimeException {
        Unproven(String message) { super(message,null,false,false); }
    }

    public List<Result> analyze(Path jarPath, List<LegacyRegistryAnalyzer.Registration> registrations) throws IOException {
        classes.clear(); methods.clear(); statics.clear(); initialized.clear(); failedStatics.clear(); values.clear(); readOnly=false;
        try (JarFile jar=new JarFile(jarPath.toFile())) {
            var entries=jar.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement();
                if(!e.getName().endsWith(".class")) continue;
                try(var in=jar.getInputStream(e)) {
                    ClassNode c=new ClassNode(ASM9);
                    new ClassReader(in.readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    classes.put(c.name,c);
                }
            }
        }
        List<Result> out=new ArrayList<>();
        for(var r:registrations) {
            boolean block=r.kind()==LegacyRegistryAnalyzer.Kind.BLOCK;
            List<Variant> table=new ArrayList<>(); String limitation="";
            try {
                budget=200_000; values.clear(); readOnly=false;
                Obj obj=allocation(r);
                String register="(Lnet/minecraft/client/renderer/texture/IIconRegister;)V";
                Method reg=find(obj.type,block?List.of("registerBlockIcons","func_149651_a"):List.of("registerIcons","func_94581_a"),register);
                if(reg!=null) run(reg,obj,List.of(new Symbol("lfb","icon-register")),0);
                else {
                    Object name=obj.fields.get("$texture");
                    if(!(name instanceof String s)) throw fail("no source-proven texture setter or icon registration");
                    obj.fields.put(block?"blockIcon":"itemIcon",new Icon(s));
                    obj.fields.put(block?"field_149761_L":"field_77791_bV",new Icon(s));
                }
                Method getter=find(obj.type,block?List.of("getIcon","func_149691_a"):List.of("getIconFromDamage","func_77617_a"),block?"(II)Lnet/minecraft/util/IIcon;":"(I)Lnet/minecraft/util/IIcon;");
                int render=0;
                if(block) {
                    Method rt=find(obj.type,List.of("getRenderType","func_149645_b"),"()I");
                    render=rt==null?baseRenderType(obj.type):num(run(rt,obj,List.of(),0)).intValue();
                    if(render!=0 && render!=1 && render!=6) throw fail("custom world/inventory renderer requires geometric conversion; renderType="+render);
                    // A tile renderer can add geometry even if getRenderType happens to be zero.
                    if(subclass(obj.type,"net/minecraft/block/BlockContainer")) throw fail("BlockEntity renderer not represented by an icon table");
                } else {
                    Method multi=find(obj.type,List.of("requiresMultipleRenderPasses","func_77623_v"),"()Z");
                    if(multi!=null && num(run(multi,obj,List.of(),0)).intValue()!=0) throw fail("multi-pass item renderer requires layer conversion");
                    if(find(obj.type,List.of("getIcon"),"(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;")!=null)
                        throw fail("stack-dependent getIcon override is not a metadata-only selector");
                }
                int max=block?16:256;
                for(int meta=0;meta<max;meta++) {
                    budget=50_000;
                    try {
                        List<String> icons=new ArrayList<>();
                        for(int side=0;side<(block?6:1);side++) {
                            readOnly=true;
                            Object value=getter==null?obj.fields.get(block?"blockIcon":"itemIcon"):
                                    run(getter,obj,block?List.of(side,meta):List.of(meta),0);
                            if(!(value instanceof Icon icon)) throw fail("getter did not return a registered icon");
                            icons.add(icon.id());
                        }
                        readOnly=false;
                        List<Double> bounds=List.of(0d,0d,0d,1d,1d,1d),inventoryBounds=bounds;
                        if(block) {
                            Obj inv=copy(obj);
                            Method inventoryShape=find(obj.type,List.of("setBlockBoundsForItemRender","func_149683_g"),"()V");
                            if(inventoryShape!=null)run(inventoryShape,inv,List.of(),0);
                            if(inv.fields.get("$bounds") instanceof List<?> box){List<Double> coords=new ArrayList<>();for(Object n:box)coords.add(num(n).doubleValue());inventoryBounds=List.copyOf(coords);}
                            Obj state=copy(obj);
                            Method shape=find(obj.type,List.of("setBlockBoundsBasedOnState","func_149719_a"),"(Lnet/minecraft/world/IBlockAccess;III)V");
                            if(shape!=null) run(shape,state,List.of(new Symbol("lfb-world",String.valueOf(meta)),0,0,0),0);
                            Object b=state.fields.get("$bounds");
                            if(b instanceof List<?> list) {
                                List<Double> box=new ArrayList<>(); for(Object n:list) box.add(num(n).doubleValue());
                                bounds=List.copyOf(box);
                            }
                            if(bounds.size()!=6 || bounds.stream().anyMatch(d->!Double.isFinite(d)||d<0||d>1)
                                    || bounds.get(0)>bounds.get(3)||bounds.get(1)>bounds.get(4)||bounds.get(2)>bounds.get(5))
                                throw fail("unsupported block bounds");
                        }
                        if(inventoryBounds.size()!=6||inventoryBounds.stream().anyMatch(d->!Double.isFinite(d)||d<0||d>1))throw fail("unsupported inventory bounds");
                        table.add(new Variant(meta,icons,bounds,inventoryBounds,render));
                    } catch(RuntimeException unsupported) {
                        if(meta==0) throw fail("metadata 0: "+unsupported.getMessage());
                        limitation="some metadata values require unsupported state or lie outside a source icon table";
                    }
                }
                if(!block && limitation.isEmpty()) limitation="metadata-only icon surface 0..255; custom IItemRenderer geometry remains a separate gate";
            } catch(RuntimeException unsupported) {
                table.clear(); limitation=unsupported.getMessage();
            }
            out.add(new Result(r.registryName(),block,table,limitation));
        }
        return List.copyOf(out);
    }

    private Obj allocation(LegacyRegistryAnalyzer.Registration r) {
        if(r.implementationClass()==null||!classes.containsKey(r.implementationClass())) throw fail("source implementation class unavailable");
        List<Obj> candidates=new ArrayList<>();
        // Match a proved GameRegistry registration to its concrete call expression. A name alone
        // is never used as a texture: it is only an identity key from the existing registry proof.
        for(ClassNode c:classes.values()) for(MethodNode m:c.methods) {
            boolean relevant=false;
            for(AbstractInsnNode n:m.instructions) if(n instanceof LdcInsnNode l && r.registryName().equals(l.cst)) { relevant=true; break; }
            if(!relevant) continue;
            Method method=frames(c,m);
            if(method==null) continue;
            for(int i=0;i<m.instructions.size();i++) {
                if(!(m.instructions.get(i) instanceof MethodInsnNode call)) continue;
                if(!registrationSink(call.owner,call.name,call.desc,r.kind(),new HashSet<>()))continue;
                Type[] types=Type.getArgumentTypes(call.desc); Frame<SourceValue> frame=method.frames()[i];
                if(frame==null||frame.getStackSize()<types.length) continue;
                int offset=frame.getStackSize()-types.length; boolean named=false;
                for(int a=0;a<types.length;a++) if(types[a].getDescriptor().equals("Ljava/lang/String;")) {
                    try { named|=r.registryName().equals(value(method,frame.getStack(offset+a),0)); }
                    catch(Unproven ignored) { }
                }
                if(!named) continue;
                for(int a=0;a<types.length;a++) if(types[a].getSort()==Type.OBJECT &&
                        (types[a].getInternalName().equals("net/minecraft/block/Block")||types[a].getInternalName().equals("net/minecraft/item/Item"))) {
                    try {
                        Object v=value(method,frame.getStack(offset+a),0);
                        if(v instanceof Obj o && o.type.equals(r.implementationClass()) && !candidates.contains(o)) candidates.add(o);
                    } catch(Unproven ignored) { }
                }
            }
        }
        if(candidates.size()>1) throw fail("ambiguous source allocation for registered identity");
        if(candidates.size()==1) return copy(candidates.getFirst());
        // A constructor alone cannot prove later fluent setters. Do not silently replace a
        // failed expression trace with an independently constructed, differently configured object.
        throw fail("registered allocation and fluent configuration not proven");
    }

    private boolean registrationSink(String owner,String name,String desc,LegacyRegistryAnalyzer.Kind kind,Set<String> seen){
        if(!seen.add(owner+"."+name+desc)||seen.size()>64)return false;
        if(owner.equals("cpw/mods/fml/common/registry/GameRegistry"))return name.equals(kind==LegacyRegistryAnalyzer.Kind.BLOCK?"registerBlock":"registerItem");
        Method method=find(owner,List.of(name),desc);if(method==null)return false;
        for(AbstractInsnNode ins:method.node().instructions)if(ins instanceof MethodInsnNode c&&registrationSink(c.owner,c.name,c.desc,kind,seen))return true;
        return false;
    }
    private Method frames(ClassNode c,MethodNode m) {
        if(methods.containsKey(m)) return methods.get(m);
        try {
            Method method=new Method(c,m,new Analyzer<>(new SourceInterpreter()).analyze(c.name,m));
            methods.put(m,method); return method;
        } catch(AnalyzerException | RuntimeException bad) { methods.put(m,null); return null; }
    }
    private Object value(Method method,SourceValue source,int depth) {
        tick(depth);
        if(source==null||source.insns.size()!=1) throw fail("merged or external expression");
        AbstractInsnNode ins=source.insns.iterator().next();
        if(values.containsKey(ins)) return values.get(ins);
        int i=method.node().instructions.indexOf(ins); Frame<SourceValue> f=method.frames()[i];
        int op=ins.getOpcode();
        if(ins instanceof LdcInsnNode l) return l.cst;
        if(ins instanceof VarInsnNode v && op>=ILOAD&&op<=ALOAD) return value(method,f.getLocal(v.var),depth+1);
        if(ins instanceof VarInsnNode && op>=ISTORE&&op<=ASTORE) return value(method,f.getStack(f.getStackSize()-1),depth+1);
        if(op==DUP) return value(method,f.getStack(f.getStackSize()-1),depth+1);
        if(op==GETSTATIC && ins instanceof FieldInsnNode field) return readStatic(field.owner,field.name,depth+1);
        if(op==GETFIELD && ins instanceof FieldInsnNode field) return field(value(method,f.getStack(f.getStackSize()-1),depth+1),field.name);
        if(ins instanceof IntInsnNode n && (op==BIPUSH||op==SIPUSH)) return n.operand;
        if(op>=ICONST_M1&&op<=ICONST_5) return op-ICONST_0;
        if(op==ACONST_NULL) return null;
        if(op>=FCONST_0&&op<=FCONST_2) return (float)(op-FCONST_0);
        if(op>=DCONST_0&&op<=DCONST_1) return (double)(op-DCONST_0);
        if(op==NEW && ins instanceof TypeInsnNode n) {
            Obj obj=new Obj(n.desc); values.put(ins,obj);
            try {
                for(int j=i+1;j<method.node().instructions.size();j++) {
                    AbstractInsnNode next=method.node().instructions.get(j);
                    if(!(next instanceof MethodInsnNode init)||!init.name.equals("<init>")) continue;
                    Frame<SourceValue> ff=method.frames()[j]; if(ff==null)continue;
                    int start=ff.getStackSize()-Type.getArgumentTypes(init.desc).length-1;
                    if(start<0)continue;
                    if(value(method,ff.getStack(start),depth+1)!=obj)continue;
                    List<Object> args=new ArrayList<>();
                    for(int k=start+1;k<ff.getStackSize();k++) args.add(value(method,ff.getStack(k),depth+1));
                    invoke(init.owner,init.name,init.desc,obj,args,INVOKESPECIAL,depth+1); return obj;
                }
                throw fail("constructor not traced");
            } catch(RuntimeException ex) { values.remove(ins); throw ex; }
        }
        if(op==ANEWARRAY || op==NEWARRAY) {
            int size=num(value(method,f.getStack(f.getStackSize()-1),depth+1)).intValue();
            checkArray(size); Object[] array=new Object[size]; values.put(ins,array);
            for(int j=i+1;j<method.node().instructions.size();j++) {
                AbstractInsnNode next=method.node().instructions.get(j);
                if(next instanceof JumpInsnNode||next instanceof TableSwitchInsnNode||next instanceof LookupSwitchInsnNode)break;
                if(next instanceof MethodInsnNode)break;
                int store=next.getOpcode();
                if(store!=AASTORE&&store!=IASTORE)continue;
                Frame<SourceValue> ff=method.frames()[j]; if(ff==null||ff.getStackSize()<3)continue;
                try {
                    if(value(method,ff.getStack(ff.getStackSize()-3),depth+1)==array) array[num(value(method,ff.getStack(ff.getStackSize()-2),depth+1)).intValue()]=value(method,ff.getStack(ff.getStackSize()-1),depth+1);
                } catch(Unproven unrelated) { throw unrelated; }
            }
            return array;
        }
        if(ins instanceof MethodInsnNode call) {
            int count=Type.getArgumentTypes(call.desc).length,start=f.getStackSize()-count;
            List<Object> args=new ArrayList<>(); for(int k=start;k<f.getStackSize();k++)args.add(value(method,f.getStack(k),depth+1));
            Object receiver=op==INVOKESTATIC?null:value(method,f.getStack(start-1),depth+1);
            Object result=invoke(call.owner,call.name,call.desc,receiver,args,op,depth+1);
            values.put(ins,result); return result;
        }
        if(op==CHECKCAST) return value(method,f.getStack(f.getStackSize()-1),depth+1);
        if(op>=IADD&&op<=LXOR && f.getStackSize()>=2) return binary(op,value(method,f.getStack(f.getStackSize()-2),depth+1),value(method,f.getStack(f.getStackSize()-1),depth+1));
        throw fail("unsupported registration expression opcode "+op);
    }

    private Object run(Method method,Obj self,List<Object> args,int depth) {
        tick(depth); MethodNode m=method.node();
        if(!m.tryCatchBlocks.isEmpty())throw fail("exception-dependent presentation");
        Object[] locals=new Object[Math.max(m.maxLocals,32)]; Arrays.fill(locals,U);
        int slot=0;if((m.access&ACC_STATIC)==0)locals[slot++]=self;
        Type[] types=Type.getArgumentTypes(m.desc);
        for(int a=0;a<args.size();a++){locals[slot]=args.get(a);slot+=types[a].getSize();}
        List<Object> stack=new ArrayList<>();
        for(int pc=0;pc<m.instructions.size();pc++) {
            tick(depth); AbstractInsnNode ins=m.instructions.get(pc); int op=ins.getOpcode(); if(op<0)continue;
            if(ins instanceof LdcInsnNode l){stack.add(l.cst);continue;}
            if(ins instanceof VarInsnNode v){if(op>=ILOAD&&op<=ALOAD)stack.add(locals[v.var]);else if(op>=ISTORE&&op<=ASTORE)locals[v.var]=pop(stack);else throw fail("local opcode");continue;}
            if(ins instanceof IincInsnNode inc){locals[inc.var]=num(locals[inc.var]).intValue()+inc.incr;continue;}
            if(ins instanceof IntInsnNode n &&(op==BIPUSH||op==SIPUSH)){stack.add(n.operand);continue;}
            if(op>=ICONST_M1&&op<=ICONST_5){stack.add(op-ICONST_0);continue;}
            if(op>=FCONST_0&&op<=FCONST_2){stack.add((float)(op-FCONST_0));continue;}
            if(op>=DCONST_0&&op<=DCONST_1){stack.add((double)(op-DCONST_0));continue;}
            if(ins instanceof FieldInsnNode field){
                if(readOnly&&(op==PUTFIELD||op==PUTSTATIC))throw fail("stateful icon selector");
                switch(op){case GETSTATIC->stack.add(readStatic(field.owner,field.name,depth+1));case PUTSTATIC->statics.put(field.owner+"."+field.name,pop(stack));case GETFIELD->stack.add(field(pop(stack),field.name));case PUTFIELD->{Object val=pop(stack);Object obj=pop(stack);if(!(obj instanceof Obj o))throw fail("field receiver");o.fields.put(field.name,val);}default->throw fail("field opcode");}continue;
            }
            if(ins instanceof MethodInsnNode call){int count=Type.getArgumentTypes(call.desc).length;List<Object> actual=new ArrayList<>();for(int a=0;a<count;a++)actual.add(0,pop(stack));Object receiver=op==INVOKESTATIC?null:pop(stack);Object result=invoke(call.owner,call.name,call.desc,receiver,actual,op,depth+1);if(Type.getReturnType(call.desc).getSort()!=Type.VOID)stack.add(result);continue;}
            if(ins instanceof JumpInsnNode j){boolean jump;
                if(op==GOTO)jump=true;
                else if(op==IFNULL||op==IFNONNULL){Object a=pop(stack);if(a==U)throw fail("unknown null branch");jump=(a==null)==(op==IFNULL);}
                else if(op>=IF_ICMPEQ&&op<=IF_ACMPNE){Object b=pop(stack),a=pop(stack);if(a==U||b==U)throw fail("unknown branch");jump=switch(op){case IF_ICMPEQ->num(a).intValue()==num(b).intValue();case IF_ICMPNE->num(a).intValue()!=num(b).intValue();case IF_ICMPLT->num(a).intValue()<num(b).intValue();case IF_ICMPGE->num(a).intValue()>=num(b).intValue();case IF_ICMPGT->num(a).intValue()>num(b).intValue();case IF_ICMPLE->num(a).intValue()<=num(b).intValue();case IF_ACMPEQ->a==b;case IF_ACMPNE->a!=b;default->false;};}
                else {int a=num(pop(stack)).intValue();jump=switch(op){case IFEQ->a==0;case IFNE->a!=0;case IFLT->a<0;case IFGE->a>=0;case IFGT->a>0;case IFLE->a<=0;default->throw fail("jump opcode");};}
                if(jump)pc=m.instructions.indexOf(j.label);continue;
            }
            if(ins instanceof TableSwitchInsnNode sw){int key=num(pop(stack)).intValue();pc=m.instructions.indexOf(key>=sw.min&&key<=sw.max?sw.labels.get(key-sw.min):sw.dflt);continue;}
            if(ins instanceof LookupSwitchInsnNode sw){int idx=sw.keys.indexOf(num(pop(stack)).intValue());pc=m.instructions.indexOf(idx<0?sw.dflt:sw.labels.get(idx));continue;}
            if(op>=IADD&&op<=LXOR &&op!=INEG&&op!=FNEG&&op!=DNEG&&op!=LNEG){Object b=pop(stack),a=pop(stack);stack.add(binary(op,a,b));continue;}
            switch(op){
                case NOP->{} case ACONST_NULL->stack.add(null);
                case POP->pop(stack);case DUP->stack.add(stack.getLast());case DUP_X1->{Object a=pop(stack),b=pop(stack);stack.add(a);stack.add(b);stack.add(a);}case SWAP->{Object a=pop(stack),b=pop(stack);stack.add(a);stack.add(b);}
                case NEW->stack.add(new Obj(((TypeInsnNode)ins).desc));
                case ANEWARRAY,NEWARRAY->{int size=num(pop(stack)).intValue();checkArray(size);stack.add(new Object[size]);}
                case ARRAYLENGTH->{Object a=pop(stack);if(!(a instanceof Object[] array))throw fail("array length");stack.add(array.length);}
                case AALOAD,IALOAD,FALOAD,DALOAD,BALOAD,SALOAD,CALOAD->{int at=num(pop(stack)).intValue();Object a=pop(stack);if(!(a instanceof Object[] array))throw fail("array read");Object v=array[at];stack.add(v==null&&op!=AALOAD?0:v);}
                case AASTORE,IASTORE,FASTORE,DASTORE,BASTORE,SASTORE,CASTORE->{if(readOnly)throw fail("stateful icon selector");Object val=pop(stack);int at=num(pop(stack)).intValue();Object a=pop(stack);if(!(a instanceof Object[] array))throw fail("array write");array[at]=val;}
                case CHECKCAST->{} case INSTANCEOF->{Object a=pop(stack);if(a!=null)throw fail("instanceof hierarchy not proven");stack.add(0);}
                case I2F,D2F->stack.add(num(pop(stack)).floatValue());case I2D,F2D->stack.add(num(pop(stack)).doubleValue());case F2I,D2I->stack.add(num(pop(stack)).intValue());case I2B->stack.add((int)num(pop(stack)).byteValue());case I2S->stack.add((int)num(pop(stack)).shortValue());case I2C->stack.add((int)(char)num(pop(stack)).intValue());
                case INEG->stack.add(-num(pop(stack)).intValue());case FNEG->stack.add(-num(pop(stack)).floatValue());case DNEG->stack.add(-num(pop(stack)).doubleValue());
                case FCMPL,FCMPG,DCMPL,DCMPG->{double b=num(pop(stack)).doubleValue(),a=num(pop(stack)).doubleValue();stack.add(Double.isNaN(a)||Double.isNaN(b)?(op==FCMPL||op==DCMPL?-1:1):a==b?0:a<b?-1:1);}
                case IRETURN,FRETURN,DRETURN,ARETURN->{return pop(stack);}case RETURN->{return null;}
                default->throw fail("unsupported presentation opcode "+op+" in "+method.owner().name+"."+m.name);
            }
        }
        throw fail("method has no completed return");
    }

    private Object invoke(String owner,String name,String desc,Object receiver,List<Object> args,int opcode,int depth) {
        tick(depth);
        if(owner.equals("net/minecraft/client/renderer/texture/IIconRegister") && Set.of("registerIcon","func_94245_a").contains(name)) {
            if(args.size()!=1||!(args.getFirst() instanceof String s)||s.isBlank())throw fail("nonconstant icon name");return new Icon(s);
        }
        if(receiver instanceof Symbol s && s.owner().equals("lfb-world")) {
            if(Set.of("getBlockMetadata","func_72805_g").contains(name))return Integer.parseInt(s.name());
            throw fail("world-dependent presentation: "+name);
        }
        if(receiver instanceof Obj object) {
            if(name.equals("<init>")&&owner.equals("java/lang/Object"))return null;
            if(owner.equals("java/lang/StringBuilder")){
                if(name.equals("<init>")){object.fields.put("$text",args.isEmpty()?"":text(args.getFirst()));return null;}
                if(name.equals("append")){object.fields.put("$text",text(object.fields.getOrDefault("$text",""))+text(args.getFirst()));return object;}
                if(name.equals("toString"))return object.fields.getOrDefault("$text","");
            }
            if(owner.equals("java/util/ArrayList")||owner.equals("java/util/LinkedList")||owner.equals("java/util/List")) {
                if(name.equals("<init>")){object.fields.put("$list",new ArrayList<>());return null;}
                @SuppressWarnings("unchecked") List<Object> list=(List<Object>)object.fields.get("$list");
                if(list==null)throw fail("list not initialized");
                return switch(name){case "add"->{if(readOnly)throw fail("stateful icon selector");list.add(args.getFirst());yield 1;}case "get"->list.get(num(args.getFirst()).intValue());case "size"->list.size();case "toArray"->list.toArray();default->throw fail("unsupported list operation "+name);};
            }
            Method target=name.equals("<init>")||opcode==INVOKESPECIAL?find(owner,List.of(name),desc):find(object.type,List.of(name),desc);
            if(target!=null)return run(target,object,args,depth+1);
            String platform=externalBase(owner);
            if(platform.startsWith("net/minecraft/block/")||platform.startsWith("net/minecraft/item/")) {
                if(name.equals("<init>")) {
                    // Constructors of unknown vanilla subclasses may have visual semantics. A
                    // static icon can still be proved, but block geometry uses baseRenderType below.
                    object.fields.putIfAbsent("$bounds",List.of(0d,0d,0d,1d,1d,1d));return null;
                }
                if(Set.of("setTextureName","func_111206_d","setBlockTextureName","func_149658_d").contains(name)) {object.fields.put("$texture",args.getFirst());return object;}
                if(Set.of("getIconString","func_111208_A","getTextureName","func_149641_N").contains(name))return object.fields.getOrDefault("$texture",U);
                if(Set.of("setBlockBounds","func_149676_a").contains(name)){object.fields.put("$bounds",new ArrayList<>(args));return null;}
                if(Set.of("getIconFromDamage","func_77617_a","getIcon","func_149691_a").contains(name))return object.fields.getOrDefault(platform.startsWith("net/minecraft/block/")?"blockIcon":"itemIcon",U);
                if(Set.of("setUnlocalizedName","func_77655_b","setBlockName","func_149663_c","setHardness","func_149711_c","setResistance","func_149752_b","setCreativeTab","func_77637_a","func_149647_a","setMaxStackSize","func_77625_d","setMaxDamage","func_77656_e","setHasSubtypes","func_77627_a","setAlwaysEdible","func_77848_i","setStepSound","func_149672_a","setLightLevel","func_149715_a","setLightOpacity","func_149713_g","setTickRandomly","func_149675_a").contains(name))return Type.getReturnType(desc).getSort()==Type.VOID?null:object;
                if(Set.of("registerIcons","func_94581_a","registerBlockIcons","func_149651_a").contains(name)){
                    Object texture=object.fields.get("$texture");if(!(texture instanceof String s))throw fail("base icon has no texture string");Icon icon=new Icon(s);object.fields.put(platform.startsWith("net/minecraft/block/")?"blockIcon":"itemIcon",icon);object.fields.put(platform.startsWith("net/minecraft/block/")?"field_149761_L":"field_77791_bV",icon);return null;
                }
            }
        }
        if(receiver instanceof String s) return switch(name){case "toLowerCase"->s.toLowerCase(Locale.ROOT);case "length"->s.length();case "concat"->s+text(args.getFirst());case "equals"->s.equals(args.getFirst())?1:0;case "substring"->args.size()==1?s.substring(num(args.getFirst()).intValue()):s.substring(num(args.get(0)).intValue(),num(args.get(1)).intValue());default->throw fail("unsupported String operation "+name);};
        if(opcode==INVOKESTATIC){
            if(owner.equals("java/lang/String")&&name.equals("valueOf"))return text(args.getFirst());
            if(owner.equals("java/lang/Math"))return switch(name){case "min"->Math.min(num(args.get(0)).intValue(),num(args.get(1)).intValue());case "max"->Math.max(num(args.get(0)).intValue(),num(args.get(1)).intValue());default->throw fail("unsupported Math operation");};
            Method target=find(owner,List.of(name),desc);if(target!=null)return run(target,null,args,depth+1);
        }
        throw fail("unsupported call "+owner+"."+name+desc);
    }
    private Object readStatic(String owner,String name,int depth) {
        String key=owner+"."+name;
        if(failedStatics.contains(owner))throw fail("unproven class initializer "+owner);
        if(statics.containsKey(key))return statics.get(key);
        ClassNode c=classes.get(owner);
        if(c==null){Object symbol=new Symbol(owner,name);statics.put(key,symbol);return symbol;}
        for(FieldNode f:c.fields)if(f.name.equals(name)&&f.value!=null){statics.put(key,f.value);return f.value;}
        if(initialized.add(owner)){
            Method cl=find(owner,List.of("<clinit>"),"()V");
            if(cl!=null){boolean prior=readOnly;readOnly=false;try{run(cl,null,List.of(),depth+1);}catch(RuntimeException ex){failedStatics.add(owner);statics.keySet().removeIf(k->k.startsWith(owner+"."));throw ex;}finally{readOnly=prior;}}
        }
        return statics.getOrDefault(key,U);
    }
    private Method find(String owner,List<String> names,String desc) {
        Set<String> visited=new HashSet<>();
        while(owner!=null&&visited.add(owner)){ClassNode c=classes.get(owner);if(c==null)return null;for(MethodNode m:c.methods)if(names.contains(m.name)&&m.desc.equals(desc))return new Method(c,m,null);owner=c.superName;}return null;
    }
    private boolean subclass(String owner,String target){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){if(owner.equals(target))return true;ClassNode c=classes.get(owner);owner=c==null?null:c.superName;}return false;}
    private String externalBase(String owner){Set<String> seen=new HashSet<>();while(owner!=null&&classes.containsKey(owner)&&seen.add(owner))owner=classes.get(owner).superName;return owner==null?"":owner;}
    private int baseRenderType(String owner){Set<String> visited=new HashSet<>();while(classes.containsKey(owner)&&visited.add(owner))owner=classes.get(owner).superName;if("net/minecraft/block/Block".equals(owner))return 0;if("net/minecraft/block/BlockBush".equals(owner))return 1;throw fail("vanilla superclass rendering not reconstructed: "+owner);}
    private static Object field(Object obj,String name){if(!(obj instanceof Obj o))throw fail("external instance field "+name);return o.fields.getOrDefault(name,U);}
    private static Obj copy(Obj obj){Obj c=new Obj(obj.type);for(var e:obj.fields.entrySet()){Object v=e.getValue();c.fields.put(e.getKey(),v instanceof Object[] a?a.clone():v instanceof List<?> l?new ArrayList<>(l):v);}return c;}
    private static Object pop(List<Object> stack){if(stack.isEmpty())throw fail("operand stack underflow");return stack.removeLast();}
    private static Number num(Object value){if(value instanceof Number n)return n;throw fail("nonconstant numeric value");}
    private static String text(Object value){if(value instanceof String||value instanceof Number||value==null)return String.valueOf(value);throw fail("nonconstant string value");}
    private static void checkArray(int size){if(size<0||size>4096)throw fail("array allocation outside bounded presentation limit");}
    private void tick(int depth){if(depth>32||--budget<0)throw fail("bounded presentation analysis limit");}
    private static Unproven fail(String why){return new Unproven(why);}
    private static Object binary(int op,Object av,Object bv){Number a=num(av),b=num(bv);return switch(op){
        case IADD->a.intValue()+b.intValue();case ISUB->a.intValue()-b.intValue();case IMUL->a.intValue()*b.intValue();case IDIV->a.intValue()/b.intValue();case IREM->a.intValue()%b.intValue();case IAND->a.intValue()&b.intValue();case IOR->a.intValue()|b.intValue();case IXOR->a.intValue()^b.intValue();case ISHL->a.intValue()<<b.intValue();case ISHR->a.intValue()>>b.intValue();case IUSHR->a.intValue()>>>b.intValue();
        case FADD->a.floatValue()+b.floatValue();case FSUB->a.floatValue()-b.floatValue();case FMUL->a.floatValue()*b.floatValue();case FDIV->a.floatValue()/b.floatValue();case FREM->a.floatValue()%b.floatValue();case DADD->a.doubleValue()+b.doubleValue();case DSUB->a.doubleValue()-b.doubleValue();case DMUL->a.doubleValue()*b.doubleValue();case DDIV->a.doubleValue()/b.doubleValue();case DREM->a.doubleValue()%b.doubleValue();default->throw fail("unsupported arithmetic opcode "+op);};}
}
