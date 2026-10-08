import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Audited, bounded rev260/rev291 helper bytecode correction; not a decompiled replacement. */
public final class PatchDesktopHelperRestart {
    private static final String OWNER="dev/yinghuang/legacyforgebridge/desktop/DesktopHelper";
    private static final String SWING="javax/swing/SwingUtilities";
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Usage: <original DesktopHelper.class> <new path>");
        byte[] original=Files.readAllBytes(Path.of(args[0]));
        ClassNode cls=new ClassNode(Opcodes.ASM9);
        new ClassReader(original).accept(cls,0);
        if(!OWNER.equals(cls.name))throw new IllegalStateException("Not DesktopHelper");
        MethodNode poll=null;
        for(MethodNode m:cls.methods)if(m.name.equals("poll")&&m.desc.equals("()V"))poll=m;
        if(poll==null)throw new IllegalStateException("Missing original poll()");
        int wait=0,timeout=0;
        for(AbstractInsnNode node:poll.instructions){
          if(node instanceof LdcInsnNode ldc && ldc.cst instanceof Long l){
             if(l==1500L){ldc.cst=7000L;wait++;}
             if(l==180000L){ldc.cst=90000L;timeout++;}
          }
        }
        if(wait!=1||timeout!=1)throw new IllegalStateException("Unexpected parent-wait/watchdog constants: "+wait+"/"+timeout);
        List<AbstractInsnNode> insns=new ArrayList<>();
        for(AbstractInsnNode n:poll.instructions)if(n.getOpcode()>=0)insns.add(n);
        boolean found=false;
        for(int i=0;i+6<insns.size();i++){
          AbstractInsnNode a=insns.get(i),b=insns.get(i+1),c=insns.get(i+2),d=insns.get(i+3),e=insns.get(i+4),f=insns.get(i+5),g=insns.get(i+6);
          if(a.getOpcode()!=Opcodes.ALOAD || !(a instanceof VarInsnNode v) || v.var!=0
                  || b.getOpcode()!=Opcodes.ICONST_1 || !(c instanceof FieldInsnNode field)
                  || c.getOpcode()!=Opcodes.PUTFIELD || !OWNER.equals(field.owner)
                  || !"closed".equals(field.name)||!"Z".equals(field.desc)
                  || d.getOpcode()!=Opcodes.ALOAD || !(d instanceof VarInsnNode vd)||vd.var!=0
                  || !(e instanceof InvokeDynamicInsnNode)
                  || !(f instanceof MethodInsnNode call) || !SWING.equals(call.owner)
                  || !"invokeLater".equals(call.name) || g.getOpcode()!=Opcodes.RETURN)continue;
          // Only patch the immediate close after LAUNCH_SENT, never the genuine ready/cancel paths.
          boolean proof=false;
          for(int j=Math.max(0,i-12);j<i;j++) if(insns.get(j) instanceof LdcInsnNode text && "LAUNCH_SENT".equals(text.cst))proof=true;
          if(!proof||found)throw new IllegalStateException("Ambiguous immediate-launch closure");
          InsnList replacement=new InsnList();
          replacement.add(new VarInsnNode(Opcodes.ALOAD,0));
          replacement.add(new VarInsnNode(Opcodes.ALOAD,1));
          replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,OWNER,"show","(Ljava/util/Properties;)V",false));
          poll.instructions.insertBefore(a,replacement);
          // Keep original RETURN. Remove the ALOAD0 / true / PUTFIELD closed and dispose frame call.
          for(AbstractInsnNode delete:List.of(a,b,c,d,e,f))poll.instructions.remove(delete);
          found=true;
          break;
        }
        if(!found)throw new IllegalStateException("Expected early close after launch not found");
        ClassWriter writer=new ClassWriter(0);cls.accept(writer);
        byte[] patched=writer.toByteArray();
        ClassNode check=new ClassNode(Opcodes.ASM9);new ClassReader(patched).accept(check,0);
        MethodNode after=check.methods.stream().filter(m->m.name.equals("poll")&&m.desc.equals("()V")).findFirst().orElseThrow();
        boolean hasWatchdog=false,hasShow=false;
        for(AbstractInsnNode n:after.instructions){
            if(n instanceof LdcInsnNode ldc && Long.valueOf(90000L).equals(ldc.cst))hasWatchdog=true;
            if(n instanceof MethodInsnNode m && OWNER.equals(m.owner)&&"show".equals(m.name))hasShow=true;
        }
        if(!hasWatchdog||!hasShow)throw new IllegalStateException("Patched helper failed source audit");
        Path out=Path.of(args[1]);Files.createDirectories(out.toAbsolutePath().getParent());Files.write(out,patched);
        System.out.println("PATCHED_POLL: retained watchdog; no early close; parent grace=7000ms, no-start manual=90000ms; "+original.length+" -> "+patched.length+" bytes");
    }
}
