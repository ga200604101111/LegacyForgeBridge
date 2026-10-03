import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Offline named-method patch, restricted to the exact base SHA checked by build_local.py. */
public final class PatchRev248 implements Opcodes {
    static final String P="dev/yinghuang/legacyforgebridge/", H=P+"protocol/Rev248ProtocolStartup";
    static final String OLD="0.2.0-alpha.27-corpus4-local.30-rev247-ui-cache.1", NEW="0.2.0-alpha.27-corpus4-local.31-rev248-startup.1";
    static ClassNode read(ZipFile z,String n)throws Exception {ClassNode c=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(P+n+".class")).readAllBytes()).accept(c,0);return c;}
    static MethodNode method(ClassNode c,String name,String desc){var ms=c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();if(ms.size()!=1)throw new IllegalStateException("method guard "+name);return ms.getFirst();}
    static void count(int n,int expected,String label){if(n!=expected)throw new IllegalStateException(label+" count="+n);System.out.println("GUARD "+label+"="+n);}
    static void save(ClassNode c,Path out)throws Exception {ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
    static void meta(ClassNode c){int changed=0;for(FieldNode f:c.fields)if(OLD.equals(f.value)){f.value=NEW;changed++;}
        for(MethodNode m:c.methods)for(AbstractInsnNode a:m.instructions.toArray()){
            if(a instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains(OLD)){l.cst=s.replace(OLD,NEW);changed++;}
            if(a instanceof InvokeDynamicInsnNode d)for(int i=0;i<d.bsmArgs.length;i++)if(d.bsmArgs[i] instanceof String s&&s.contains(OLD)){d.bsmArgs[i]=s.replace(OLD,NEW);changed++;}
        }count(changed,c.name.endsWith("/Rev247SupportWindow")?2:1,c.name+" artifact-version");}
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]);
        try(ZipFile z=new ZipFile(args[0])){
            ClassNode entry=read(z,"protocol/ViaFabricPlusEntrypoint");
            MethodNode load=method(entry,"onPlatformLoad","(Lcom/viaversion/viafabricplus/api/ViaFabricPlusBase;)V");
            int selects=0,initializes=0;for(AbstractInsnNode a:load.instructions.toArray())if(a instanceof MethodInsnNode m){
                if(m.owner.equals(P+"protocol/ViaFabricPlusBackend")&&m.name.equals("selectMinecraft1710ForNextConnection"))selects++;
                if(m.owner.equals(P+"protocol/ViaFabricPlusBackend")&&m.name.equals("initialize"))initializes++;
            }count(selects,1,"old early selection");count(initializes,1,"backend initialization retained");
            load.instructions.clear();load.tryCatchBlocks.clear();if(load.localVariables!=null)load.localVariables.clear();
            load.instructions.add(new FieldInsnNode(GETSTATIC,P+"protocol/ViaFabricPlusBackend","INSTANCE","L"+P+"protocol/ViaFabricPlusBackend;"));
            load.instructions.add(new VarInsnNode(ALOAD,1));
            load.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,P+"protocol/ViaFabricPlusBackend","initialize","(Lcom/viaversion/viafabricplus/api/ViaFabricPlusBase;)V",false));
            load.instructions.add(new VarInsnNode(ALOAD,1));load.instructions.add(new MethodInsnNode(INVOKESTATIC,H,"install","(Ljava/lang/Object;)V",false));load.instructions.add(new InsnNode(RETURN));
            save(entry,out);
            for(String n:List.of("BuildInfo","LegacyFileLogger","network/FmlConnectionTrace","behavior/LegacyMotionTraceLog","desktop/Rev247SupportWindow")){
                ClassNode c=read(z,n);meta(c);
                if(n.equals("behavior/LegacyMotionTraceLog")){
                    MethodNode start=method(c,"start","(Ljava/lang/String;)Z");int hits=0;
                    for(AbstractInsnNode a:start.instructions.toArray())if(a instanceof MethodInsnNode m&&m.owner.equals(P+"behavior/Rev243Diagnostics")&&m.name.equals("captureStarted")){
                        start.instructions.insert(a,new MethodInsnNode(INVOKESTATIC,H,"captureContext","()V",false));hits++;
                    }count(hits,1,"single capture context call");
                }save(c,out);
            }
        }
    }
}
