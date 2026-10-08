package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
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
 * SOURCE ONLY: prove paired 1.7.10 TileEntity instance-field <-> NBTTagCompound primitive
 * entries in effective writeToNBT/readFromNBT paths. NBT persistence IS NOT a network payload:
 * neither getDescriptionPacket nor onDataPacket existence admits a client-side sync bridge.
 * Does not load legacy classes, execute tick logic, or register a modern BlockEntity.
 */
public final class LegacyTileNbtPersistenceAnalyzer {
    private static final String TILE = "net/minecraft/tileentity/TileEntity";
    private static final String NBT = "net/minecraft/nbt/NBTTagCompound";
    private static final String PACKET = "net/minecraft/network/Packet";
    private static final String S35 = "net/minecraft/network/play/server/S35PacketUpdateTileEntity";
    private static final String NETWORK = "net/minecraft/network/NetworkManager";
    private static final String NBT_DESC = "(L" + NBT + ";)V";
    private static final int MAX_CLASSES = 65_536, MAX_SOURCE_DEPTH = 16, MAX_METHOD_INSNS = 2048;
    private static final int MAX_FIELDS = 64;

    public enum Status { PAIRED_NBT_ONLY, NO_NBT_PAIR, SOURCE_NBT_UNPROVABLE }
    public record StoredField(String owner, String name, String descriptor, String key,
                              String primitiveKind) {
        public StoredField {
            if (owner==null || owner.isBlank() || name==null || name.isBlank()
                    || descriptor==null || descriptor.isBlank() || key==null || key.isBlank()
                    || primitiveKind==null || primitiveKind.isBlank())
                throw new IllegalArgumentException("Unbounded source NBT field proof");
        }
    }
    public record Audit(String sourceTileClass, List<String> requiredVisualFields,
                        List<StoredField> pairedNbtFields, List<String> unpairedVisualFields,
                        boolean descriptionPacketHookObserved, boolean dataPacketHookObserved,
                        boolean networkPayloadProven, boolean clientRuntimeWired,
                        Status status) {
        public Audit {
            requiredVisualFields=List.copyOf(requiredVisualFields);
            pairedNbtFields=List.copyOf(pairedNbtFields);
            unpairedVisualFields=List.copyOf(unpairedVisualFields);
            Objects.requireNonNull(status);
            if (networkPayloadProven || clientRuntimeWired)
                throw new IllegalArgumentException("NBT persistence cannot grant network/runtime readiness");
        }
    }
    public record Analysis(Optional<Audit> audit, List<String> diagnostics) {
        public Analysis { audit=Objects.requireNonNull(audit); diagnostics=List.copyOf(diagnostics); }
    }
    private record Ref(String owner, String name, String desc) { }
    private record FieldKey(String owner, String name, String descriptor) { }
    private record Access(String key, String kind) { }
    private record MethodRef(ClassNode owner, MethodNode method) { }
    private record Context(Frame<SourceValue>[] frames, Map<AbstractInsnNode,Integer> positions) {
        Frame<SourceValue> at(AbstractInsnNode insn) {
            Integer i=positions.get(insn);return i==null?null:frames[i];
        }
    }
    private static final Map<String,String[]> ACCESS = Map.of(
            "I", new String[]{"setInteger", "func_74768_a", "getInteger", "func_74762_e"},
            "F", new String[]{"setFloat", "func_74776_a", "getFloat", "func_74760_g"},
            "J", new String[]{"setLong", "func_74772_a", "getLong", "func_74763_f"},
            "Z", new String[]{"setBoolean", "func_74757_a", "getBoolean", "func_74767_n"});

    /** Candidate and required field identities come from earlier source-proven render evidence. */
    public Analysis analyze(Path sourceJar, LegacyBlockTileModelPreflight.Candidate candidate,
                            Collection<String> requiredFields) throws IOException {
        Objects.requireNonNull(candidate);
        return analyze(sourceJar,candidate.tileClass(),requiredFields);
    }

