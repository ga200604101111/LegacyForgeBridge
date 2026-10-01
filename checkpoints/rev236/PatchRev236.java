import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Exact rev236 binary overlay.
 *
 * The rev235 sourceJump suppression branch is retained structurally so existing StackMap frames
 * and branch targets do not need to change. Its finite upward-delta threshold (1e-6) is replaced
 * with +Infinity, making the suppression body unreachable for all finite source jump deltas while
 * preserving the source callback result.
 */
public final class PatchRev236 {
    static ClassNode read(Path p)throws Exception{
        ClassNode n=new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(n,0);
        return n;
    }
    static void write(Path p,ClassNode n)throws Exception{
        ClassWriter w=new ClassWriter(0);
        n.accept(w);
        Files.write(p,w.toByteArray());
    }
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);
        patchJump(root.resolve("dev/yinghuang/legacyforgebridge/behavior/LegacyClientJumpMotion.class"));
        patchBuild(root.resolve("dev/yinghuang/legacyforgebridge/BuildInfo.class"));
    }
    static void patchJump(Path p)throws Exception{
        ClassNode n=read(p);boolean threshold=false,banner=false;
        for(MethodNode m:n.methods){
            if(m.name.equals("sourceJump")&&m.desc.equals("(Lnet/minecraft/class_1309;)V")){
                for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
                    if(x instanceof LdcInsnNode l&&l.cst instanceof Double d&&Double.compare(d,1.0E-6D)==0){
                        l.cst=Double.POSITIVE_INFINITY;threshold=true;break;
                    }
                }
            }
            if(m.name.equals("initialize")){
                for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
                    if(x instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains("mode=SERVER_AUTHORITY")){
                        l.cst="LFB source motion compatibility READY mode=LEGACY_CLIENT_PREDICTION; source jump velocity is preserved exactly like 1.7.10 client prediction; incoming server velocity packets remain authoritative and are never canceled; jumpEcho property={} (diagnostic only)";
                        banner=true;
                    }
                }
            }
        }
        if(!threshold||!banner)throw new IllegalStateException("rev236 jump anchors missing");
        write(p,n);
    }
    static void patchBuild(Path p)throws Exception{
        ClassNode n=read(p);boolean v=false,r=false;
        for(MethodNode m:n.methods)if(m.name.equals("<clinit>"))for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
            if(x instanceof LdcInsnNode l&&l.cst instanceof String s){
                if(s.equals("0.2.0-alpha.27-corpus4-local.19-rev235-local-test.1")){l.cst="0.2.0-alpha.27-corpus4-local.20-rev236-local-test.1";v=true;}
                else if(s.equals("2026-10-01.235-generic-converted-stack-armor-mirror")){l.cst="2026-10-01.236-preserve-legacy-client-jump-prediction";r=true;}
            }
        }
        if(!v||!r)throw new IllegalStateException("rev236 BuildInfo anchors missing");
        write(p,n);
    }
}
