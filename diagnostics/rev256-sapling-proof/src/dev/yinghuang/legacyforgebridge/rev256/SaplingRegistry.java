package dev.yinghuang.legacyforgebridge.rev256;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.slf4j.LoggerFactory;
/** Reads proof DATA, never depends on stripped legacy JVM classes. */
public final class SaplingRegistry {
 public record Rule(SaplingProof.Candidate proof,class_265 selection,String source,String origin,String hash){}
 private static volatile Map<String,Rule> rules;
 private static final Set<String> installed=new HashSet<>();
 public static synchronized void discover(){
  if(rules!=null)return;
  Map<String,Rule> all=new LinkedHashMap<>();Set<String> conflicted=new HashSet<>();
  for(var mod:FabricLoader.getInstance().getAllMods()){
   var manifest=mod.findPath("legacyforgebridge/converted-content.json");if(manifest.isEmpty())continue;
   try(Reader r=Files.newBufferedReader(manifest.get(),StandardCharsets.UTF_8)){
    JsonObject root=JsonParser.parseReader(r).getAsJsonObject();
    if(!root.has("sourceSha256")||!root.has("blocks"))continue;
    String hash=root.get("sourceSha256").getAsString().toLowerCase(Locale.ROOT);
    if(!hash.matches("[0-9a-f]{64}"))continue;
    Map<String,String> ids=new LinkedHashMap<>();
    for(JsonElement e:root.getAsJsonArray("blocks")){
     if(!e.isJsonObject())continue;JsonObject b=e.getAsJsonObject();
     if(b.has("id")&&b.has("sourceClass"))ids.put(b.get("id").getAsString(),b.get("sourceClass").getAsString());
    }
    Properties p=new Properties();String origin;var data=mod.findPath(SaplingProof.PATH);
    if(data.isPresent()){
     origin="candidate-proof";try(Reader in=Files.newBufferedReader(data.get(),StandardCharsets.UTF_8)){p.load(in);}
    }else{
     origin="bundled-source-proof:"+hash.substring(0,12);
     try(InputStream in=SaplingRegistry.class.getResourceAsStream("/legacyforgebridge/sapling-evidence/"+hash+".properties")){
      if(in==null)continue;p.load(new InputStreamReader(in,StandardCharsets.UTF_8));
     }
    }
    for(var e:SaplingProof.verified(p,hash,ids).entrySet()){
     if(conflicted.contains(e.getKey()))continue;
     SaplingProof.Box b=e.getValue().selection();
     class_265 shape=b==null?null:class_259.method_1081(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
     Rule rule=new Rule(e.getValue(),shape,ids.get(e.getKey()),origin,hash);
     Rule prior=all.putIfAbsent(e.getKey(),rule);
     if(prior!=null&&!prior.proof().equals(rule.proof())){all.remove(e.getKey());conflicted.add(e.getKey());}
    }
   }catch(Exception e){LoggerFactory.getLogger("LFB-rev256").warn("Sapling proof rejected; unproven blocks unchanged",e);}
  }
  rules=Map.copyOf(all);
  for(var e:rules.entrySet())LoggerFactory.getLogger("LFB-rev256").info("Sapling proof loaded: "+e.getKey()+"; "+e.getValue().origin()+"; "+e.getValue().proof());
 }
 public static Rule rule(class_2960 id){if(rules==null)discover();return id==null?null:rules.get(id.toString());}
 public static class_4970.class_2251 properties(class_2960 id,class_4970.class_2251 p){
  Rule r=rule(id);if(r==null)return p;
  if(r.selection()!=null)p=p.method_9624();
  if(r.proof().cutout())p=p.method_22488();
  // Do NOT call noCollision when a source-owned collision override was found.
  if(r.proof().emptyCollision())p=p.method_9634();
  return p;
 }
 public static synchronized void install(){
  discover();
  for(var e:rules.entrySet())if(e.getValue().proof().cutout()){
   if(installed.contains(e.getKey()))continue;
   class_2960 id=class_2960.method_60654(e.getKey());
   Object obj=class_7923.field_41175.method_63535(id);
   if(obj instanceof dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock block){
    BlockRenderLayerMap.putBlock(block,class_11515.field_60925);installed.add(e.getKey());
    LoggerFactory.getLogger("LFB-rev256").info("Sapling CUTOUT installed: "+id+"; carrier="+block.getClass().getName());
   }else LoggerFactory.getLogger("LFB-rev256").warn("Sapling carrier missing or specialized; not changing render layer: "+id);
  }
 }
 public static boolean layerInstalled(String id){return installed.contains(id);}
 public static int size(){discover();return rules.size();}
 private SaplingRegistry(){}
}
