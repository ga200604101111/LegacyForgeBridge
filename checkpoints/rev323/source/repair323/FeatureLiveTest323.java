
package dev.yinghuang.legacyforgebridge.repair322;
import com.google.gson.*;
import net.minecraft.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import java.nio.file.*;import java.util.*;
/** Opt-in client-only update regression test. Does not send packets or alter a server/save. */
public final class FeatureLiveTest323 {
 static final Path ROOT=FabricLoader.getInstance().getGameDir().resolve("lfb");
 static class_638 target;static LinkedHashMap<class_2338,class_2680> saved;static long due;static int phase;static String id;static class_2338 origin;static long changeStart,rebuildStart;static JsonObject report;
 public static void register(){ClientTickEvents.END_CLIENT_TICK.register(client->{try{step(client);}catch(Throwable ex){try{restore();}finally{phase=0;saved=null;target=null;}try{Files.writeString(ROOT.resolve("logs/generic323-live-error.txt"),ex.toString());Files.deleteIfExists(ROOT.resolve("generic323-live.request"));}catch(Exception ignored){}System.err.println("[LFB323] CLIENT_UPDATE_TEST_FAILED "+ex);}});}
 static void restore(){if(target!=null&&saved!=null)for(var e:saved.entrySet())target.method_8652(e.getKey(),e.getValue(),3);}
 static void step(class_310 c)throws Exception {
  if(phase>0){if(c.field_1687!=target){restore();throw new IllegalStateException("World changed during local test");}if(FeatureClient323.tick<due)return;
   if(phase==1){restore();phase=2;due=FeatureClient323.tick+12;return;}
   if(phase==2){JsonArray counts=new JsonArray();int vertices=0;for(var e:saved.entrySet()){class_2680 now=target.method_8320(e.getKey());if(!now.equals(e.getValue()))throw new IllegalStateException("Client state not restored");class_1087 m=c.method_1541().method_3349(now);MutableMesh mesh=Renderer.get().mutableMesh();((FabricBlockStateModel)m).emitQuads(mesh.emitter(),target,e.getKey(),now,class_5819.method_43047(),d->false);int[] n={0};mesh.forEach(q->{n[0]++;for(int v=0;v<4;v++)if(!Float.isFinite(q.u(v))||!Float.isFinite(q.v(v)))throw new IllegalStateException("Nonfinite grid UV");});counts.add(n[0]);vertices+=n[0];mesh.clear();}
    int expectedFaces=6*VisualRuntime322.GRIDS.get(id).size()*VisualRuntime322.GRIDS.get(id).size();if(vertices!=expectedFaces)throw new IllegalStateException("Unexpected exterior face count: "+vertices+" expected "+expectedFaces);report.addProperty("expectedExteriorQuads",expectedFaces);if(FeatureClient323.incomingChanges<=changeStart||FeatureClient323.regionRebuilds<=rebuildStart)throw new IllegalStateException("Incoming change hooks or queued rebuild not exercised");
    report.addProperty("passed",true);report.addProperty("blocksRestored",saved.size());report.addProperty("visibleQuads",vertices);report.addProperty("changeNotifications",FeatureClient323.incomingChanges-changeStart);report.addProperty("coalescedRebuilds",FeatureClient323.regionRebuilds-rebuildStart);report.add("quadsPerCell",counts);report.addProperty("completedAt",java.time.Instant.now().toString());writeReport();System.out.println("[LFB323] CLIENT_UPDATE_TEST_PASS "+id+" restored="+saved.size()+" quads="+vertices);saved=null;target=null;phase=0;return;}
  }
  Path request=ROOT.resolve("generic323-live.request");if(!Files.exists(request)||c.field_1687==null||c.field_1724==null||c.method_18506()!=null)return;
  int x=(int)Math.floor(c.field_1724.method_23317()),y=(int)Math.floor(c.field_1724.method_23318()),z=(int)Math.floor(c.field_1724.method_23321());
  class_638 world=c.field_1687;class_2338 found=null;String selected=null;
  outer:for(int radius=0;radius<=16;radius++)for(int dy=-8;dy<=8;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;class_2338 p=new class_2338(x+dx,y+dy,z+dz);String key=class_7923.field_41175.method_10221(world.method_8320(p).method_26204()).toString();if(VisualRuntime322.GRIDS.containsKey(key)){found=p;selected=key;break outer;}}
  if(found==null){Files.writeString(ROOT.resolve("logs/generic323-live-wait.txt"),"No existing giant structure within 16 blocks; no changes made.");Files.delete(request);return;}
  int size=VisualRuntime322.GRIDS.get(selected).size();origin=new class_2338(Math.floorDiv(found.method_10263(),size)*size,Math.floorDiv(found.method_10264(),size)*size,Math.floorDiv(found.method_10260(),size)*size);LinkedHashMap<class_2338,class_2680> snapshot=new LinkedHashMap<>();
  for(int dx=0;dx<size;dx++)for(int dy=0;dy<size;dy++)for(int dz=0;dz<size;dz++){class_2338 p=new class_2338(origin.method_10263()+dx,origin.method_10264()+dy,origin.method_10260()+dz);class_2680 s=world.method_8320(p);if(!class_7923.field_41175.method_10221(s.method_26204()).toString().equals(selected)){Files.writeString(ROOT.resolve("logs/generic323-live-wait.txt"),"Found an incomplete giant structure; did not change it.");Files.delete(request);return;}if(world.method_8321(p)!=null)throw new IllegalStateException("Refusing to modify block entities");snapshot.put(p,s);}
  Files.delete(request);target=world;saved=snapshot;id=selected;changeStart=FeatureClient323.incomingChanges;rebuildStart=FeatureClient323.regionRebuilds;
  report=new JsonObject();report.addProperty("type","client-only live loaded-world remove/restore update regression; no outgoing packet or server modification");report.addProperty("id",id);report.addProperty("serverPlacementTested",false);report.addProperty("sourceCells",snapshot.size());report.addProperty("origin",origin.toString());report.addProperty("startedAt",java.time.Instant.now().toString());
  for(var p:snapshot.keySet())target.method_8652(p,class_2246.field_10124.method_9564(),3);
  phase=1;due=FeatureClient323.tick+4;
 }
 static void writeReport()throws Exception{Files.createDirectories(ROOT.resolve("logs"));Files.writeString(ROOT.resolve("logs/generic323-live.json"),CoreVisualPass322.G.toJson(report));}
}
