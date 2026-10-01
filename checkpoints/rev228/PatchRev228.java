// Exact local binary-overlay patcher used for rev228.
// Compile/run with JDK 21 internal ASM exports. It patches the cumulative rev227 main JAR.
import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class PatchRev228 {
    static final String VERSION = "0.2.0-alpha.27-corpus4-local.12-rev228-local-test.1";
    static final String REVISION = "2026-10-01.228-iy-armor-hud-base-attribute-visibility";
    static final String DISPLAY = "net/minecraft/class_9285$class_11193";
    static final String BUILDER = "net/minecraft/class_9285$class_9286";
    static final String OLD_ADD = "(Lnet/minecraft/class_6880;Lnet/minecraft/class_1322;Lnet/minecraft/class_9274;)Lnet/minecraft/class_9285$class_9286;";
    static final String NEW_ADD = "(Lnet/minecraft/class_6880;Lnet/minecraft/class_1322;Lnet/minecraft/class_9274;Lnet/minecraft/class_9285$class_11193;)Lnet/minecraft/class_9285$class_9286;";

    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[0]);
        patchGeneratedArmorHidden(root.resolve("dev/yinghuang/legacyforgebridge/convert/runtime/GeneratedModSupport.class"));
        patchLegacySourceEquipmentAttributes(root.resolve("dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class"));
        patchLegacyBehaviorClient(root.resolve("dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorClient.class"));
        patchConfigScreen(root.resolve("dev/yinghuang/legacyforgebridge/config/LegacyBlockingPoseConfigScreen.class"));
        patchBuildInfo(root.resolve("dev/yinghuang/legacyforgebridge/BuildInfo.class"));
    }
    static ClassNode read(Path p)throws Exception{ClassNode n=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(n,0);return n;}
    static void write(Path p,ClassNode n)throws Exception{ClassWriter w=new ClassWriter(0);n.accept(w);Files.write(p,w.toByteArray());}
    static AbstractInsnNode prevReal(AbstractInsnNode n){for(AbstractInsnNode p=n==null?null:n.getPrevious();p!=null;p=p.getPrevious())if(p.getOpcode()>=0)return p;return null;}
    static AbstractInsnNode nextReal(AbstractInsnNode n){for(AbstractInsnNode p=n==null?null:n.getNext();p!=null;p=p.getNext())if(p.getOpcode()>=0)return p;return null;}
    static void hide(MethodNode m,MethodInsnNode c){m.instructions.insertBefore(c,new MethodInsnNode(Opcodes.INVOKESTATIC,DISPLAY,"method_70733","()L"+DISPLAY+";",true));c.name="method_70728";c.desc=NEW_ADD;}
    static void patchGeneratedArmorHidden(Path p)throws Exception{
        ClassNode n=read(p);int count=0;
        for(MethodNode m:n.methods)if(m.name.equals("registerItem"))for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())
            if(x instanceof MethodInsnNode c&&c.owner.equals(BUILDER)&&c.name.equals("method_57487")&&c.desc.equals(OLD_ADD)){
                AbstractInsnNode prev=prevReal(c);if(prev instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==17){hide(m,c);count++;break;}}
        if(count!=1)throw new IllegalStateException("armor patch "+count);write(p,n);
    }
    static void patchLegacySourceEquipmentAttributes(Path p)throws Exception{
        ClassNode n=read(p);int hidden=0,skipped=0;
        for(MethodNode m:n.methods)if(m.name.equals("configure")){
            LabelNode loop=null;
            for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==6){
                AbstractInsnNode nx=nextReal(x);if(nx instanceof MethodInsnNode c&&c.owner.equals("java/util/Iterator")&&c.name.equals("hasNext")){
                    for(AbstractInsnNode b=x.getPrevious();b!=null;b=b.getPrevious()){if(b instanceof LabelNode l){loop=l;break;}if(b.getOpcode()>=0)break;}
                    if(loop==null){loop=new LabelNode();m.instructions.insertBefore(x,loop);}break;}}
            if(loop==null)throw new IllegalStateException("loop missing");
            for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ASTORE&&v.var==7){
                LabelNode keep=new LabelNode();InsnList a=new InsnList();
                a.add(new VarInsnNode(Opcodes.ALOAD,3));a.add(new JumpInsnNode(Opcodes.IFNULL,keep));a.add(new VarInsnNode(Opcodes.ALOAD,7));
                a.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"dev/yinghuang/legacyforgebridge/compat/LegacySourceItemContract$Modifier","attribute","()Ljava/lang/String;",false));
                a.add(new LdcInsnNode("generic.movementSpeed"));a.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
                a.add(new JumpInsnNode(Opcodes.IFEQ,keep));a.add(new JumpInsnNode(Opcodes.GOTO,loop));a.add(keep);m.instructions.insert(x,a);skipped++;break;}
            List<MethodInsnNode> calls=new ArrayList<>();
            for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof MethodInsnNode c&&c.owner.equals(BUILDER)&&c.name.equals("method_57487")&&c.desc.equals(OLD_ADD))calls.add(c);
            if(calls.size()<3)throw new IllegalStateException("attribute calls "+calls.size());hide(m,calls.get(1));hide(m,calls.get(calls.size()-1));hidden+=2;
        }
        if(hidden!=2||skipped!=1)throw new IllegalStateException("source patch "+hidden+"/"+skipped);write(p,n);
    }
    static void patchLegacyBehaviorClient(Path p)throws Exception{
        ClassNode n=read(p);int count=0;
        for(MethodNode m:n.methods)for(AbstractInsnNode x=m.instructions.getFirst();x!=null;){AbstractInsnNode nx=x.getNext();
            if(x instanceof MethodInsnNode c&&c.owner.equals("dev/yinghuang/legacyforgebridge/convert/Rev226Compat")&&c.name.equals("filterCompatAttributeTooltip")){
                AbstractInsnNode a2=prevReal(x),a1=prevReal(a2);m.instructions.remove(a1);m.instructions.remove(a2);m.instructions.remove(x);count++;}x=nx;}
        if(count!=1)throw new IllegalStateException("filter patch "+count);write(p,n);
    }
    static void patchConfigScreen(Path p)throws Exception{
        ClassNode n=read(p);int count=0;
        for(MethodNode m:n.methods){LdcInsnNode key=null;for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof LdcInsnNode l&&"legacyforgebridge.config.compat_attributes.category".equals(l.cst)){key=l;break;}
            if(key==null)continue;AbstractInsnNode start=prevReal(key),consumer=null;
            for(AbstractInsnNode x=key;x!=null;x=x.getNext())if(x instanceof MethodInsnNode c&&c.owner.equals("dev/yinghuang/legacyforgebridge/convert/Rev226Compat")&&c.name.equals("modernAttributesVisibilityConsumer")){consumer=x;break;}
            if(consumer==null)throw new IllegalStateException("consumer missing");AbstractInsnNode end=consumer;while(end!=null&&end.getOpcode()!=Opcodes.POP)end=end.getNext();if(end==null)throw new IllegalStateException("end missing");
            AbstractInsnNode after=end.getNext();for(AbstractInsnNode x=start;x!=after;){AbstractInsnNode nx=x.getNext();m.instructions.remove(x);x=nx;}count++;}
        if(count!=1)throw new IllegalStateException("config patch "+count);write(p,n);
    }
    static void patchBuildInfo(Path p)throws Exception{
        ClassNode n=read(p);boolean v=false,r=false;for(MethodNode m:n.methods)if(m.name.equals("<clinit>"))for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof LdcInsnNode l&&l.cst instanceof String s){
            if(s.equals("0.2.0-alpha.27-corpus4-local.11-rev227-local-test.1")){l.cst=VERSION;v=true;}else if(s.equals("2026-10-01.227-iy-projectile-particles-armor-cache-cloth")){l.cst=REVISION;r=true;}}
        if(!v||!r)throw new IllegalStateException("BuildInfo patch");write(p,n);
    }
}
