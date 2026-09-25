package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Immutable per-launch lookup of source-proven metadata item model definitions. */
public final class ConvertedIconModelCatalog {
    private record Entry(String fabricId,String model) { }
    private static volatile Map<String,Entry> models;
    private ConvertedIconModelCatalog(){ }
    public static String itemModel(String item,int metadata){
        Map<String,Entry> snapshot=models;
        if(snapshot==null){synchronized(ConvertedIconModelCatalog.class){if(models==null)models=load();snapshot=models;}}
        Entry entry=snapshot.get(item+"#"+metadata);
        return entry==null||ConvertedModCatalog.isMarkedStale(entry.fabricId())?null:entry.model();
    }
    private static Map<String,Entry> load(){
        Map<String,Entry> found=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        for(var mod:FabricLoader.getInstance().getAllMods()){
            var path=mod.findPath("legacyforgebridge/icon-presentation.json");if(path.isEmpty())continue;
            try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                if(!root.has("items")||!root.get("items").isJsonObject())continue;
                for(var item:root.getAsJsonObject("items").entrySet()){
                    if(!item.getValue().isJsonObject())continue;
                    for(var variant:item.getValue().getAsJsonObject().entrySet()){
                        if(!variant.getValue().isJsonPrimitive())continue;
                        String key=item.getKey()+"#"+variant.getKey(),model=variant.getValue().getAsString();
                        if(!model.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||ambiguous.contains(key))continue;
                        // The definition must actually be in the candidate that claims it.
                        String[] id=model.split(":",2);
                        if(mod.findPath("assets/"+id[0]+"/items/"+id[1]+".json").isEmpty())continue;
                        Entry value=new Entry(mod.getMetadata().getId(),model),previous=found.putIfAbsent(key,value);
                        if(previous!=null&&!previous.equals(value)){found.remove(key);ambiguous.add(key);}
                    }
                }
            }catch(Exception invalid){/* A corrupt optional presentation map must not corrupt item identity. */}
        }
        return Map.copyOf(found);
    }
}
