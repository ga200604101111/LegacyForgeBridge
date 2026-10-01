import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class PatchRev230 {
    static final String HELPER="dev/yinghuang/legacyforgebridge/behavior/Rev230ArmorNbtMerge";
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);
        patchModernBoundary(root.resolve("dev/yinghuang/legacyforgebridge/mixin/client/ViaModernModItemBoundaryMixin.class"));
        patchTooltip(root.resolve("dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorClient.class"));
        patchBuildInfo(root.resolve("dev/yinghuang/legacyforgebridge/BuildInfo.class"));
    }
    static ClassNode read(Path p)throws Exception{ClassNode n=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(n,0);return n;}
    static void write(Path p,ClassNode n)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Files.write(p,w.toByteArray());}
    static AbstractInsnNode prevReal(AbstractInsnNode n){for(AbstractInsnNode p=n==null?null:n.getPrevious();p!=null;p=p.getPrevious())if(p.getOpcode()>=0)return p;return null;}
    static void patchModernBoundary(Path p)throws Exception{
        ClassNode n=read(p);int client=0,server=0;
        for(MethodNode m:n.methods){
            if(m.name.equals("legacyforgebridge$restoreConvertedItemAtModernEdge")){
                MethodInsnNode use=null;
                for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof MethodInsnNode c&&c.owner.equals("dev/yinghuang/legacyforgebridge/compat/LegacyViaUseComponents")&&c.name.equals("restore")){use=c;break;}
                if(use==null)throw new IllegalStateException("client use restore anchor missing");
                InsnList a=new InsnList();
                a.add(new VarInsnNode(Opcodes.ALOAD,3));
                a.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable","getReturnValue","()Ljava/lang/Object;",false));
                a.add(new VarInsnNode(Opcodes.ALOAD,0));
                a.add(new TypeInsnNode(Opcodes.CHECKCAST,"com/viaversion/viaversion/api/rewriter/Rewriter"));
                a.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"com/viaversion/viaversion/api/rewriter/Rewriter","protocol","()Lcom/viaversion/viaversion/api/protocol/Protocol;",true));
                a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"toClient","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
                m.instructions.insertBefore(use,a);client++;
            } else if(m.name.equals("legacyforgebridge$encodeConvertedItemBeforeModernMapping")){
                MethodInsnNode prepare=null;
                for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof MethodInsnNode c&&c.owner.equals("dev/yinghuang/legacyforgebridge/compat/LegacyModItemIdentityBridge")&&c.name.equals("prepareModernItemForVia")){prepare=c;break;}
                if(prepare==null)throw new IllegalStateException("server prepare anchor missing");
                InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,2));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"toServer","(Ljava/lang/Object;)V",false));
                AbstractInsnNode before=prevReal(prevReal(prepare));
                m.instructions.insertBefore(before,a);server++;
            }
        }
        if(client!=1||server!=1)throw new IllegalStateException("boundary patch "+client+"/"+server);write(p,n);
    }
    static void patchTooltip(Path p)throws Exception{
        ClassNode n=read(p);int count=0;
        for(MethodNode m:n.methods)for(AbstractInsnNode x=m.instructions.getFirst();x!=null;){AbstractInsnNode nx=x.getNext();
            if(x instanceof MethodInsnNode c&&c.owner.equals("dev/yinghuang/legacyforgebridge/behavior/Rev229ArmorCompat")&&c.name.equals("appendLegacyArmorTooltip")){
                AbstractInsnNode a2=prevReal(x),a1=prevReal(a2);
                if(a1==null||a2==null)throw new IllegalStateException("tooltip args missing");
                m.instructions.remove(a1);m.instructions.remove(a2);m.instructions.remove(x);count++;}
            x=nx;}
        if(count!=1)throw new IllegalStateException("tooltip patch "+count);write(p,n);
    }
    static void patchBuildInfo(Path p)throws Exception{
        ClassNode n=read(p);boolean v=false,r=false;
        for(MethodNode m:n.methods)if(m.name.equals("<clinit>"))for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof LdcInsnNode l&&l.cst instanceof String s){
            if(s.equals("0.2.0-alpha.27-corpus4-local.13-rev229-local-test.1")){l.cst="0.2.0-alpha.27-corpus4-local.14-rev230-local-test.1";v=true;}
            else if(s.equals("2026-10-01.229-armor-display-hud-carrier-source-attributes")){l.cst="2026-10-01.230-legacy-nbt-armor-merge";r=true;}}
        if(!v||!r)throw new IllegalStateException("BuildInfo anchors "+v+"/"+r);write(p,n);
    }
}
