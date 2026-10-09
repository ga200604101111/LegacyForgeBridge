import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;import java.nio.file.*;import java.util.*;import java.util.jar.*;import java.io.*;
/** Structural check plus dataflow verification; does not pretend to load Minecraft libraries. */
public class VerifyRev312 {
 static final String PREFIX="dev/yinghuang/legacyforgebridge/convert/", SHARED=PREFIX+"shared/";
 static int methods,classes,equivalentMethods;
 static String trace(MethodNode m){Textifier t=new Textifier();m.accept(new TraceMethodVisitor(t));StringWriter s=new StringWriter();t.print(new PrintWriter(s));return s.toString();}
 static Map<String,MethodNode> map(ClassNode c){Map<String,MethodNode> m=new HashMap<>();for(MethodNode n:c.methods)m.put(n.name+n.desc,n);return m;}
 public static void main(String[] args)throws Exception{
  try(JarFile base=new JarFile(args[0])){
   Path root=Path.of(args[1]);
   for(Path p:Files.walk(root).filter(x->x.toString().endsWith(".class")).toList()){
    String name=root.relativize(p).toString().replace('\\','/');ClassNode n=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(n,0);
    classes++;
    for(MethodNode m:n.methods)if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
     new Analyzer<>(new BasicVerifier()).analyze(n.name,m);methods++;
    }
    if(!name.startsWith(PREFIX)||name.startsWith(SHARED)||base.getJarEntry(name)==null)continue;
    ClassNode old=new ClassNode();new ClassReader(base.getInputStream(base.getJarEntry(name))).accept(old,0);
    Map<String,MethodNode> originals=map(old);
    for(MethodNode m:n.methods){
     if(m.name.equals("lfb$rev312AnalyzeUncached"))m.name="analyze";
     else if(m.name.equals("lfb$rev312ClassifyUncached"))m.name="classifyItem";
     else if(m.name.equals("lfb$rev312HiddenUncached"))m.name="hiddenRegistrationKeys";
     else if(m.name.equals("lfb$rev312ConvertUncached"))m.name="convertAnalyzed";
     else if(m.name.startsWith("lfb$rev312"))continue;
     else if(n.name.endsWith("/LegacyRegistryAnalyzer")&&Set.of("analyze","classifyItem","hiddenRegistrationKeys").contains(m.name))continue;
     else if(n.name.endsWith("/LegacyConversionEngine")&&m.name.equals("convertAnalyzed"))continue;
     MethodNode target=originals.get(m.name+m.desc);if(target==null)throw new AssertionError("Unexpected method "+n.name+"#"+m.name);
     for(AbstractInsnNode i=m.instructions.getFirst();i!=null;){AbstractInsnNode next=i.getNext();
      if(i instanceof MethodInsnNode c&&c.owner.equals(SHARED+"SharedSourceSession")){
       if(c.name.equals("openEntry")){c.setOpcode(Opcodes.INVOKEVIRTUAL);c.owner="java/util/jar/JarFile";c.name="getInputStream";c.desc="(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;";}
       else if(c.name.equals("beforePublication"))m.instructions.remove(i);
       else throw new AssertionError("Unknown scope hook "+c.name);
      }i=next;
     }
     // New zero-argument guards and stack-neutral stream calls retain max stack in originals.
     m.maxStack=target.maxStack;m.maxLocals=target.maxLocals;
     if(!trace(m).equals(trace(target)))throw new AssertionError("Algorithm changed: "+n.name+"#"+m.name+m.desc);
     equivalentMethods++;
    }
   }
  }
  System.out.println("PASS classes="+classes+" verifiedMethods="+methods+" originalAlgorithmsEquivalentAfterRemovingHooks="+equivalentMethods);
 }
}
