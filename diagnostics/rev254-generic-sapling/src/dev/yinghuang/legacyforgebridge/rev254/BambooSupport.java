package dev.yinghuang.legacyforgebridge.rev254;
import net.minecraft.*;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.*;
import java.io.Reader;
import java.nio.file.Files;
import java.util.*;
import org.slf4j.LoggerFactory;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
/** Hash-bound Bamboo 2.6.8.5 leaf/petal + legacy resource compatibility adapter. */
public final class BambooSupport {
 private static Set<String> leafIds,saplingIds;
 private static boolean warnedSprite;
 public static synchronized void discover(){
  if(leafIds!=null&&saplingIds!=null)return;
  Set<String> leaves=new HashSet<>(),saplings=new HashSet<>();
  for(var mod:FabricLoader.getInstance().getAllMods()){
   var manifest=mod.findPath("legacyforgebridge/converted-content.json");if(manifest.isEmpty())continue;
   try(Reader r=Files.newBufferedReader(manifest.get())){
    JsonObject root=JsonParser.parseReader(r).getAsJsonObject();
    if(!root.has("sourceSha256")||!BambooRules.hash(root.get("sourceSha256").getAsString()))continue;
    JsonArray blocks=root.getAsJsonArray("blocks");if(blocks==null)continue;
    for(JsonElement e:blocks){JsonObject b=e.getAsJsonObject();
     if(!b.has("sourceClass")||!b.has("id"))continue;
     String hash=root.get("sourceSha256").getAsString(),source=b.get("sourceClass").getAsString(),id=b.get("id").getAsString();
     if(BambooRules.admitLeaf(hash,source))leaves.add(id);
     if(BambooRules.admitSapling(hash,source))saplings.add(id);
    }
   }catch(Exception e){LoggerFactory.getLogger("LFB-rev254").warn("Cannot read converted source manifest; no guessed Bamboo rule will be applied",e);}
  }
  leafIds=Set.copyOf(leaves);saplingIds=Set.copyOf(saplings);
  LoggerFactory.getLogger("LFB-rev254").info("Bamboo 2.6.8.5 verified carriers: leaves="+leafIds+", saplings="+saplingIds+"; sapling=cross/cutout + source bounds + empty collision");
 }
 public static boolean isLeaf(class_2960 id){if(leafIds==null)discover();return id!=null&&leafIds.contains(id.toString());}
 public static boolean isSapling(class_2960 id){if(saplingIds==null)discover();return id!=null&&saplingIds.contains(id.toString());}
 public static class_4970.class_2251 soundProperties(class_2960 id,class_4970.class_2251 p){
  boolean leaf=isLeaf(id),sapling=isSapling(id);
  if(Rev254Config.BAMBOO_SOUND&&(leaf||sapling))return p.method_9626(class_2498.field_11535);
  return p;
 }
 public static void display(class_2960 id,class_2680 state,class_1937 level,class_2338 pos,class_5819 random){
  if(!Rev254Config.BAMBOO_PETALS || !(level instanceof class_638 world) || !isLeaf(id))return;
  if(random.method_43048(100)!=0)return;
  int x=pos.method_10263(),y=pos.method_10264(),z=pos.method_10260();
  if(!world.method_8320(new class_2338(x,y-1,z)).method_26215())return;
  double px=(float)x+random.method_43057(),pz=(float)z+random.method_43057();
  int meta=ConvertedLegacyBlock.legacyMeta(state);
  class_310 mc=class_310.method_1551();if(mc.field_1687!=world)return;
  class_1059 atlas=mc.method_72703().method_73025(class_2960.method_60654("minecraft:blocks"));
  class_1058 sprite=atlas.method_4608(class_2960.method_60654("legacyforgebridge:rev254/petal_"+BambooRules.variant(meta)));
  if(sprite==atlas.method_73023()){
   if(!warnedSprite){warnedSprite=true;LoggerFactory.getLogger("LFB-rev254").warn("Bamboo petal sprite missing from block atlas; check resource reload errors");}return;
  }
  mc.field_1713.method_3058(new BambooPetal(world,px,y,pz,sprite,meta,random));
 }
 private BambooSupport(){}
}