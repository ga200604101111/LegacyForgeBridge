import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class PatchRev234 {
    static final String SYNC="dev/yinghuang/legacyforgebridge/behavior/Rev234ArmorAttributeSync";
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);
        patchLiving(root.resolve("dev/yinghuang/legacyforgebridge/mixin/client/LegacyLivingBehaviorMixin.class"));
        patchBuildInfo(root.resolve("dev/yinghuang/legacyforgebridge/BuildInfo.class"));
    }
    static ClassNode read(Path p)throws Exception{ClassNode n=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(n,0);return n;}
    static void write(Path p,ClassNode n)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Files.write(p,w.toByteArray());}

    static void patchLiving(Path p)throws Exception{
        ClassNode n=read(p);MethodNode target=null;
        for(MethodNode m:n.methods)if(m.name.equals("legacyforgebridge$armorHud1710")){target=m;break;}
        if(target==null)throw new IllegalStateException("armor HUD injection missing");
        target.instructions.clear();target.tryCatchBlocks.clear();target.localVariables=null;
        InsnList ins=new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD,0));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,SYNC,"sync","(Ljava/lang/Object;)V",false));
        ins.add(new InsnNode(Opcodes.RETURN));
        target.instructions.add(ins);target.maxStack=1;target.maxLocals=2;
        if(target.visibleAnnotations==null)throw new IllegalStateException("Inject annotation missing");
        boolean changed=false;
        for(AnnotationNode a:target.visibleAnnotations){
            if(!"Lorg/spongepowered/asm/mixin/injection/Inject;".equals(a.desc)||a.values==null)continue;
            for(int i=0;i<a.values.size();i+=2){
                String key=(String)a.values.get(i);Object value=a.values.get(i+1);
                if("at".equals(key)&&value instanceof List<?> list){
                    for(Object o:list)if(o instanceof AnnotationNode at&&at.values!=null){
                        for(int j=0;j<at.values.size();j+=2)if("value".equals(at.values.get(j))){at.values.set(j+1,"HEAD");changed=true;}
                    }
                }else if("cancellable".equals(key)){a.values.set(i+1,Boolean.FALSE);}
            }
        }
        if(!changed)throw new IllegalStateException("Inject @At value not changed");write(p,n);
    }

    static void patchBuildInfo(Path p)throws Exception{
        ClassNode n=read(p);boolean v=false,r=false;
        for(MethodNode m:n.methods)if(m.name.equals("<clinit>"))for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof LdcInsnNode l&&l.cst instanceof String s){
            if(s.equals("0.2.0-alpha.27-corpus4-local.17-rev233-local-test.1")){l.cst="0.2.0-alpha.27-corpus4-local.18-rev234-local-test.1";v=true;}
            else if(s.equals("2026-10-01.233-armor-hud-liquid-semantics")){l.cst="2026-10-01.234-native-armor-attribute-source-modifier-visibility";r=true;}
        }
        if(!v||!r)throw new IllegalStateException("BuildInfo anchors "+v+"/"+r);write(p,n);
    }
}
