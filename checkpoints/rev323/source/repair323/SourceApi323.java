
package dev.yinghuang.legacyforgebridge.repair322;
import java.util.*;import org.objectweb.asm.tree.*;import static dev.yinghuang.legacyforgebridge.repair322.CoreVisualPass322.*;
/** Additional vanilla API contracts, isolated from the live game. */
public final class SourceApi323 {
 public static final Object UNHANDLED=new Object();
 public static String staticOwner(Object analyzer,String owner,String name){try{Map<String,ClassNode> classes=(Map)field(analyzer,"classes");String next=owner;Set<String> seen=new HashSet<>();while(classes.containsKey(next)&&seen.add(next)){ClassNode c=classes.get(next);if(c.fields.stream().anyMatch(f->f.name.equals(name)))return next;next=c.superName;}return owner;}catch(Exception e){throw new IllegalStateException(e);}}
 public static Object invokeExtra(Object a,String owner,String name,String desc,Object self,List<Object> args,int opcode,int depth){try{
  Object captured=MeshCapture323.invokeExtra(a,owner,name,desc,self,args,opcode,depth);if(captured!=UNHANDLED)return captured;
  if(self!=null&&self.getClass().getSimpleName().equals("Obj")){String type=(String)field(self,"type");Map<String,Object> fields=(Map)field(self,"fields");
   if(Set.of("setBlockBoundsForItemRender","func_149683_g").contains(name)&&desc.equals("()V")&&owner.startsWith("net/minecraft/block/")&&invoke(a,"find",type,List.of(name),desc)==null){fields.put("$bounds",List.of(0d,0d,0d,1d,1d,1d));return null;}
   if(Set.of("getIcon","func_149691_a").contains(name)&&desc.equals("(II)Lnet/minecraft/util/IIcon;")&&invoke(a,"find",type,List.of(name),desc)==null&&"net/minecraft/block/BlockRotatedPillar".equals(invoke(a,"externalBase",type))){int face=((Number)args.get(0)).intValue(),meta=((Number)args.get(1)).intValue(),axis=meta&12;boolean top=axis==0&&face<2||axis==4&&face>3||axis==8&&(face==2||face==3);Object m=invoke(a,"find",type,top?List.of("getTopIcon","func_150161_d"):List.of("getSideIcon","func_150163_b"),"(I)Lnet/minecraft/util/IIcon;");if(m==null)return UNHANDLED;return invoke(a,"run",m,self,List.of(meta&3),depth+1);}
  }
  return UNHANDLED;
 }catch(Exception e){throw e instanceof RuntimeException r?r:new IllegalStateException(e);}}
}
