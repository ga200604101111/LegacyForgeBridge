import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Exact rev229 class overlay patch. The helper class is compiled separately from the matching
 * Rev229ArmorCompat.java source and copied into the cumulative rev228 artifact before this patch.
 */
public final class PatchRev229 {
    static final String MOD = "dev/yinghuang/legacyforgebridge/compat/LegacySourceItemContract$Modifier";
    static final String BUILDER = "net/minecraft/class_9285$class_9286";
    static final String DISPLAY = "net/minecraft/class_9285$class_11193";
    static final String HELPER = "dev/yinghuang/legacyforgebridge/behavior/Rev229ArmorCompat";
    static final String DISPLAY_ADD = "(Lnet/minecraft/class_6880;Lnet/minecraft/class_1322;Lnet/minecraft/class_9274;Lnet/minecraft/class_9285$class_11193;)Lnet/minecraft/class_9285$class_9286;";

    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[0]);
        patchSource(root.resolve("dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class"));
        patchClient(root.resolve("dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorClient.class"));
        patchBuildInfo(root.resolve("dev/yinghuang/legacyforgebridge/BuildInfo.class"));
    }

    static ClassNode read(Path p)throws Exception{
        ClassNode n=new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(n,0);
        return n;
    }
    static void write(Path p,ClassNode n)throws Exception{
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        n.accept(w);
        Files.write(p,w.toByteArray());
    }
    static AbstractInsnNode prevReal(AbstractInsnNode n){
        for(AbstractInsnNode p=n==null?null:n.getPrevious();p!=null;p=p.getPrevious())if(p.getOpcode()>=0)return p;
        return null;
    }
    static AbstractInsnNode nextReal(AbstractInsnNode n){
        for(AbstractInsnNode p=n==null?null:n.getNext();p!=null;p=p.getNext())if(p.getOpcode()>=0)return p;
        return null;
    }

    static void patchSource(Path p)throws Exception{
        ClassNode n=read(p);
        MethodNode target=null;
        for(MethodNode m:n.methods)
            if(m.name.equals("configure")&&m.desc.equals("(Ljava/lang/String;Lnet/minecraft/class_1792$class_1793;)V")){target=m;break;}
        if(target==null)throw new IllegalStateException("configure missing");

        int movementSkip=0;
        for(AbstractInsnNode x=target.instructions.getFirst();x!=null;x=x.getNext()){
            if(!(x instanceof LdcInsnNode ldc)||!"generic.movementSpeed".equals(ldc.cst))continue;
            AbstractInsnNode eq=nextReal(x), ifeq=nextReal(eq), go=nextReal(ifeq);
            if(ifeq instanceof JumpInsnNode keep && ifeq.getOpcode()==Opcodes.IFEQ
                    && go instanceof JumpInsnNode skip && go.getOpcode()==Opcodes.GOTO){
                skip.label=keep.label;
                movementSkip++;
                break;
            }
        }
        if(movementSkip!=1)throw new IllegalStateException("movement skip patch count="+movementSkip);

        List<MethodInsnNode> displayAdds=new ArrayList<>();
        for(AbstractInsnNode x=target.instructions.getFirst();x!=null;x=x.getNext())
            if(x instanceof MethodInsnNode c&&c.owner.equals(BUILDER)
                    &&c.name.equals("method_70728")&&c.desc.equals(DISPLAY_ADD))displayAdds.add(c);
        if(displayAdds.size()!=2)throw new IllegalStateException("expected 2 display adds, got "+displayAdds.size());

        MethodInsnNode sourceAdd=displayAdds.get(0);
        AbstractInsnNode hidden=prevReal(sourceAdd);
        if(!(hidden instanceof MethodInsnNode h)||h.getOpcode()!=Opcodes.INVOKESTATIC
                ||!h.owner.equals(DISPLAY)||!h.name.equals("method_70733"))
            throw new IllegalStateException("source hidden display anchor missing");
        target.instructions.remove(hidden);

        InsnList extra=new InsnList();
        extra.add(new VarInsnNode(Opcodes.ALOAD,7));
        extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,MOD,"attribute","()Ljava/lang/String;",false));
        target.instructions.insertBefore(sourceAdd,extra);
        sourceAdd.setOpcode(Opcodes.INVOKESTATIC);
        sourceAdd.owner=HELPER;
        sourceAdd.name="addArmorSourceModifier";
        sourceAdd.desc="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V";
        sourceAdd.itf=false;

        AbstractInsnNode obsoletePop=nextReal(sourceAdd);
        if(obsoletePop==null||obsoletePop.getOpcode()!=Opcodes.POP)
            throw new IllegalStateException("source add POP anchor missing");
        target.instructions.remove(obsoletePop);
        write(p,n);
    }

    static void patchClient(Path p)throws Exception{
        ClassNode n=read(p);
        MethodNode target=null;
        for(MethodNode m:n.methods)if(m.name.equals("lambda$onInitializeClient$2")){target=m;break;}
        if(target==null)throw new IllegalStateException("tooltip lambda missing");

        TypeInsnNode anchor=null;
        for(AbstractInsnNode x=target.instructions.getFirst();x!=null;x=x.getNext())
            if(x instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW
                    &&t.desc.equals("dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorClient$TooltipComponents")){anchor=t;break;}
        if(anchor==null)throw new IllegalStateException("TooltipComponents anchor missing");

        InsnList add=new InsnList();
        add.add(new VarInsnNode(Opcodes.ALOAD,0));
        add.add(new VarInsnNode(Opcodes.ALOAD,3));
        add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"appendLegacyArmorTooltip",
                "(Ljava/lang/Object;Ljava/util/List;)V",false));
        target.instructions.insertBefore(anchor,add);
        write(p,n);
    }

    static void patchBuildInfo(Path p)throws Exception{
        ClassNode n=read(p);
        MethodNode clinit=null;
        boolean version=false,revision=false,cache=false;
        for(MethodNode m:n.methods)if(m.name.equals("<clinit>")){clinit=m;break;}
        if(clinit==null)throw new IllegalStateException("BuildInfo clinit missing");

        for(AbstractInsnNode x=clinit.instructions.getFirst();x!=null;x=x.getNext()){
            if(x instanceof LdcInsnNode l&&l.cst instanceof String s){
                if(s.equals("0.2.0-alpha.27-corpus4-local.12-rev228-local-test.1")){
                    l.cst="0.2.0-alpha.27-corpus4-local.13-rev229-local-test.1";version=true;
                }else if(s.equals("2026-10-01.228-iy-armor-hud-base-attribute-visibility")){
                    l.cst="2026-10-01.229-armor-display-hud-carrier-source-attributes";revision=true;
                }
            }
            if(x instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC
                    &&f.name.equals("CACHE_COMPATIBILITY_VERSION"))cache=true;
        }

        if(!cache){
            AbstractInsnNode ret=null;
            for(AbstractInsnNode x=clinit.instructions.getLast();x!=null;x=x.getPrevious())
                if(x.getOpcode()==Opcodes.RETURN){ret=x;break;}
            if(ret==null)throw new IllegalStateException("BuildInfo return missing");
            InsnList add=new InsnList();
            add.add(new LdcInsnNode("0.2.0-alpha.27-corpus4-local.11-rev227-cache.1"));
            add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "dev/yinghuang/legacyforgebridge/BuildInfo","text",
                    "(Ljava/lang/String;)Ljava/lang/String;",false));
            add.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                    "dev/yinghuang/legacyforgebridge/BuildInfo","CACHE_COMPATIBILITY_VERSION","Ljava/lang/String;"));
            clinit.instructions.insertBefore(ret,add);
        }

        if(!version||!revision)throw new IllegalStateException("BuildInfo anchors missing");
        write(p,n);
    }
}
