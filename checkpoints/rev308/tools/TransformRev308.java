import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Frame-preserving straight-line patches applied only to exact rev307 intermediary class payloads. */
public final class TransformRev308 implements Opcodes {
    static final String REV="dev/yinghuang/legacyforgebridge/rev308/";
    static final String STACK="Lnet/minecraft/class_1799;";
    static final String PROPERTIES="Lnet/minecraft/class_1792$class_1793;";
    static int patched=0;
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: java TransformRev308 inputClassesDir outputClassesDir");
        Path src=Path.of(args[0]),dst=Path.of(args[1]);
        patch(src,dst,"dev/yinghuang/legacyforgebridge/convert/runtime/GeneratedModSupport",TransformRev308::generated);
        patch(src,dst,"dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime",TransformRev308::sourceItem);
        patch(src,dst,"dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorRuntime",TransformRev308::behavior);
        patch(src,dst,"dev/yinghuang/legacyforgebridge/compat/LegacySourceInteractionRuntime",TransformRev308::creative);
        patch(src,dst,"dev/yinghuang/legacyforgebridge/compat/LegacyProjectilePresentationRegistry",TransformRev308::projectile);
        if(patched!=7)throw new AssertionError("Unexpected patch point count "+patched+" (expected 7)");
        System.out.println("rev308 ASM patch sites: "+patched+", classes: 5");
    }
    interface Transformer{void apply(ClassNode node);}
    static void patch(Path src,Path dst,String internal,Transformer fn)throws Exception {
        byte[] input=Files.readAllBytes(src.resolve(internal+".class"));
        ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);
        fn.apply(node);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Path dest=dst.resolve(internal+".class");Files.createDirectories(dest.getParent());Files.write(dest,writer.toByteArray());
    }
    static MethodNode method(ClassNode n,String name,String desc){for(MethodNode m:n.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new AssertionError("Missing target "+n.name+"#"+name+desc);}
    static void generated(ClassNode c) {
        MethodNode m=method(c,"registerItem","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IFFF)V");
        int hits=0;
        for(AbstractInsnNode insn=m.instructions.getFirst();insn!=null;insn=insn.getNext()) {
            if(insn instanceof MethodInsnNode call&&call.owner.equals("dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime")&&call.name.equals("configure")) {
                InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,1));x.add(new VarInsnNode(ALOAD,2));x.add(new VarInsnNode(ALOAD,9));
                x.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308SwordBridge","configure","(Ljava/lang/String;Ljava/lang/String;"+PROPERTIES+")V",false));
                m.instructions.insert(insn,x);hits++;patched++;
            }
        }
        if(hits!=1)throw new AssertionError("registerItem bridge count "+hits);
    }
    static void sourceItem(ClassNode c) {
        MethodNode use=method(c,"fallbackUse","(Lnet/minecraft/class_1269;Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;)Lnet/minecraft/class_1269;");
        int u=0;
        for(AbstractInsnNode i=use.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==ARETURN){
            InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,1));x.add(new VarInsnNode(ALOAD,2));x.add(new VarInsnNode(ALOAD,3));
            x.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308SwordBridge","afterUse","(Lnet/minecraft/class_1269;Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;)Lnet/minecraft/class_1269;",false));
            use.instructions.insertBefore(i,x);u++;}
        if(u!=2)throw new AssertionError("fallbackUse return sites "+u);patched++;
        MethodNode action=method(c,"fallbackAction","(Lnet/minecraft/class_1799;Lnet/minecraft/class_1839;)Lnet/minecraft/class_1839;");
        int a=0;
        for(AbstractInsnNode i=action.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==ARETURN){
            InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,0));x.add(new InsnNode(SWAP));
            x.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308SwordBridge","afterAction","(Lnet/minecraft/class_1799;Lnet/minecraft/class_1839;)Lnet/minecraft/class_1839;",false));
            action.instructions.insertBefore(i,x);a++;}
        if(a!=1)throw new AssertionError("fallbackAction return sites "+a);patched++;
        MethodNode duration=method(c,"fallbackDuration","(Lnet/minecraft/class_1799;I)I");
        int d=0;
        for(AbstractInsnNode i=duration.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==IRETURN){
            InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,0));x.add(new InsnNode(SWAP));
            x.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308SwordBridge","afterDuration","(Lnet/minecraft/class_1799;I)I",false));
            duration.instructions.insertBefore(i,x);d++;}
        if(d!=1)throw new AssertionError("fallbackDuration return sites "+d);patched++;
    }
    static void behavior(ClassNode c) {
        MethodNode m=method(c,"tooltip","(Lnet/minecraft/class_1799;Lnet/minecraft/class_1657;ZLjava/util/List;)V");
        int count=0;
        for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==RETURN){
            InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,0));x.add(new VarInsnNode(ALOAD,3));
            x.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308TooltipBridge","append","(Lnet/minecraft/class_1799;Ljava/util/List;)V",false));
            m.instructions.insertBefore(i,x);count++;}
        if(count!=2)throw new AssertionError("tooltip return count "+count);patched++;
    }
    static void creative(ClassNode c) {
        MethodNode m=method(c,"emitCreative","(Lnet/minecraft/class_1761$class_7704;Lnet/minecraft/class_1792;)V");
        int count=0;
        for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/class_1761$class_7704")&&call.name.equals("method_45420")){
            InsnList x=new InsnList();x.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308CreativeEnchantBridge","decorate","(Lnet/minecraft/class_1799;)Lnet/minecraft/class_1799;",false));
            m.instructions.insertBefore(i,x);count++;}
        if(count!=2)throw new AssertionError("emitCreative sites "+count);patched++;
    }
    static void projectile(ClassNode c) {
        MethodNode load=method(c,"loadMod","(Ljava/lang/String;)V");
        int count=0;
        for(AbstractInsnNode i=load.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==RETURN){
            InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,0));
            x.add(new MethodInsnNode(INVOKESTATIC,c.name,"installRev308SourceRule","(Ljava/lang/String;)V",false));
            load.instructions.insertBefore(i,x);count++;
        }
        if(count<3)throw new AssertionError("loadMod return sites "+count);
        // Method has NO newly inserted control-flow branches; the source-rule lookup is fail-closed.
        MethodNode helper=new MethodNode(ACC_PRIVATE|ACC_STATIC,"installRev308SourceRule","(Ljava/lang/String;)V",null,null);
        LabelNode skip=new LabelNode();
        helper.instructions.add(new VarInsnNode(ALOAD,0));
        helper.instructions.add(new MethodInsnNode(INVOKESTATIC,REV+"Rev308ArrowBridge","sourceRule","(Ljava/lang/String;)Ldev/yinghuang/legacyforgebridge/compat/LegacyProjectilePresentationRegistry$Rule;",false));
        helper.instructions.add(new VarInsnNode(ASTORE,1));
        helper.instructions.add(new VarInsnNode(ALOAD,1));
        helper.instructions.add(new JumpInsnNode(IFNULL,skip));
        helper.instructions.add(new VarInsnNode(ALOAD,1));
        helper.instructions.add(new MethodInsnNode(INVOKESTATIC,c.name,"install","(Ldev/yinghuang/legacyforgebridge/compat/LegacyProjectilePresentationRegistry$Rule;)V",false));
        helper.instructions.add(new InsnNode(RETURN));
        helper.instructions.add(skip);
        helper.instructions.add(new InsnNode(RETURN));
        c.methods.add(helper);
        // new tiny helper needs explicit stack-map frame at IFNULL target for V21 (no hierarchy needed)
        helper.instructions.insert(skip,new FrameNode(F_SAME,0,null,0,null));
        patched++;
    }
}
