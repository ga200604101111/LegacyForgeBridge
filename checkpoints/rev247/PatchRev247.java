import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Guarded, named-method edits over the pinned cumulative binary. Not a game transformer. */
public final class PatchRev247 implements Opcodes {
    static final String P="dev/yinghuang/legacyforgebridge/", UI=P+"desktop/DesktopProgressUi", SUP=P+"desktop/Rev247SupportWindow", CACHE=P+"convert/Rev247CacheCompatibility";
    static ClassNode read(byte[] b){ClassNode n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
    static MethodNode method(ClassNode n,String name,String desc){
        List<MethodNode> list=n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();
        if(list.size()!=1)throw new IllegalStateException(n.name+" method guard "+name+desc+" count="+list.size());return list.getFirst();
    }
    static void replace(MethodNode m,InsnList code){m.instructions=code;m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;}
    static MethodInsnNode call(String owner,String name,String desc){return new MethodInsnNode(INVOKESTATIC,owner,name,desc,false);}
    static InsnList list(AbstractInsnNode... a){InsnList i=new InsnList();for(var n:a)i.add(n);return i;}
    static void save(ClassNode n,Path out)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Path p=out.resolve(n.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
    static void count(int value,int expected,String name){if(value!=expected)throw new IllegalStateException(name+" count="+value+" expected="+expected);System.out.println("GUARD "+name+"="+value);}
    static String metadata(String s){return s.replace("0.2.0-alpha.27-corpus4-local.29-rev246-ui.1","0.2.0-alpha.27-corpus4-local.30-rev247-ui-cache.1")
        .replace("2026-10-03.246-desktop-status-support-window","2026-10-03.245-deep-motion-correlation-diagnostics");}
    static void metadata(ClassNode n,int expected){int changes=0;for(MethodNode m:n.methods)for(AbstractInsnNode a:m.instructions.toArray()) {
        if(a instanceof LdcInsnNode l&&l.cst instanceof String s){String t=metadata(s);if(!s.equals(t)){l.cst=t;changes++;}}
        if(a instanceof InvokeDynamicInsnNode d)for(int i=0;i<d.bsmArgs.length;i++)if(d.bsmArgs[i] instanceof String s){String t=metadata(s);if(!s.equals(t)){d.bsmArgs[i]=t;changes++;}}
    }count(changes,expected,n.name+" metadata strings");}
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]);
        try(ZipFile z=new ZipFile(args[0])){
            ClassNode ui=read(z.getInputStream(z.getEntry(UI+".class")).readAllBytes());
            replace(method(ui,"showSupportedMods","(Ljavax/swing/JFrame;)V"),list(new VarInsnNode(ALOAD,0),call(SUP,"open","(Ljavax/swing/JFrame;)V"),new InsnNode(RETURN)));
            replace(method(ui,"displayState","(Ljava/lang/String;)Ljava/lang/String;"),list(new VarInsnNode(ALOAD,0),call(SUP,"displayState","(Ljava/lang/String;)Ljava/lang/String;"),new InsnNode(ARETURN)));
            String update="(Ljavax/swing/JFrame;Ljava/util/Properties;Ljava/lang/String;)V";
            MethodNode oldUpdate=method(ui,"updateEdt",update);oldUpdate.name="rev247$updateEdtOriginal";
            MethodNode nu=new MethodNode(ACC_PRIVATE|ACC_STATIC,"updateEdt",update,null,null);
            nu.instructions=list(new VarInsnNode(ALOAD,0),new VarInsnNode(ALOAD,1),new VarInsnNode(ALOAD,2),call(SUP,"observe",update),
                new VarInsnNode(ALOAD,0),new VarInsnNode(ALOAD,1),new VarInsnNode(ALOAD,2),call(UI,oldUpdate.name,update),new InsnNode(RETURN));ui.methods.add(nu);
            String hide="(Ljavax/swing/JFrame;L"+UI+"$State;Ljava/awt/event/ActionEvent;)V";
            MethodNode oldHide=method(ui,"lambda$updateEdt$5",hide);oldHide.name="rev247$autoHideOriginal";
            MethodNode nh=new MethodNode(ACC_PRIVATE|ACC_STATIC,"lambda$updateEdt$5",hide,null,null);LabelNode run=new LabelNode();
            nh.instructions=list(new VarInsnNode(ALOAD,0),new VarInsnNode(ALOAD,2),call(SUP,"deferAutoHide","(Ljavax/swing/JFrame;Ljava/awt/event/ActionEvent;)Z"),
                new JumpInsnNode(IFEQ,run),new InsnNode(RETURN),run,new FrameNode(F_SAME,0,null,0,null),new VarInsnNode(ALOAD,0),new VarInsnNode(ALOAD,1),new VarInsnNode(ALOAD,2),call(UI,oldHide.name,hide),new InsnNode(RETURN));ui.methods.add(nh);
            save(ui,out);System.out.println("GUARD UI delegation/wrappers=4");
            for(String name:List.of("BuildInfo","LegacyFileLogger","network/FmlConnectionTrace","behavior/LegacyMotionTraceLog")){
                ClassNode n=read(z.getInputStream(z.getEntry(P+name+".class")).readAllBytes());
                metadata(n,name.equals("BuildInfo")||name.equals("LegacyFileLogger")?2:1);
                if(name.equals("behavior/LegacyMotionTraceLog")){
                    int c=0;MethodNode event=method(n,"event","(Ljava/lang/String;Ljava/lang/String;)V");
                    for(AbstractInsnNode a:event.instructions.toArray())if(a instanceof MethodInsnNode i&&i.owner.equals(P+"behavior/Rev245VelocityCorrelation")&&i.name.equals("decorate")){
                        if(!i.desc.equals("(Ljava/lang/String;Ljava/lang/String;JJ)Ljava/lang/String;"))throw new IllegalStateException("decorator descriptor changed");
                        i.owner=P+"behavior/Rev247GroundEvidence";c++;
                    }count(c,1,"ground evidence decorator");
                }save(n,out);
            }
            ClassNode snapshot=read(z.getInputStream(z.getEntry(P+"convert/ConversionStateStore$Snapshot.class")).readAllBytes());
            MethodNode access=method(snapshot,"cacheFingerprint","()Ljava/lang/String;");int c=0;
            for(AbstractInsnNode a:access.instructions.toArray())if(a.getOpcode()==ARETURN){access.instructions.insertBefore(a,call(CACHE,"normalizeFingerprint","(Ljava/lang/String;)Ljava/lang/String;"));c++;}
            count(c,1,"snapshot read normalization");save(snapshot,out);
            ClassNode manager=read(z.getInputStream(z.getEntry(P+"convert/LegacyConversionManager.class")).readAllBytes());
            MethodNode load=method(manager,"loadCache","()Ljava/util/Properties;");c=0;
            for(AbstractInsnNode a:load.instructions.toArray())if(a.getOpcode()==ARETURN){load.instructions.insertBefore(a,call(CACHE,"normalizeCache","(Ljava/util/Properties;)Ljava/util/Properties;"));c++;}
            count(c,2,"cache read normalization");save(manager,out);
            // Retain cached diagnostics across this exact presentation-only revision alias.
            ClassNode report=read(z.getInputStream(z.getEntry(P+"desktop/ConversionOutcomeReport.class")).readAllBytes());c=0;
            for(MethodNode m:report.methods)for(AbstractInsnNode a:m.instructions.toArray())if(a instanceof LdcInsnNode l&&"converterRevision".equals(l.cst)){
                AbstractInsnNode next=a.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                if(!(next instanceof MethodInsnNode s)||!s.name.equals("str"))continue;
                next=next.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                if(next instanceof MethodInsnNode eq&&eq.owner.equals("java/lang/String")&&eq.name.equals("equals")&&eq.desc.equals("(Ljava/lang/Object;)Z")){
                    m.instructions.set(eq,call(CACHE,"equivalentRevision","(Ljava/lang/String;Ljava/lang/String;)Z"));c++;
                }
            }count(c,1,"cached report revision alias");save(report,out);
        }
    }
}