    /** Explicit fixture seam: names are identities to verify, never mod-specific selectors. */
    public Analysis analyze(Path sourceJar, String tileClass, Collection<String> requiredFields)
            throws IOException {
        Objects.requireNonNull(sourceJar);Objects.requireNonNull(requiredFields);
        if (!validInternal(tileClass) || requiredFields.size()>MAX_FIELDS)
            return reject("Missing/unbounded TileEntity identity or visual field count");
        TreeSet<String> names=new TreeSet<>();
        for (String field:requiredFields) {
            if(field==null || field.isBlank() || field.length()>128 || !field.matches("[A-Za-z_$][A-Za-z0-9_$]*"))
                return reject("Unsafe or missing source visual field name");
            if(!names.add(field))return reject("Duplicate visual field identity");
        }
        if(names.isEmpty())return reject("No source renderer/animator field dependency to audit");
        Map<String,ClassNode> classes=load(sourceJar,tileClass);
        ClassNode tile=classes.get(tileClass);
        if(tile==null || !descends(classes,tileClass,TILE))return reject("Missing source TileEntity ancestry");
        Map<String,FieldKey> resolved=new TreeMap<>();
        for(String name:names){
            List<FieldKey> owners=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(String cur=tileClass;cur!=null&&seen.add(cur);){
                ClassNode node=classes.get(cur);
                if(node==null)break;
                for(FieldNode field:node.fields)if(field.name.equals(name)){
                    if((field.access & Opcodes.ACC_STATIC)!=0 || !ACCESS.containsKey(field.desc))
                        return reject("Visual field has unsupported/static source type: "+name);
                    owners.add(new FieldKey(node.name,field.name,field.desc));
                }
                cur=node.superName;
            }
            if(owners.size()!=1)return reject("Visual field is missing or shadowed across TileEntity hierarchy: "+name);
            resolved.put(name,owners.getFirst());
        }
        // An overridden TileEntity method need not call super: follow ONLY actual, exact
        // source-owned invokespecial edges, never infer that ancestral methods ran.
        List<MethodRef> writes=methodClosure(classes,tileClass,"writeToNBT","func_145841_b");
        List<MethodRef> reads=methodClosure(classes,tileClass,"readFromNBT","func_145839_a");
        Map<FieldKey,Access> outgoing=new HashMap<>(), incoming=new HashMap<>();
        boolean writeOkay=scanWrite(classes,writes,outgoing),readOkay=scanRead(classes,reads,incoming);
        List<StoredField> pairs=new ArrayList<>();List<String> missing=new ArrayList<>();
        for(String name:names){
            FieldKey key=resolved.get(name);
            Access out=outgoing.get(key), in=incoming.get(key);
            if(writeOkay && readOkay && out!=null && in!=null
                    && out.key().equals(in.key()) && out.kind().equals(in.kind()))
                pairs.add(new StoredField(key.owner,key.name,key.descriptor,out.key,out.kind));
            else missing.add(name);
        }
        boolean packetSend=declaredHook(classes,tileClass,Set.of("getDescriptionPacket","func_145844_m"),
                "()L"+PACKET+";");
        boolean packetReceive=declaredHook(classes,tileClass,Set.of("onDataPacket","func_145836_u"),
                "(L"+NETWORK+";L"+S35+";)V");
        Status status=!writeOkay || !readOkay ? Status.SOURCE_NBT_UNPROVABLE
                : pairs.isEmpty() ? Status.NO_NBT_PAIR : Status.PAIRED_NBT_ONLY;
        Audit audit=new Audit(tileClass,List.copyOf(names),pairs,missing,
                packetSend,packetReceive,false,false,status);
        List<String> details=new ArrayList<>();
        if(!writeOkay||!readOkay)details.add("Source NBT method body or inheritance call flow unsupported");
        if(!missing.isEmpty())details.add("NBT read+write unproven for "+missing);
        if(packetSend||packetReceive)details.add("Packet hooks observed, but NBT transport/payload provenance NOT proven");
        return new Analysis(Optional.of(audit),details);
    }

