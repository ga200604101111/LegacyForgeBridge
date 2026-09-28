package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.ClientFeedbackProgram;
import dev.yinghuang.legacyforgebridge.compat.ClientFeedbackProgram.*;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Reads callback instructions, including closed helpers, into a gesture-only projection.
 * Unknown events/world effects are explicit DEFER boundaries; they are never assumed successful. */
public final class ClientFeedbackCompiler implements Opcodes {
    public static final String PATH="legacyforgebridge/source-client-feedback.json";
    private static final String STACK="Lnet/minecraft/item/ItemStack;",PLAYER="Lnet/minecraft/entity/player/EntityPlayer;",WORLD="Lnet/minecraft/world/World;",LIVING="Lnet/minecraft/entity/EntityLivingBase;";
    private final Map<String,ClassNode> classes=new TreeMap<>();
    private record Source(String owner,MethodNode method) {String key(){return owner+"."+method.name+method.desc;}}
    private final Map<String,Code> codes=new LinkedHashMap<>();private String concrete;
    public ClientFeedbackCompiler(Path source)throws IOException{
        try(var jar=new JarFile(source.toFile())){for(var e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")){
            if(classes.size()>=20000||e.getSize()>4_194_304)throw new IOException("source budget");var c=new ClassNode(ASM9);new ClassReader(jar.getInputStream(e)).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);if(classes.put(c.name,c)!=null)throw new IOException("duplicate source class");
        }}
    }
    private Source resolve(String cls,String name,String desc){Set<String> seen=new HashSet<>();while(cls!=null&&classes.containsKey(cls)&&seen.add(cls)&&seen.size()<128){var c=classes.get(cls);for(var m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return new Source(cls,m);cls=c.superName;}return null;}
    /** Only virtual calls on this source item use the concrete override; invokespecial stays exact. */
    private Source dispatch(MethodInsnNode call){
        String owner=call.owner;
        if(call.getOpcode()==INVOKEVIRTUAL){Set<String> seen=new HashSet<>();String c=concrete;while(c!=null&&seen.add(c)&&seen.size()<128){
            if(c.equals(owner)){owner=concrete;break;}ClassNode node=classes.get(c);if(node==null)break;c=node.superName;
        }}
        return resolve(owner,call.name,call.desc);
    }
    public Map<String,Program> compile(String sourceClass){
        concrete=sourceClass;Map<String,Program> output=new LinkedHashMap<>();
        for(String kind:List.of("release","entity")){
            String name=kind.equals("release")?"func_77615_a":"func_111207_a",desc=kind.equals("release")?"("+STACK+WORLD+PLAYER+"I)V":"("+STACK+PLAYER+LIVING+")Z";
            var root=resolve(sourceClass,name,desc);if(root==null)continue;
            if(!hasSwing(root,new HashSet<>(),0))continue;
            codes.clear();try{compile(root);output.put(kind,new Program(kind,root.key(),new LinkedHashMap<>(codes)));}catch(IllegalArgumentException bad){/* caller reports missing projection; no guessed gesture */}
        }return output;
    }
    /** Dormant source projection. Admission through PlayerUseItemEvent.Tick is a separate,
     * currently unavailable runtime boundary; never put this result in active programs.
     */
    public Optional<Program> compileUsingTick(String sourceClass){
        concrete=sourceClass;
        Source root=resolve(sourceClass,"onUsingTick","("+STACK+PLAYER+"I)V");
        if(root==null||(root.method.access&ACC_PUBLIC)==0
                ||(root.method.access&(ACC_STATIC|ACC_ABSTRACT|ACC_NATIVE))!=0
                ||!hasSwing(root,new HashSet<>(),0))return Optional.empty();
        codes.clear();
        try{compile(root);return Optional.of(new Program("usingTick",root.key(),new LinkedHashMap<>(codes)));}
        catch(IllegalArgumentException unsupported){return Optional.empty();}
    }
    private boolean hasSwing(Source source,Set<String> seen,int depth){if(depth>16||!seen.add(source.key()))return false;for(var i:source.method.instructions)if(i instanceof MethodInsnNode m){if(swing(m))return true;var helper=dispatch(m);if(helper!=null&&hasSwing(helper,seen,depth+1))return true;}return false;}
    private void compile(Source source){
        if(codes.containsKey(source.key()))return;if(codes.size()>=64)throw new IllegalArgumentException("feedback helper budget");MethodNode m=source.method;
        if(m.tryCatchBlocks!=null&&!m.tryCatchBlocks.isEmpty())throw new IllegalArgumentException("exception-dependent gesture");
        Type[] args=Type.getArgumentTypes(m.desc);int[] slots=new int[args.length];int n=(m.access&ACC_STATIC)!=0?0:1;for(int j=0;j<args.length;j++){slots[j]=n;n+=args[j].getSize();}
        List<AbstractInsnNode> raw=new ArrayList<>();for(var i:m.instructions)if(i.getOpcode()>=0)raw.add(i);if(raw.size()>8192)throw new IllegalArgumentException("method budget");
        // Placeholder breaks compile-time recursion; runtime recursion still has a strict budget.
        codes.put(source.key(),new Code(Math.max(m.maxLocals,n),slots,(m.access&ACC_STATIC)!=0,List.of(new Step("DEFER",0,"recursive compilation"))));
        List<Step> out=new ArrayList<>();for(var insn:raw){int op=insn.getOpcode();Step step;
            if(insn instanceof VarInsnNode v){step=new Step(op>=ILOAD&&op<=ALOAD?"LOAD":op>=ISTORE&&op<=ASTORE?"STORE":"DEFER",v.var,null);}
            else if(insn instanceof IincInsnNode i)step=new Step("IINC",i.var,""+i.incr);
            else if(insn instanceof IntInsnNode i&&op!=NEWARRAY)step=new Step("INT",i.operand,null);
            else if(insn instanceof LdcInsnNode l){step=l.cst instanceof Integer x?new Step("INT",x,null):l.cst instanceof Float x?new Step("FLOAT",0,x.toString()):l.cst instanceof Double x?new Step("DOUBLE",0,x.toString()):new Step("DEFER",0,"non-numeric source constant");}
            else if(insn instanceof JumpInsnNode j){var target=j.label.getNext();while(target!=null&&target.getOpcode()<0)target=target.getNext();int index=raw.indexOf(target);if(index<0)throw new IllegalArgumentException("invalid jump");step=new Step(jump(op),index,null);}
            else if(insn instanceof FieldInsnNode f){
                if(op==GETFIELD&&f.name.equals("field_70170_p")&&f.desc.equals(WORLD)&&!shadows(f))step=new Step("WORLD",0,null);
                else if(op==GETFIELD&&f.name.equals("field_72995_K")&&f.desc.equals("Z")&&!shadows(f))step=new Step("REMOTE",0,null);
                else step=new Step("DEFER",0,"source state field "+f.owner+"."+f.name);
            }else if(insn instanceof MethodInsnNode call){
                // A source body takes precedence over a scalar shortcut. In particular,
                // invokespecial must keep the named parent's duration/maximum semantics,
                // and unknown code in an override must still reach a DEFER boundary.
                Source helper=dispatch(call);
                if(helper!=null&&!call.name.equals("<init>")){compile(helper);step=new Step("CALL",Type.getReturnType(call.desc).getSort()==Type.VOID?0:1,helper.key());}
                else if(swing(call))step=new Step("SWING",0,null);
                // The exact Forge 1.7.10 Item implementation is empty. Source-visible
                // overrides were resolved above; no unknown or virtual parent is bypassed.
                else if(call.getOpcode()==INVOKESPECIAL&&call.owner.equals("net/minecraft/item/Item")
                        &&call.name.equals("onUsingTick")&&call.desc.equals("("+STACK+PLAYER+"I)V"))step=new Step("BASE_USE_TICK",0,null);
                else if(call.name.equals("func_77626_a")&&call.desc.equals("("+STACK+")I"))step=new Step("DURATION",0,null);
                else if(call.owner.equals("net/minecraft/item/ItemStack")&&call.name.equals("func_77952_i")&&call.desc.equals("()I"))step=new Step("DAMAGE",0,null);
                else if(call.name.equals("func_77612_l")&&call.desc.equals("()I"))step=new Step("MAX",0,null);
                else if(call.owner.equals("net/minecraft/entity/EntityLivingBase")&&call.name.equals("func_71124_b")&&call.desc.equals("(I)"+STACK))step=new Step("EQUIPPED",0,null);
                else if(call.owner.equals("net/minecraft/item/ItemStack")&&call.name.equals("func_77972_a")&&call.desc.equals("(I"+LIVING+")V"))step=new Step("SERVER_RESOURCE",3,"durability remains server-owned");
                else if(call.owner.equals("net/minecraft/entity/player/EntityPlayer")&&call.name.equals("func_71020_j")&&call.desc.equals("(F)V"))step=new Step("SERVER_RESOURCE",2,"exhaustion remains server-owned");
                else step=new Step("DEFER",0,"unprojected invocation "+call.owner+"."+call.name+call.desc);
            }else step=simple(op);
            out.add(step);
        }
        codes.put(source.key(),new Code(Math.max(m.maxLocals,n),slots,(m.access&ACC_STATIC)!=0,out));
    }
    private boolean shadows(FieldInsnNode f){var s=classes.get(f.owner);if(s==null)return false;return s.fields.stream().anyMatch(x->x.name.equals(f.name)&&x.desc.equals(f.desc));}
    private static boolean swing(MethodInsnNode m){return Set.of("net/minecraft/entity/player/EntityPlayer","net/minecraft/entity/EntityLivingBase","net/minecraft/client/entity/EntityClientPlayerMP").contains(m.owner)&&m.name.equals("func_71038_i")&&m.desc.equals("()V");}
    private static String jump(int op){return switch(op){case GOTO->"GOTO";case IFEQ->"IFEQ";case IFNE->"IFNE";case IFLT->"IFLT";case IFLE->"IFLE";case IFGT->"IFGT";case IFGE->"IFGE";case IF_ICMPEQ->"IF_ICMPEQ";case IF_ICMPNE->"IF_ICMPNE";case IF_ICMPLT->"IF_ICMPLT";case IF_ICMPLE->"IF_ICMPLE";case IF_ICMPGT->"IF_ICMPGT";case IF_ICMPGE->"IF_ICMPGE";case IFNULL->"IFNULL";case IFNONNULL->"IFNONNULL";default->"DEFER";};}
    private static Step simple(int op){
        if(op>=ICONST_M1&&op<=ICONST_5)return new Step("INT",op-ICONST_0,null);if(op>=FCONST_0&&op<=FCONST_2)return new Step("FLOAT",0,""+(float)(op-FCONST_0));if(op>=DCONST_0&&op<=DCONST_1)return new Step("DOUBLE",0,""+(double)(op-DCONST_0));
        String name=switch(op){case NOP->"NOP";case ACONST_NULL->"NULL";case POP->"POP";case DUP->"DUP";case IADD->"IADD";case ISUB->"ISUB";case IMUL->"IMUL";case IDIV->"IDIV";case IREM->"IREM";case INEG->"INEG";case FADD->"FADD";case FSUB->"FSUB";case FMUL->"FMUL";case FDIV->"FDIV";case FREM->"FREM";case FNEG->"FNEG";case DADD->"DADD";case DSUB->"DSUB";case DMUL->"DMUL";case DDIV->"DDIV";case DREM->"DREM";case DNEG->"DNEG";case I2F->"I2F";case I2D->"I2D";case F2D->"F2D";case F2I->"F2I";case D2F->"D2F";case D2I->"D2I";case FCMPL->"FCMPL";case FCMPG->"FCMPG";case DCMPL->"DCMPL";case DCMPG->"DCMPG";case RETURN->"RETURN";case IRETURN->"IRETURN";case FRETURN->"FRETURN";case DRETURN->"DRETURN";case ARETURN->"ARETURN";default->"DEFER";};return new Step(name,0,name.equals("DEFER")?"unprojected opcode "+op:null);
    }
    public static void apply(ConversionContext context)throws IOException{
        Path interaction=context.stagingDir().resolve("legacyforgebridge/source-interactions.json");if(!Files.isRegularFile(interaction))return;
        JsonObject root=JsonParser.parseString(Files.readString(interaction)).getAsJsonObject();JsonObject output=new JsonObject(),items=new JsonObject();output.addProperty("schema",1);output.addProperty("sourceSha256",context.sourceHash());output.add("items",items);
        JsonObject pendingTicks=new JsonObject();output.add("pendingUsingTick",pendingTicks);
        var compiler=new ClientFeedbackCompiler(context.sourceJar());Map<String,Map<String,Program>> cache=new HashMap<>();
        Map<String,Optional<Program>> tickCache=new HashMap<>();
        for(var e:root.getAsJsonObject("items").entrySet()){
            String cls=e.getValue().getAsJsonObject().get("sourceClass").getAsString();var programs=cache.computeIfAbsent(cls,compiler::compile);
            var tick=tickCache.computeIfAbsent(cls,compiler::compileUsingTick);
            if(tick.isPresent()){
                JsonObject pending=new JsonObject();pending.addProperty("sourceClass",cls);
                pending.addProperty("nativeRuntimeEnabled",false);
                pending.addProperty("status","REQUIRES_UPSTREAM_TICK_EVENT_BRIDGE");
                pending.add("program",ClientFeedbackProgram.encode(tick.get()));pendingTicks.add(e.getKey(),pending);
            }
            if(programs.isEmpty())continue;
            JsonObject row=new JsonObject();row.addProperty("sourceClass",cls);JsonObject ps=new JsonObject();programs.forEach((kind,p)->ps.add(kind,ClientFeedbackProgram.encode(p)));row.add("programs",ps);row.addProperty("serverAuthorityUnchanged",true);row.addProperty("scope","source gesture projection; DEFER boundaries do not certify whole callback");items.add(e.getKey(),row);
        }
        Path out=context.stagingDir().resolve(PATH);Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(output)+"\n",StandardCharsets.UTF_8);
    }
}
