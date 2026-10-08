import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Audited rev292 -> rev293 only: add one pass, enable source-proven armor slot, bump version. */
public final class Rev293PatchBinaries {
 static final String R="dev/yinghuang/legacyforgebridge/convert/";
 static final String P=R+"pass/";
 static final String PROFILE=R+"profile/GenericLegacyModProfile.class";
 static final String SUPPORT=R+"runtime/GeneratedModSupport.class";
 static final String BUILD="dev/yinghuang/legacyforgebridge/BuildInfo.class";
 static final String BUILDER=R+"api/ConversionPlan$Builder";
 static final String ADD="(L"+R+"api/ConversionPass;)L"+BUILDER+";";
 static byte[] patchProfile(byte[] original){
  ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(original).accept(node,0);
  MethodNode method=node.methods.stream().filter(m->m.name.equals("configure")&&m.desc.equals("(L"+BUILDER+";)V")).findFirst().orElseThrow();
  AbstractInsnNode anchor=null;int oldCount=0;
  for(AbstractInsnNode insn:method.instructions){
   if(insn instanceof TypeInsnNode t && t.getOpcode()==Opcodes.NEW){
    if(t.desc.equals(P+"LegacyBatchVisualRecoveryPass"))oldCount++;
    if(t.desc.equals(P+"LegacyItemSemanticsRecoveryPass"))throw new IllegalStateException("Already installed rev293");
   }
   if(insn instanceof MethodInsnNode call && call.owner.equals(P+"LegacyBatchVisualRecoveryPass")&&call.name.equals("<init>")){
    AbstractInsnNode next=insn.getNext();
    while(next!=null && next.getOpcode()<0)next=next.getNext();
    if(!(next instanceof MethodInsnNode add)||!add.owner.equals(BUILDER)||!add.name.equals("add")||!add.desc.equals(ADD))throw new IllegalStateException("Unexpected profile shape");
    AbstractInsnNode pop=next.getNext();
    while(pop!=null&&pop.getOpcode()<0)pop=pop.getNext();
    if(pop==null||pop.getOpcode()!=Opcodes.POP)throw new IllegalStateException("Expected profile builder pop");
    anchor=pop;
   }
  }
  if(oldCount!=1||anchor==null)throw new IllegalStateException("Expected exactly one rev292 pass");
  InsnList extra=new InsnList();
  extra.add(new VarInsnNode(Opcodes.ALOAD,1));
  extra.add(new TypeInsnNode(Opcodes.NEW,P+"LegacyItemSemanticsRecoveryPass"));
  extra.add(new InsnNode(Opcodes.DUP));
  extra.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,P+"LegacyItemSemanticsRecoveryPass","<init>","()V",false));
  extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,BUILDER,"add",ADD,false));
  extra.add(new InsnNode(Opcodes.POP));
  method.instructions.insert(anchor,extra);
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
 }
 static byte[] patchSupport(byte[] original){
  ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(original).accept(node,0);
  MethodNode method=node.methods.stream().filter(m->m.name.equals("registerItem")&&m.desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IFFF)V")).findFirst().orElseThrow();
  AbstractInsnNode match=null;int candidates=0;
  for(AbstractInsnNode insn:method.instructions)if(insn instanceof VarInsnNode put&&put.getOpcode()==Opcodes.ISTORE&&put.var==15){
    AbstractInsnNode next=insn.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
    AbstractInsnNode after=next==null?null:next.getNext();while(after!=null&&after.getOpcode()<0)after=after.getNext();
    if(next instanceof VarInsnNode get && get.getOpcode()==Opcodes.ILOAD&&get.var==15 && after instanceof TableSwitchInsnNode){match=insn;candidates++;}
  }
  if(candidates!=1)throw new IllegalStateException("Expected exactly one 1.21.11 armor slot selection, found "+candidates);
  InsnList extra=new InsnList();
  extra.add(new VarInsnNode(Opcodes.ALOAD,1)); // source-verified kind from generated registry metadata
  extra.add(new VarInsnNode(Opcodes.ILOAD,15)); // any prior behavior proof preserved
  extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,R+"runtime/LegacySourceArmorSlotFallback","choose","(Ljava/lang/String;I)I",false));
  extra.add(new VarInsnNode(Opcodes.ISTORE,15));
  method.instructions.insert(match,extra);
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
 }
 static byte[] patchBuild(byte[] original){
  ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(original).accept(node,0);
  MethodNode init=node.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
  int changed=0;
  for(AbstractInsnNode insn:init.instructions)if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String str){
   if(str.equals("0.2.0-alpha.27-corpus4-local.45-rev292-batch-visual-prism-watchdog-preview.1")){
    ldc.cst="0.2.0-alpha.27-corpus4-local.46-rev293-item-localization-equipment-preview.1";changed++;
   }else if(str.equals("2026-10-08.292-batch-source-visual-prism-watchdog")){
    ldc.cst="2026-10-08.293-item-localization-equipment-source";changed++;
   }
  }
  if(changed!=2)throw new IllegalStateException("Unexpected BuildInfo previous version");
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
 }
 public static void main(String[] args)throws Exception {
  Path old=Path.of(args[0]),root=Path.of(args[1]);
  try(JarFile jar=new JarFile(old.toFile())) {
   for(String path:List.of(PROFILE,SUPPORT,BUILD)) {
    byte[] bytes=jar.getInputStream(jar.getJarEntry(path)).readAllBytes();
    byte[] newBytes=path.equals(PROFILE)?patchProfile(bytes):path.equals(SUPPORT)?patchSupport(bytes):patchBuild(bytes);
    Path target=root.resolve(path);Files.createDirectories(target.getParent());Files.write(target,newBytes);
    System.out.println("PATCHED "+path+" "+bytes.length+" -> "+newBytes.length);
   }
  }
 }
}