    private static boolean validInternal(String s) {
        return s!=null && s.length()<=255 && s.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(/[a-zA-Z_$][a-zA-Z0-9_$]*)+");
    }
    private static Analysis reject(String s){return new Analysis(Optional.empty(),List.of(s));}
    private static boolean descends(Map<String,ClassNode> classes,String child,String parent){
        Set<String> seen=new HashSet<>();for(String cur=child;cur!=null&&seen.add(cur);){
            if(cur.equals(parent))return true;
            ClassNode node=classes.get(cur);cur=node==null?null:node.superName;
        }return false;
    }
    /** Load only the requested TileEntity and its source-owned ancestors, not every JAR class. */
    private static Map<String,ClassNode> load(Path file,String tile)throws IOException {
        Map<String,ClassNode> result=new HashMap<>();
        try(JarFile source=new JarFile(file.toFile(),false)){
            if(source.size()>MAX_CLASSES)throw new IOException("Unbounded source JAR");
            // Fail closed on duplicate archive entry identities before reading any source class.
            Set<String> entries=new HashSet<>();
            var all=source.entries();
            while(all.hasMoreElements()){
                JarEntry entry=all.nextElement();
                if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;
                if(!entries.add(entry.getName()))throw new IOException("Duplicate source class entry "+entry.getName());
            }
            Set<String> visited=new HashSet<>();
            String cursor=tile;
            while(cursor!=null && !cursor.equals(TILE) && visited.add(cursor)){
                JarEntry entry=source.getJarEntry(cursor+".class");
                if(entry==null)break;
                try(InputStream stream=source.getInputStream(entry)){
                    ClassNode node=new ClassNode(Opcodes.ASM9);
                    try{new ClassReader(stream).accept(node,ClassReader.SKIP_FRAMES|ClassReader.SKIP_DEBUG);}
                    catch(RuntimeException invalid){break;}
                    if(!node.name.equals(cursor))throw new IOException("Source class archive identity mismatch: "+cursor);
                    result.put(cursor,node);
                    cursor=node.superName;
                }
            }
        }
        return result;
    }
    private static MethodNode declared(ClassNode node, Set<String> names, String desc){
        if(node==null)return null;
        MethodNode match=null;
        for(MethodNode m:node.methods)if(names.contains(m.name)&&m.desc.equals(desc)
                && (m.access & (Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
            if(match!=null)return null;
            match=m;
        }return match;
    }
    private static boolean declaredHook(Map<String,ClassNode> classes,String source,
                                        Set<String> names,String desc){
        Set<String> seen=new HashSet<>();
        for(String cur=source;cur!=null&&seen.add(cur);){
            ClassNode node=classes.get(cur);
            if(node==null)break;
            if(declared(node,names,desc)!=null)return true;
            cur=node.superName;
        }return false;
    }
    private static List<MethodRef> methodClosure(Map<String,ClassNode> classes,String tile,
                                                  String mcp,String srg){
        Set<String> seen=new HashSet<>(), names=Set.of(mcp,srg);
        ClassNode start=null;MethodNode found=null;
        for(String cur=tile;cur!=null&&seen.add(cur);){
            ClassNode node=classes.get(cur);if(node==null)break;
            MethodNode candidate=declared(node,names,NBT_DESC);
            if(candidate!=null){start=node;found=candidate;break;}
            cur=node.superName;
        }
        if(start==null)return List.of();
        List<MethodRef> closure=new ArrayList<>();
        Set<String> visited=new HashSet<>();
        while(start!=null && found!=null && closure.size()<MAX_SOURCE_DEPTH && visited.add(start.name)){
            closure.add(new MethodRef(start,found));
            MethodInsnNode parentCall=null;
            for(AbstractInsnNode op:found.instructions)
                if(op instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                        && names.contains(call.name) && NBT_DESC.equals(call.desc)
                        && descends(classes,start.name,call.owner)){
                    if(parentCall!=null)return List.of();
                    parentCall=call;
                }
            if(parentCall==null||parentCall.owner.equals(TILE))break;
            start=classes.get(parentCall.owner);
            found=declared(start,names,NBT_DESC);
            if(found==null)return List.of();
        }
        return closure;
    }
    private static Context frames(ClassNode owner,MethodNode method){
        try{
            Frame<SourceValue>[] frame=new Analyzer<>(new SourceInterpreter()).analyze(owner.name,method);
            Map<AbstractInsnNode,Integer> offsets=new IdentityHashMap<>();
            for(int i=0;i<method.instructions.size();i++)offsets.put(method.instructions.get(i),i);
            return new Context(frame,offsets);
        }catch(AnalyzerException|RuntimeException bad){return null;}
    }
    private static boolean linear(MethodNode m){
        if(m==null||!m.tryCatchBlocks.isEmpty()||m.instructions.size()>MAX_METHOD_INSNS)return false;
        int returns=0;
        int lastOpcode=-1;
        for(AbstractInsnNode op:m.instructions){
            if(op.getOpcode()<0)continue;
            lastOpcode=op.getOpcode();
            if(op.getOpcode()==Opcodes.RETURN)returns++;
            if(op instanceof JumpInsnNode||op instanceof LookupSwitchInsnNode
                    ||op instanceof TableSwitchInsnNode||op instanceof InvokeDynamicInsnNode
                    ||op.getOpcode()==Opcodes.ATHROW||op.getOpcode()==Opcodes.MONITORENTER
                    ||op.getOpcode()==Opcodes.MONITOREXIT||op.getOpcode()==Opcodes.PUTSTATIC
                    ||(op.getOpcode()>=Opcodes.IRETURN && op.getOpcode()<=Opcodes.ARETURN))return false;
        }
        return returns==1 && lastOpcode==Opcodes.RETURN;
    }
    private static boolean initialLocal(Context ctx, VarInsnNode var, int slot){
        Frame<SourceValue> frame=ctx.at(var);
        if(frame==null || var.getOpcode()!=Opcodes.ALOAD || var.var!=slot || slot>=frame.getLocals())return false;
        SourceValue local=frame.getLocal(slot);
        return local!=null && local.insns!=null && local.insns.isEmpty();
    }
    private static boolean parameter(Context ctx,SourceValue value,int slot){
        if(value==null || value.insns==null || value.insns.size()!=1)return false;
        AbstractInsnNode producer=value.insns.iterator().next();
        return producer instanceof VarInsnNode var && initialLocal(ctx,var,slot);
    }
    private static boolean thisField(Context ctx,SourceValue value, FieldKey field,
                                     Map<String,ClassNode> classes){
        if(value==null || value.insns==null || value.insns.size()!=1)return false;
        AbstractInsnNode producer=value.insns.iterator().next();
        if(!(producer instanceof FieldInsnNode get) || get.getOpcode()!=Opcodes.GETFIELD
                || !field.owner.equals(get.owner) || !field.name.equals(get.name)
                || !field.descriptor.equals(get.desc) || !classField(classes,field))return false;
        Frame<SourceValue> frame=ctx.at(get);
        return frame!=null && frame.getStackSize()>0
                && parameter(ctx,frame.getStack(frame.getStackSize()-1),0);
    }
    private static boolean classField(Map<String,ClassNode> classes,FieldKey field){
        ClassNode node=classes.get(field.owner);
        return node!=null && node.fields.stream().anyMatch(f->f.name.equals(field.name)
                && f.desc.equals(field.descriptor) && (f.access & Opcodes.ACC_STATIC)==0);
    }
    private static String literal(SourceValue value){
        if(value==null || value.insns==null || value.insns.size()!=1)return null;
        AbstractInsnNode op=value.insns.iterator().next();
        return op instanceof LdcInsnNode ldc && ldc.cst instanceof String s && !s.isBlank()
                && s.length()<=128 ? s : null;
    }
    private static boolean setCall(MethodInsnNode call, String descriptor){
        if(!call.owner.equals(NBT) || call.getOpcode()!=Opcodes.INVOKEVIRTUAL)return false;
        String[] names=ACCESS.get(descriptor);
        return names!=null && Set.of(names[0],names[1]).contains(call.name)
                && call.desc.equals("(Ljava/lang/String;"+descriptor+")V");
    }
    private static boolean getCall(MethodInsnNode call, String descriptor){
        if(!call.owner.equals(NBT) || call.getOpcode()!=Opcodes.INVOKEVIRTUAL)return false;
        String[] names=ACCESS.get(descriptor);
        return names!=null && Set.of(names[2],names[3]).contains(call.name)
                && call.desc.equals("(Ljava/lang/String;)"+descriptor);
    }
    private static boolean primitiveNbtMethod(MethodInsnNode call,boolean write){
        for(String type:ACCESS.keySet())if((write?setCall(call,type):getCall(call,type)))return true;
        return false;
    }
    private static boolean superCall(MethodInsnNode call,MethodRef ref,boolean write,
                                     Map<String,ClassNode> classes){
        if(call.getOpcode()!=Opcodes.INVOKESPECIAL||!call.desc.equals(NBT_DESC))return false;
        if(!Set.of(write?"writeToNBT":"readFromNBT",write?"func_145841_b":"func_145839_a")
                .contains(call.name))return false;
        if(call.owner.equals(ref.owner.superName))return true;
        return call.owner.equals(TILE)&&descends(classes,ref.owner.name,TILE);
    }
    private static boolean scanWrite(Map<String,ClassNode> classes,List<MethodRef> methods,
                                     Map<FieldKey,Access> writes){
        if(methods.isEmpty())return true;
        for(MethodRef ref:methods){
            MethodNode method=ref.method;if(!linear(method))return false;
            Context ctx=frames(ref.owner,method);if(ctx==null)return false;
            for(AbstractInsnNode op:method.instructions){
                if(op instanceof FieldInsnNode f && f.getOpcode()==Opcodes.PUTFIELD)return false;
                if(!(op instanceof MethodInsnNode call))continue;
                if(superCall(call,ref,true,classes)){
                    Frame<SourceValue> superFrame=ctx.at(call);
                    if(superFrame==null || superFrame.getStackSize()<2
                            || !parameter(ctx,superFrame.getStack(superFrame.getStackSize()-2),0)
                            || !parameter(ctx,superFrame.getStack(superFrame.getStackSize()-1),1))return false;
                    continue;
                }
                if(!primitiveNbtMethod(call,true))return false;
                Frame<SourceValue> frame=ctx.at(call);
                if(frame==null||frame.getStackSize()<3 || !parameter(ctx,frame.getStack(frame.getStackSize()-3),1))return false;
                String key=literal(frame.getStack(frame.getStackSize()-2));if(key==null)return false;
                boolean found=false;
                for(String desc:ACCESS.keySet())if(setCall(call,desc)){
                    SourceValue value=frame.getStack(frame.getStackSize()-1);
                    if(value==null||value.insns==null||value.insns.size()!=1)return false;
                    AbstractInsnNode origin=value.insns.iterator().next();
                    if(!(origin instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD
                            || !field.desc.equals(desc))return false;
                    FieldKey id=new FieldKey(field.owner,field.name,field.desc);
                    if(!thisField(ctx,value,id,classes)||writes.putIfAbsent(id,new Access(key,desc))!=null)return false;
                    found=true;break;
                }
                if(!found)return false;
            }
        }return true;
    }
    private static boolean scanRead(Map<String,ClassNode> classes,List<MethodRef> methods,
                                    Map<FieldKey,Access> reads){
        if(methods.isEmpty())return true;
        for(MethodRef ref:methods){
            MethodNode method=ref.method;if(!linear(method))return false;
            Context ctx=frames(ref.owner,method);if(ctx==null)return false;
            int getters=0,assigned=0;
            for(AbstractInsnNode op:method.instructions){
                if(op instanceof MethodInsnNode call){
                    if(superCall(call,ref,false,classes)){
                        Frame<SourceValue> superFrame=ctx.at(call);
                        if(superFrame==null || superFrame.getStackSize()<2
                                || !parameter(ctx,superFrame.getStack(superFrame.getStackSize()-2),0)
                                || !parameter(ctx,superFrame.getStack(superFrame.getStackSize()-1),1))return false;
                        continue;
                    }
                    if(!primitiveNbtMethod(call,false))return false;
                    getters++;
                }
                if(!(op instanceof FieldInsnNode put) || put.getOpcode()!=Opcodes.PUTFIELD)continue;
                FieldKey field=new FieldKey(put.owner,put.name,put.desc);
                if(!classField(classes,field)||!ACCESS.containsKey(put.desc))return false;
                Frame<SourceValue> frame=ctx.at(put);
                if(frame==null||frame.getStackSize()<2||!parameter(ctx,frame.getStack(frame.getStackSize()-2),0))return false;
                SourceValue assignedValue=frame.getStack(frame.getStackSize()-1);
                if(assignedValue==null||assignedValue.insns==null||assignedValue.insns.size()!=1)return false;
                AbstractInsnNode origin=assignedValue.insns.iterator().next();
                if(!(origin instanceof MethodInsnNode call)||!getCall(call,put.desc))return false;
                Frame<SourceValue> args=ctx.at(origin);
                if(args==null||args.getStackSize()<2||!parameter(ctx,args.getStack(args.getStackSize()-2),1))return false;
                String key=literal(args.getStack(args.getStackSize()-1));if(key==null)return false;
                if(reads.putIfAbsent(field,new Access(key,put.desc))!=null)return false;
                assigned++;
            }
            if(getters!=assigned)return false;
        }return true;
    }
}
