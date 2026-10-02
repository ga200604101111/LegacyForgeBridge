import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.io.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;import jdk.internal.org.objectweb.asm.util.*;
/** Checks actual delivered bytecode and preserves all unrelated core method bodies. */
public class AuditPatches {
 static final String B="dev/yinghuang/legacyforgebridge/behavior/";
 static ClassNode read(ZipFile z,String n)throws Exception {ClassNode c=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(n+".class")).readAllBytes()).accept(c,0);return c;}
 static String body(MethodNode m){Textifier t=new Textifier();m.accept(new TraceMethodVisitor(t));StringWriter s=new StringWriter();t.print(new PrintWriter(s));return s.toString();}
 static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
 public static void main(String[] args)throws Exception {
  int compared=0,checked=0;try(ZipFile old=new ZipFile(args[0]);ZipFile now=new ZipFile(args[1])) {
   for(String name:List.of("LegacyBehaviorRuntime","LegacyBehaviorRuntime$Snapshot","LegacyClientJumpMotion")) {
    ClassNode a=read(old,B+name),b=read(now,B+name);
    for(MethodNode m:a.methods) {
     MethodNode n=method(b,m.name.equals("fall")?"lfb$fallBeforeRev242":m.name,m.desc);
     if(m.name.equals("runEvent")) for(var i:n.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals(B+"Rev242LandingBridge")&&c.name.equals("sourceParticles")) {
      var prev=i.getPrevious();if(prev.getOpcode()!=Opcodes.DUP)throw new AssertionError("diagnostic stack");n.instructions.remove(prev);n.instructions.remove(i);
     }
     if(m.name.equals("fill")) for(var i:n.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals(B+"Rev242LandingBridge")&&c.name.equals("sourceY")) {
      var getY=i.getPrevious();var dup=getY.getPrevious();if(dup.getOpcode()!=Opcodes.DUP)throw new AssertionError("posY stack");n.instructions.remove(dup);n.instructions.remove(i);
     }
     if(m.name.equals("initialize")) {
      String oldReady=null;for(var i:m.instructions)if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.startsWith("LFB source motion compatibility READY"))oldReady=s;
      for(var i:n.instructions)if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.startsWith("LFB source motion compatibility READY"))l.cst=oldReady;
     }
     // COMPUTE_MAXS may change only max stack, not frames or semantic instructions.
     n.maxStack=m.maxStack;n.maxLocals=m.maxLocals;
     if(!body(m).equals(body(n)))throw new AssertionError("Unexpected core change: "+name+"."+m.name+m.desc);
     compared++;
    }
   }
   for(var entries=now.entries();entries.hasMoreElements();) {
    var e=entries.nextElement();if(!e.getName().endsWith(".class"))continue;
    if(e.getName().startsWith(B+"Rev241")||e.getName().startsWith(B+"Rev242")||e.getName().equals(B+"LegacyBehaviorRuntime.class")||e.getName().equals(B+"LegacyBehaviorRuntime$Snapshot.class")) {
     new ClassReader(now.getInputStream(e).readAllBytes()).accept(new CheckClassAdapter(new ClassWriter(0),false),0);checked++;
    }
   }
  }
  System.out.println("AUDIT coreMethodsPreserved="+compared+" structuralClassChecks="+checked+" actualPackagedBytecode=true");
 }
}
