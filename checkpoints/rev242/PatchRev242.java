import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** ABI-preserving edits over the exact cumulative rev241 main JAR. */
public final class PatchRev242 {
    static final String P="dev/yinghuang/legacyforgebridge/", B=P+"behavior/", LAND=B+"Rev242LandingBridge";
    static final String V="0.2.0-alpha.27-corpus4-local.26-rev242-local-test.1";
    static ClassNode read(ZipFile z,String name)throws Exception {
        ClassNode n=new ClassNode();
        try(var in=z.getInputStream(z.getEntry(name+".class"))){new ClassReader(in.readAllBytes()).accept(n,0);}return n;
    }
    static void write(Path out,ClassNode n)throws Exception {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);
        Path f=out.resolve(n.name+".class");Files.createDirectories(f.getParent());Files.write(f,w.toByteArray());
    }
    static void require(int got,int expected,String description){if(got!=expected)throw new IllegalStateException(description+": "+got+" expected "+expected);}
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]);
        try(ZipFile z=new ZipFile(args[0])) {
            ClassNode n=read(z,B+"LegacyBehaviorRuntime");
            String desc="(Lnet/minecraft/class_1309;DFLnet/minecraft/class_1282;)Z";
            int methods=0,particleHooks=0;
            for(MethodNode m:n.methods) {
                if(m.name.equals("fall")&&m.desc.equals(desc)){m.name="lfb$fallBeforeRev242";methods++;}
                if(m.name.equals("runEvent")) for(AbstractInsnNode ins:m.instructions.toArray()) {
                    if(ins instanceof MethodInsnNode c && c.owner.equals(B+"LegacyBehaviorRuntime$Snapshot")&&c.name.equals("commit")) {
                        InsnList hook=new InsnList();hook.add(new InsnNode(Opcodes.DUP));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,LAND,"sourceParticles","(Ljava/lang/Object;)V",false));
                        m.instructions.insertBefore(ins,hook);particleHooks++;
                    }
                }
            }
            require(methods,1,"original fall method");require(particleHooks,1,"source-particle diagnostic hook");
            MethodNode wrapper=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"fall",desc,null,null);
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            wrapper.instructions.add(new VarInsnNode(Opcodes.DLOAD,1));
            wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD,3));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD,4));
            wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,LAND,"fall","(Ljava/lang/Object;DFLjava/lang/Object;)Z",false));
            wrapper.instructions.add(new InsnNode(Opcodes.IRETURN));n.methods.add(wrapper);write(out,n);
            n=read(z,B+"LegacyBehaviorRuntime$Snapshot");int positions=0;
            for(MethodNode m:n.methods)if(m.name.equals("fill"))for(AbstractInsnNode ins:m.instructions.toArray()) {
                if(ins instanceof MethodInsnNode c&&c.owner.equals("net/minecraft/class_1297")&&c.name.equals("method_23318")&&c.desc.equals("()D")) {
                    m.instructions.insertBefore(c,new InsnNode(Opcodes.DUP));
                    m.instructions.insert(c,new MethodInsnNode(Opcodes.INVOKESTATIC,LAND,"sourceY","(Ljava/lang/Object;D)D",false));
                    positions++;
                }
            }
            require(positions,1,"scoped local source-posY hook");write(out,n);
            n=read(z,B+"LegacyClientJumpMotion");int ready=0;
            for(MethodNode m:n.methods)for(AbstractInsnNode ins:m.instructions)if(ins instanceof LdcInsnNode l&&l.cst instanceof String s
                    &&s.startsWith("LFB source motion compatibility READY rev241;")) {
                l.cst="LFB source motion compatibility READY rev242; source boost unchanged; matched-ascent and previous stationary descent guard (jumpReconcile=off disables); source landing fallback active; jumpEcho={} ignored";ready++;
            }
            require(ready,1,"ready log");write(out,n);
            n=read(z,P+"BuildInfo");int version=0,revision=0;
            for(MethodNode m:n.methods)for(AbstractInsnNode ins:m.instructions)if(ins instanceof LdcInsnNode l) {
                if("0.2.0-alpha.27-corpus4-local.25-rev241-local-test.1".equals(l.cst)){l.cst=V;version++;}
                if("2026-10-02.241-source-jump-history-reconciliation".equals(l.cst)){
                    l.cst="2026-10-02.242-stationary-descent-and-source-landing";revision++;
                }
            }
            require(version,1,"build version");require(revision,1,"converter revision");write(out,n);
            System.out.println("PASS guarded ASM edits: fall wrapper, scoped snapshot posY, source-particle audit, ready/build labels");
        }
    }
}
