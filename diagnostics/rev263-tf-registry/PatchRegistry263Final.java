import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRegistry263Final {
 static final String R="dev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer.class";
 static final String B="dev/yinghuang/legacyforgebridge/BuildInfo.class";
 static byte[] patchRegistry(byte[] bytes){
  ClassNode c=new ClassNode(); new ClassReader(bytes).accept(c,0);
  int dispatch=0, lambda=0;
  for(MethodNode m:c.methods){
   if(m.name.equals("vanillaBlockDispatch") && m.desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z")){
    for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
     if(!(x instanceof VarInsnNode v) || v.getOpcode()!=Opcodes.ALOAD || v.var!=8) continue;
     AbstractInsnNode a=nextReal(x), z=nextReal(a), ret=nextReal(z);
     if(!(a instanceof JumpInsnNode j) || j.getOpcode()!=Opcodes.IFNONNULL) continue;
     if(z==null||z.getOpcode()!=Opcodes.ICONST_0||ret==null||ret.getOpcode()!=Opcodes.IRETURN) continue;
     InsnList q=new InsnList();
     q.add(new VarInsnNode(Opcodes.ALOAD,7));
     q.add(new LdcInsnNode("net/minecraft/block/Block"));
     q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","startsWith","(Ljava/lang/String;)Z",false));
     q.add(new VarInsnNode(Opcodes.ALOAD,2));
     q.add(new LdcInsnNode("net/minecraft/block/Block"));
     q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
     q.add(new VarInsnNode(Opcodes.ILOAD,6));
     q.add(new InsnNode(Opcodes.IOR));
     q.add(new InsnNode(Opcodes.IAND));
     m.instructions.insertBefore(z,q);
     m.instructions.remove(z);
     dispatch++; break;
    }
   }
   if(m.name.equals("lambda$recoverFieldBindings$1")){
    m.instructions.clear();
    InsnList q=m.instructions;
    q.add(new VarInsnNode(Opcodes.ALOAD,2));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"dev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Registration","kind","()Ldev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Kind;",false));
    q.add(new VarInsnNode(Opcodes.ALOAD,0));
    q.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/Objects","equals","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
    q.add(new VarInsnNode(Opcodes.ALOAD,2));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"dev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Registration","registryName","()Ljava/lang/String;",false));
    q.add(new VarInsnNode(Opcodes.ALOAD,1));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
    q.add(new VarInsnNode(Opcodes.ALOAD,2));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"dev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Registration","registryName","()Ljava/lang/String;",false));
    q.add(new LdcInsnNode("item."));
    q.add(new VarInsnNode(Opcodes.ALOAD,1));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
    q.add(new InsnNode(Opcodes.IOR));
    q.add(new VarInsnNode(Opcodes.ALOAD,2));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"dev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Registration","registryName","()Ljava/lang/String;",false));
    q.add(new LdcInsnNode("tile."));
    q.add(new VarInsnNode(Opcodes.ALOAD,1));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false));
    q.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
    q.add(new InsnNode(Opcodes.IOR));
    q.add(new InsnNode(Opcodes.IAND));
    q.add(new InsnNode(Opcodes.IRETURN));
    m.tryCatchBlocks.clear(); m.localVariables=null; lambda++;
   }
  }
  if(dispatch!=1||lambda!=1) throw new IllegalStateException("registry patch counts "+dispatch+","+lambda);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w); return w.toByteArray();
 }
 static byte[] patchBuild(byte[] bytes){
  ClassNode c=new ClassNode(); new ClassReader(bytes).accept(c,0); int n=0;
  for(MethodNode m:c.methods) if(m.name.equals("<clinit>")) for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext()){
   if(!(x instanceof LdcInsnNode l) || !(l.cst instanceof String s)) continue;
   if(s.equals("0.2.0-alpha.27-corpus4-local.45-rev262-tf-creative.1")){ l.cst="0.2.0-alpha.27-corpus4-local.46-rev263-tf-registry.1"; n++; }
   else if(s.equals("2026-10-07.262-block-creative-allocation-proof")){ l.cst="2026-10-07.263-registry-vanilla-boundary-field-bindings"; n++; }
  }
  if(n!=2) throw new IllegalStateException("build patch count "+n);
  ClassWriter w=new ClassWriter(0); c.accept(w); return w.toByteArray();
 }
 static AbstractInsnNode nextReal(AbstractInsnNode x){ x=x.getNext(); while(x!=null&&(x.getType()==AbstractInsnNode.LABEL||x.getType()==AbstractInsnNode.LINE||x.getType()==AbstractInsnNode.FRAME))x=x.getNext(); return x; }
 public static void main(String[] a)throws Exception{
  Path in=Path.of(a[0]),out=Path.of(a[1]);
  try(JarFile jf=new JarFile(in.toFile()); JarOutputStream jo=jf.getManifest()==null?new JarOutputStream(Files.newOutputStream(out)):new JarOutputStream(Files.newOutputStream(out),jf.getManifest())){
   var en=jf.entries(); while(en.hasMoreElements()){
    JarEntry e=en.nextElement(); if(e.getName().equalsIgnoreCase("META-INF/MANIFEST.MF"))continue;
    byte[] d; try(InputStream is=jf.getInputStream(e)){d=is.readAllBytes();}
    if(e.getName().equals(R))d=patchRegistry(d); else if(e.getName().equals(B))d=patchBuild(d);
    JarEntry ne=new JarEntry(e.getName()); ne.setTime(e.getTime()); jo.putNextEntry(ne);jo.write(d);jo.closeEntry();
   }
  }
 }
}
