
package dev.yinghuang.legacyforgebridge.repair322;
import java.nio.file.*;import java.util.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
import static org.objectweb.asm.Opcodes.*;
/** Offline surgical patcher; preserves existing Hotfix2 methods. */
public final class PatchHooks322 {
 static final String ROOT="dev/yinghuang/legacyforgebridge/";
 static final String R=ROOT+"repair322/VisualRuntime322",P=ROOT+"repair322/CoreVisualPass322";
 public static void main(String[] a)throws Exception {
  if(a.length!=3)throw new IllegalArgumentException("kind input.class output.class");
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(a[1]))).accept(c,0);int count=0;
  for(MethodNode m:c.methods){
   if(a[0].equals("bootstrap")&&m.name.equals("find")&&m.desc.equals("(Ljava/nio/file/Path;)Ljava/util/List;")){for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==ARETURN){m.instructions.insertBefore(ins,new MethodInsnNode(INVOKESTATIC,ROOT+"bootstrap/DependencyGuard322","filter","(Ljava/util/List;)Ljava/util/List;",false));count++;}}

   if(a[0].equals("converter")){for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&(call.owner.equals(ROOT+"desktop/DesktopConversionSession")||call.owner.equals(ROOT+"bootstrap/CoreProgress"))&&call.name.equals("applyFeedback")){
     AbstractInsnNode prev=ins.getPrevious();while(prev!=null&&prev.getOpcode()<0)prev=prev.getPrevious();if(!(prev instanceof VarInsnNode load)||load.getOpcode()!=ALOAD)throw new IllegalArgumentException("context load not adjacent");
     InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,load.var));add.add(new MethodInsnNode(INVOKESTATIC,P,"apply","(L"+ROOT+"convert/api/ConversionContext;)V",false));m.instructions.insert(ins,add);count++;
   }}
   if(a[0].equals("block")&&(m.name.equals("method_9530")||m.name.equals("method_9549"))&&m.desc.startsWith("(Lnet/minecraft/class_2680;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;")){
    for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==ARETURN){InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,1));add.add(new VarInsnNode(ALOAD,2));add.add(new VarInsnNode(ALOAD,3));add.add(new InsnNode(m.name.equals("method_9549")?ICONST_1:ICONST_0));add.add(new MethodInsnNode(INVOKESTATIC,R,"shape","(Lnet/minecraft/class_265;Lnet/minecraft/class_2680;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Z)Lnet/minecraft/class_265;",false));m.instructions.insertBefore(ins,add);count++;}
   }
   if(a[0].equals("properties")&&m.name.equals("properties")&&m.desc.equals("(Ljava/lang/String;Lnet/minecraft/class_4970$class_2251;)Lnet/minecraft/class_4970$class_2251;")){
    for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==ARETURN){InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,0));add.add(new InsnNode(SWAP));add.add(new MethodInsnNode(INVOKESTATIC,R,"properties",m.desc,false));m.instructions.insertBefore(ins,add);count++;}
   }
  }
  if(count==0||(a[0].equals("converter")&&count!=1))throw new IllegalArgumentException("unexpected hook count "+count);
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);byte[] binary=writer.toByteArray();ClassNode check=new ClassNode();new ClassReader(binary).accept(check,0);
  for(MethodNode m:check.methods)if(m.instructions.size()>0)new Analyzer<BasicValue>(new BasicInterpreter()).analyze(check.name,m);
  Files.write(Path.of(a[2]),binary);System.out.println("HOOKS "+a[0]+" class="+c.name+" count="+count+" analyzedMethods="+check.methods.size());
 }
}
