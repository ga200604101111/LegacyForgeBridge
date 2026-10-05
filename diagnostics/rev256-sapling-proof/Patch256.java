import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;
/** Offline binary overlay; not packaged as a game hook. */
public final class Patch256 implements Opcodes {
 public static void main(String[] a)throws Exception{
  byte[] in=Files.readAllBytes(Path.of(a[0]));ClassNode c=new ClassNode(ASM8);new ClassReader(in).accept(c,0);int count=0;
  for(MethodNode m:c.methods)if(m.name.equals("apply")&&m.desc.equals("(Ldev/yinghuang/legacyforgebridge/convert/api/ConversionContext;)V")){
   for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode call && call.owner.equals("dev/yinghuang/legacyforgebridge/rev256/SaplingProofCompiler"))throw new IllegalStateException("Already patched");
   InsnList pre=new InsnList();pre.add(new VarInsnNode(ALOAD,1));pre.add(new MethodInsnNode(INVOKESTATIC,"dev/yinghuang/legacyforgebridge/rev256/SaplingProofCompiler","capture",m.desc,false));m.instructions.insert(pre);count++;
  }
  if(count!=1)throw new IllegalStateException("Expected one exact apply method, got "+count);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);byte[] out=w.toByteArray();
  ClassNode check=new ClassNode(ASM8);new ClassReader(out).accept(check,0);
  for(MethodNode m:check.methods)if((m.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<>(new BasicVerifier()).analyze(check.name,m);
  Files.write(Path.of(a[1]),out);System.out.println("PASS source-strip entry hook, verified all methods; no gameplay patch");
 }
}
