import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Straight-line lifecycle hooks over the exact cumulative rev240 class files. */
public final class PatchRev241 {
    static final String ROOT="dev/yinghuang/legacyforgebridge/";
    static final String HELPER=ROOT+"behavior/Rev241JumpMotionBridge";
    static MethodInsnNode call(String name,String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,name,desc,false);
    }
    static InsnList objects(String name,int... vars) {
        InsnList list=new InsnList();String desc="(";
        for(int v:vars){list.add(new VarInsnNode(Opcodes.ALOAD,v));desc+="Ljava/lang/Object;";}
        list.add(call(name,desc+")V"));return list;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("base.jar class-output-dir");
        Path output=Path.of(args[1]);
        try(ZipFile zip=new ZipFile(args[0])) {
            String name=ROOT+"behavior/LegacyClientJumpMotion.class";
            ClassNode n=read(zip,name);int sourceHooks=0,heads=0,initStrings=0,tailLabels=0;
            Set<String> hooked=new TreeSet<>();
            for(MethodNode m:n.methods) {
                if(m.name.equals("sourceJump")&&m.desc.equals("(Lnet/minecraft/class_1309;)V")) {
                    for(AbstractInsnNode i:m.instructions.toArray()) {
                        if(i instanceof MethodInsnNode c&&c.owner.equals(ROOT+"behavior/LegacyBehaviorRuntime")
                                &&c.name.equals("jump")&&c.desc.equals("(Lnet/minecraft/class_1309;)V")) {
                            InsnList before=new InsnList();before.add(new InsnNode(Opcodes.DUP));
                            before.add(call("sourceBefore","(Ljava/lang/Object;)V"));
                            m.instructions.insertBefore(c,before);
                            m.instructions.insert(c,objects("sourceAfter",0));sourceHooks++;
                        }
                    }
                }
                if(m.name.equals("velocityTail"))for(AbstractInsnNode i:m.instructions)
                    if(i instanceof InvokeDynamicInsnNode dyn)for(int k=0;k<dyn.bsmArgs.length;k++)
                        if(dyn.bsmArgs[k] instanceof String text&&text.contains("action=OBSERVE_ONLY")) {
                            dyn.bsmArgs[k]=text.replace("action=OBSERVE_ONLY","action=POST_RECONCILE_OBSERVATION");tailLabels++;
                        }
                String key=m.name+m.desc;
                InsnList h=switch(key) {
                    case "tick(Lnet/minecraft/class_310;)V" -> objects("tick",0);
                    case "nativeVelocity(Lnet/minecraft/class_1297;Lnet/minecraft/class_243;)V" -> objects("nativeVelocity",0,1);
                    case "velocityHead(Lnet/minecraft/class_2743;)V" -> objects("packetHead",0);
                    case "velocityTail(Lnet/minecraft/class_2743;)V" -> objects("packetTail",0);
                    case "barrier(Ljava/lang/String;)V" -> {
                        InsnList b=new InsnList();b.add(new VarInsnNode(Opcodes.ALOAD,0));
                        b.add(call("barrier","(Ljava/lang/String;)V"));yield b;
                    }
                    case "nativePosition(Lnet/minecraft/class_1297;DDD)V" -> {
                        InsnList b=new InsnList();b.add(new VarInsnNode(Opcodes.ALOAD,0));
                        for(int v:new int[]{1,3,5})b.add(new VarInsnNode(Opcodes.DLOAD,v));
                        b.add(call("nativePosition","(Ljava/lang/Object;DDD)V"));yield b;
                    }
                    default -> null;
                };
                if(h!=null){m.instructions.insert(h);heads++;hooked.add(key);}
                if(m.name.equals("initialize")&&m.desc.equals("()V"))
                    for(AbstractInsnNode i:m.instructions)if(i instanceof LdcInsnNode ldc
                            &&ldc.cst instanceof String text&&text.startsWith("LFB source motion compatibility READY mode=LEGACY_CLIENT_PREDICTION")) {
                        ldc.cst="LFB source motion compatibility READY rev241; source prediction preserved; matched-ascent reconciliation default ON (jumpReconcile=off disables); unmatched motion and packet XZ preserved; old diagnostic jumpEcho property={} is ignored";
                        initStrings++;
                    }
            }
            if(sourceHooks!=2||heads!=6||initStrings!=1||tailLabels!=1)
                throw new IllegalStateException("Unexpected baseline hooks: "+sourceHooks+","+heads+","+initStrings);
            write(output.resolve(name),n);
            name=ROOT+"BuildInfo.class";n=read(zip,name);int ver=0,rev=0;
            for(MethodNode m:n.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof LdcInsnNode l) {
                if("0.2.0-alpha.27-corpus4-local.24-rev240-local-test.1".equals(l.cst)) {
                    l.cst="0.2.0-alpha.27-corpus4-local.25-rev241-local-test.1";ver++;
                } else if("2026-10-02.240-liquid-cache-refresh-visible-fallback".equals(l.cst)) {
                    l.cst="2026-10-02.241-source-jump-history-reconciliation";rev++;
                }
            }
            if(ver!=1||rev!=1)throw new IllegalStateException("Wrong BuildInfo baseline");
            write(output.resolve(name),n);
            System.out.println("PATCH PASS sourceSites="+sourceHooks+" lifecycleHeads="+heads+" readiness="+initStrings+" version="+ver+" revision="+rev);
            System.out.println("HOOKS "+hooked);
        }
    }
    static ClassNode read(ZipFile zip,String name)throws Exception {
        ClassNode n=new ClassNode();try(var in=zip.getInputStream(zip.getEntry(name))){new ClassReader(in.readAllBytes()).accept(n,0);}return n;
    }
    static void write(Path path,ClassNode n)throws Exception {
        // Added instructions have zero net stack effect at old frame boundaries.
        // Keep all original StackMap frames; only recompute max stack/local sizes.
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);
        Files.createDirectories(path.getParent());Files.write(path,w.toByteArray());
    }
}
