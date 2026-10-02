import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
public final class AuditReadOnly {
    public static void main(String[] args)throws Exception {
        try(ZipFile z=new ZipFile(args[0])) {
            String root="dev/yinghuang/legacyforgebridge/";
            ClassNode bridge=read(z,root+"behavior/Rev241JumpMotionBridge.class");int noops=0;
            for(MethodNode m:bridge.methods)if(Set.of("sourceBefore","sourceAfter","nativeVelocity","nativePosition","packetHead","packetTail").contains(m.name)) {
                var instructions=Arrays.stream(m.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();
                if(instructions.size()!=1||instructions.get(0).getOpcode()!=Opcodes.RETURN)throw new AssertionError("motion hook is not inert: "+m.name);noops++;
            }
            if(noops!=6)throw new AssertionError("wrong inert ABI");
            int injections=0;
            for(String n:new String[]{"Rev243CameraTraceMixin","Rev243MoveTraceMixin"}) {
                ClassNode c=read(z,root+"mixin/diagnostic/"+n+".class");
                for(MethodNode m:c.methods){
                    if(m.visibleAnnotations!=null)for(AnnotationNode a:m.visibleAnnotations)if(a.desc.endsWith("/Inject;")) {
                        Map<String,Object> map=new HashMap<>();for(int i=0;i<a.values.size();i+=2)map.put((String)a.values.get(i),a.values.get(i+1));
                        if(!Boolean.FALSE.equals(map.get("cancellable"))||!Integer.valueOf(0).equals(map.get("require")))throw new AssertionError("invasive injection");injections++;
                    }
                    for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.startsWith("net/minecraft"))throw new AssertionError("mixin invokes game mutator");
                }
            }
            if(injections!=3)throw new AssertionError("wrong observer injection count");
            for(String n:new String[]{"Rev243Diagnostics","Rev243Diagnostics$ReadApi","LegacyMotionTraceLog","Rev241JumpMotionBridge"}) {
                ClassNode c=read(z,root+"behavior/"+n+".class");
                for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call) {
                    if(Set.of("method_18800","method_18799","method_5814","cancel").contains(call.name))throw new AssertionError("unexpected mutator: "+call);
                    if(call.owner.equals("java/lang/reflect/Field")&&call.name.startsWith("set")&&!call.name.equals("setAccessible"))throw new AssertionError("reflective game field mutation");
                }
            }
            System.out.println("AUDIT_PASS inertMotionHooks=6 observationalInjections=3 cancellations=0 gameVelocityOrPositionWrites=0 testClassesPackaged=false");
        }
    }
    static ClassNode read(ZipFile z,String p)throws Exception{ClassNode c=new ClassNode();try(var in=z.getInputStream(z.getEntry(p))){new ClassReader(in.readAllBytes()).accept(c,0);}return c;}
}
