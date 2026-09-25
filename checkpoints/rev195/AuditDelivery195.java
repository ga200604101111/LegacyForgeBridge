import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import com.google.gson.*;
/** Targeted ABI audit; not a replacement for full Minecraft linkage checks. */
public final class AuditDelivery195 implements Opcodes {
 public static void main(String[] args)throws Exception {
  int classes=0,properties=0,clocks=0,changedClasses=0,changedMethods=0;
  var failures=new ArrayList<String>();
  try(var jar=new JarFile(args[0])){
   for(var e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")){
    ClassNode n=new ClassNode();new ClassReader(jar.getInputStream(e).readAllBytes()).accept(n,0);classes++;
    if(n.name.equals("dev/yinghuang/legacyforgebridge/BuildInfo")){
     for(var f:n.fields)if(Set.of("VERSION","CONVERTER_REVISION","CONVERSION_SCHEMA").contains(f.name)&&f.value!=null)failures.add("Inlined BuildInfo constant "+f.name);
    }
    boolean changed=Files.exists(Path.of(args[1]).resolve(e.getName()));if(changed)changedClasses++;
    for(var m:n.methods){
     if(changed&&(m.access&(ACC_ABSTRACT|ACC_NATIVE))==0){new Analyzer<>(new BasicVerifier()).analyze(n.name,m);changedMethods++;}
     for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.startsWith("net/minecraft/")){
      if(c.name.equals("method_28498")){properties++;if(!c.desc.equals("(Lnet/minecraft/class_2769;)Z"))failures.add(n.name+"."+m.name+" invalid Property "+c.desc);}
      if(c.name.equals("method_8510"))failures.add(n.name+"."+m.name+" obsolete world clock "+c.owner);
      if(c.name.equals("method_75260")&&c.desc.equals("()J"))clocks++;
     }
    }
   }
  }
  if(!failures.isEmpty())throw new AssertionError(String.join("\n",failures));
  if(properties!=14||clocks!=3||classes!=1439||changedClasses!=3)throw new AssertionError("Unexpected audit coverage: "+classes+"/"+properties+"/"+clocks+"/"+changedClasses);
  var report=new LinkedHashMap<String,Object>();report.put("classes_scanned",classes);report.put("property_presence_calls_checked",properties);report.put("current_world_clock_calls_checked",clocks);report.put("invalid_targeted_calls",0);report.put("asm_checked_changed_classes",changedClasses);report.put("asm_checked_changed_methods",changedMethods);report.put("scope","Targeted two-family ABI scan; not universal Minecraft member/linkage verification");
  System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(report));
 }
}
