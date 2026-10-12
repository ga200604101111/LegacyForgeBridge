
package dev.yinghuang.legacyforgebridge.repair322;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.convert.runtime.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
/** Optional once-per-launch read-only model test. Does not create or alter any world. */
public final class VisualSelfTest322 {
 static int ticks;static boolean ran;
 public static void register(){ClientTickEvents.END_CLIENT_TICK.register(client->{if(ran||++ticks<60)return;if(!Boolean.getBoolean("legacyforgebridge.visualSelfTest322")&&!Files.exists(FabricLoader.getInstance().getGameDir().resolve("lfb/visual322-selftest.request"))){ran=true;return;}if(client.method_1554()==null||client.field_1755==null)return;
  try{if(VisualRuntime322.PLANTS.isEmpty())return;String id=VisualRuntime322.PLANTS.keySet().iterator().next();var block=class_7923.field_41175.method_63535(class_2960.method_60654(id));class_1087 test;try{test=client.method_1541().method_3349(ConvertedLegacyBlock.withLegacyMeta(block.method_9564(),3));if(test==null||test==client.method_1554().method_68046())return;}catch(NullPointerException notReady){return;}ran=true;run(client);}catch(Throwable ex){ran=true;System.err.println("[LFB visual322] SELF_TEST_FAILURE "+ex);ex.printStackTrace();}
 });}
 static class_1920 view(class_2680 state,class_2338 pos){return (class_1920)Proxy.newProxyInstance(VisualSelfTest322.class.getClassLoader(),new Class<?>[]{class_1920.class},(o,m,a)->{
  if(m.getName().equals("method_8320"))return pos.equals(a[0])?state:class_2246.field_10124.method_9564();
  if(m.getName().equals("toString"))return "LFBReadOnlyModelTest";
  if(m.getName().equals("hashCode"))return System.identityHashCode(o);if(m.getName().equals("equals"))return o==a[0];
  if(m.getReturnType()==int.class)return m.getName().equals("method_23752")?0xffffff:0;
  if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==float.class)return 1f;
  if(m.getReturnType()==double.class)return 1d;return null;
 });}
 static void run(class_310 client)throws Exception {JsonObject report=new JsonObject();report.addProperty("type","read-only native baked-model test, not live-world gameplay");report.addProperty("time",java.time.Instant.now().toString());JsonArray tests=new JsonArray();
  Set<String> ids=new LinkedHashSet<>(VisualRuntime322.PLANTS.keySet());ids.addAll(VisualRuntime322.GRIDS.keySet());
  for(String id:ids){var block=class_7923.field_41175.method_63535(class_2960.method_60654(id));var metas=VisualRuntime322.PLANTS.containsKey(id)?VisualRuntime322.PLANTS.get(id).icons.keySet():Set.of(0);
   for(int meta:metas){class_2680 state=ConvertedLegacyBlock.withLegacyMeta(block.method_9564(),meta);class_1087 model=client.method_1541().method_3349(state);class_2338 pos=new class_2338(-1017,36,1242);MutableMesh mesh=Renderer.get().mutableMesh();((FabricBlockStateModel)model).emitQuads(mesh.emitter(),view(state,pos),pos,state,class_5819.method_43047(),d->false);int[] count={0};mesh.forEach(q->{count[0]++;for(int i=0;i<4;i++){if(!Float.isFinite(q.x(i))||!Float.isFinite(q.y(i))||!Float.isFinite(q.z(i))||!Float.isFinite(q.u(i))||!Float.isFinite(q.v(i)))throw new IllegalStateException("nonfinite mesh");}});if(VisualClient322.traced.stream().anyMatch(x->x.startsWith("WORLD_GEOMETRY_ERROR")))throw new IllegalStateException("source geometry fallback occurred");if(count[0]==0)throw new IllegalStateException("empty mesh "+id+":"+meta);
    class_1799 stack=new class_1799(block);LegacyStackComponents.set(stack,meta);JsonObject test=new JsonObject();test.addProperty("id",id);test.addProperty("meta",meta);test.addProperty("quads",count[0]);test.addProperty("bakedModelClass",model.getClass().getName());test.addProperty("name",stack.method_7964().getString());String modelId=ConvertedIconModelCatalog.itemModel(id,meta);test.addProperty("itemModel",modelId);var itemModel=client.method_1554().method_65746(class_2960.method_60654(modelId==null?id:modelId));if(itemModel==null)throw new IllegalStateException("missing item model "+id+":"+meta);test.addProperty("itemModelClass",itemModel.getClass().getName());tests.add(test);mesh.clear();
   }
  }report.add("tests",tests);report.addProperty("passed",true);Path path=FabricLoader.getInstance().getGameDir().resolve("lfb/logs/visual322-selftest.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.deleteIfExists(FabricLoader.getInstance().getGameDir().resolve("lfb/visual322-selftest.request"));System.out.println("[LFB visual322] SELF_TEST_PASS count="+tests.size()+" report="+path);
 }
}
