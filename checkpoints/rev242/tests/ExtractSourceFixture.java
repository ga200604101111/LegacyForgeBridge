import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.commons.*;
/** Test-only: actual uploaded RPGTool method bodies, type-remapped to the shipped shim API. */
public class ExtractSourceFixture {
 public static void main(String[] a)throws Exception {
  String api="dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorApi$";
  Map<String,String> map=new HashMap<>();
  map.put("mhzd/net/rpgtool1/util/RPGEvent","fixture/RpgEvent");
  map.put("mhzd/net/rpgtool1/armor/WingBase","fixture/WingItem");
  map.put("net/minecraft/entity/Entity",api+"Entity");
  map.put("net/minecraft/entity/EntityLivingBase",api+"Living");
  map.put("net/minecraft/entity/player/EntityPlayer",api+"Player");
  map.put("net/minecraft/world/World",api+"World");
  map.put("net/minecraft/item/Item",api+"Item");
  map.put("net/minecraft/item/ItemStack",api+"Stack");
  map.put("net/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent",api+"Event");
  map.put("net/minecraftforge/event/entity/living/LivingFallEvent",api+"Event");
  ClassNode n=new ClassNode();
  try(ZipFile z=new ZipFile(a[0]);var in=z.getInputStream(z.getEntry("mhzd/net/rpgtool1/util/RPGEvent.class"))) {
   new ClassReader(in.readAllBytes()).accept(n,0);
  }
  n.methods.removeIf(m->!Set.of("<init>","onJump","onFallDown").contains(m.name));
  if(n.methods.size()!=3)throw new AssertionError("Unexpected source event methods");
  for(MethodNode m:n.methods){m.visibleAnnotations=null;m.invisibleAnnotations=null;}
  n.innerClasses.clear();n.visibleAnnotations=null;n.invisibleAnnotations=null;
  ClassWriter w=new ClassWriter(0);
  n.accept(new ClassRemapper(w,new Remapper(){public String map(String name){return map.getOrDefault(name,name);}}));
  Path p=Path.of(a[1],"fixture/RpgEvent.class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
  System.out.println("PASS extracted original onJump/onFallDown bytecode; only test type references remapped");
 }
}
