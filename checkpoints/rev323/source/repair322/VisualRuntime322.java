
package dev.yinghuang.legacyforgebridge.repair322;
import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.runtime.*;
import dev.yinghuang.legacyforgebridge.repair322.VisualSupport322.*;
import net.fabricmc.loader.api.*;
import net.minecraft.*;
import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.*;
/** Read-only runtime for converter-generated, source-derived visual programs. */
public final class VisualRuntime322 {
 public static final Map<String,Plant> PLANTS=new ConcurrentHashMap<>();
 public static final Map<String,Grid> GRIDS=new ConcurrentHashMap<>();
 private static boolean loaded;
 public record Instance(Block block,Drawer drawer){}
 public static final class Plant {
  public final String id;public final Map<Integer,String[]> icons;public final Set<String> textures;public final ThreadLocal<Instance> instances;
  Plant(String id,JsonObject r)throws Exception {this.id=id;icons=new TreeMap<>();Set<String> t=new HashSet<>();for(var e:r.getAsJsonObject("icons").entrySet()){String[] a=new Gson().fromJson(e.getValue(),String[].class);icons.put(Integer.parseInt(e.getKey()),a);t.addAll(Arrays.asList(a));}if(r.has("textures"))for(var v:r.getAsJsonArray("textures"))t.add(v.getAsString());textures=Set.copyOf(t);
   var b=Class.forName(r.get("blockProgram").getAsString()).asSubclass(Block.class).getConstructor();var d=Class.forName(r.get("rendererProgram").getAsString()).asSubclass(Drawer.class).getConstructor();
   instances=ThreadLocal.withInitial(()->{try{Block bb=b.newInstance();icons.forEach((m,a)->bb.icons[m]=a.clone());return new Instance(bb,d.newInstance());}catch(Exception ex){throw new IllegalStateException(ex);}});
  }
 }
 public record Grid(String id,int size,GridProgram program){}
 public static synchronized void load(){if(loaded)return;loaded=true;
  for(ModContainer mod:FabricLoader.getInstance().getAllMods())try{var path=mod.findPath("legacyforgebridge/visual-programs322.json");if(path.isEmpty()||ConvertedModCatalog.isMarkedStale(mod.getMetadata().getId()))continue;
   JsonObject root=JsonParser.parseString(Files.readString(path.get())).getAsJsonObject();if(root.get("schemaVersion").getAsInt()!=1||!ConvertedModCatalog.loadedSourceSha256(mod.getMetadata().getId()).orElse("").equals(root.get("sourceSha256").getAsString()))throw new IllegalArgumentException("visual source hash mismatch");
   for(var e:root.getAsJsonObject("plants").entrySet()){JsonObject r=e.getValue().getAsJsonObject();verify(mod,r,"blockProgram","blockSha256");verify(mod,r,"rendererProgram","rendererSha256");PLANTS.put(e.getKey(),new Plant(e.getKey(),r));}
   for(var e:root.getAsJsonObject("grids").entrySet()){JsonObject r=e.getValue().getAsJsonObject();verify(mod,r,"program","programSha256");GridProgram p=Class.forName(r.get("program").getAsString()).asSubclass(GridProgram.class).getConstructor().newInstance();int n=r.get("size").getAsInt();if(n!=4)throw new IllegalArgumentException("unsupported proven grid");GRIDS.put(e.getKey(),new Grid(e.getKey(),n,p));}
  }catch(Exception|LinkageError ex){System.err.println("[LFB visual322] RUNTIME_REJECTED "+mod.getMetadata().getId()+" "+ex);}
  System.out.println("[LFB visual322] RUNTIME_READY plants="+PLANTS.keySet()+" grids="+GRIDS.keySet());
 }
 static void verify(ModContainer mod,JsonObject r,String program,String hash)throws Exception {String n=r.get(program).getAsString();if(!n.startsWith("dev.yinghuang.legacyforgebridge.generated.visual322."))throw new SecurityException("not generated visual program");Path p=mod.findPath(n.replace('.','/')+".class").orElseThrow();String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));if(!digest.equals(r.get(hash).getAsString()))throw new SecurityException("visual bytecode hash mismatch");}
 public static World view(Block host,class_1922 world,class_2338 pos,class_2680 current){return new World(){
  class_2680 state(int x,int y,int z){if(x==pos.method_10263()&&y==pos.method_10264()&&z==pos.method_10260())return current;return world.method_8320(new class_2338(x,y,z));}
  public int func_72805_g(int x,int y,int z){return ConvertedLegacyBlock.legacyMeta(state(x,y,z));}
  public Block func_147439_a(int x,int y,int z){return state(x,y,z).method_26204()==current.method_26204()?host:null;}
 };}
 public static class_265 shape(class_265 fallback,class_2680 state,class_1922 world,class_2338 pos,boolean collision){load();String id=class_7923.field_41175.method_10221(state.method_26204()).toString();Plant p=PLANTS.get(id);if(p==null||!p.icons.containsKey(ConvertedLegacyBlock.legacyMeta(state)))return fallback;if(collision)return class_259.method_1073();try{Block b=p.instances.get().block();b.func_149719_a(view(b,world,pos,state),pos.method_10263(),pos.method_10264(),pos.method_10260());double[] a=b.bounds();return class_259.method_1081(a[0],a[1],a[2],a[3],a[4],a[5]);}catch(RuntimeException ex){return fallback;}}
 public static class_4970.class_2251 properties(String id,class_4970.class_2251 settings){load();if(PLANTS.containsKey(id))settings.method_26250();return settings;}
}
