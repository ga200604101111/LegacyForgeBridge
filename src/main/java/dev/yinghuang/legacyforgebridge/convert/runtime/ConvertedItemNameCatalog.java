package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Per-launch name catalogue; conflicting candidates cannot win by load order. */
public final class ConvertedItemNameCatalog {
    private record Entry(String mod,String key) { }
    private static volatile Map<String,Entry> entries;
    private ConvertedItemNameCatalog(){ }
    public static String translationKey(String item,int metadata) {
        Map<String,Entry> map=entries;
        if(map==null){synchronized(ConvertedItemNameCatalog.class){if(entries==null)entries=load();map=entries;}}
        Entry value=map.get(item+"#"+metadata);
        if(value==null)value=map.get(item+"#default");
        return value==null||ConvertedModCatalog.isMarkedStale(value.mod())?null:value.key();
    }
    private static Map<String,Entry> load() {
        Map<String,Entry> result=new HashMap<>();Set<String> conflicts=new HashSet<>();
        for(var mod:FabricLoader.getInstance().getAllMods()) {
            String modId=mod.getMetadata().getId();
            Map<String,Entry> candidate=new HashMap<>();
            var content=mod.findPath("legacyforgebridge/converted-content.json");
            if(content.isEmpty())continue;
            try {
                JsonObject root=JsonParser.parseString(Files.readString(content.get(),StandardCharsets.UTF_8)).getAsJsonObject();
                for(String category:List.of("blocks","items"))if(root.has(category))for(JsonElement el:root.getAsJsonArray(category)) {
                    JsonObject def=el.getAsJsonObject();if(def.has("id")&&def.has("descriptionKey"))candidate.put(def.get("id").getAsString()+"#default",new Entry(modId,def.get("descriptionKey").getAsString()));
                }
                var path=mod.findPath("legacyforgebridge/item-names.json");
                if(path.isPresent()) {
                    JsonObject names=JsonParser.parseString(Files.readString(path.get(),StandardCharsets.UTF_8)).getAsJsonObject();
                    if(names.has("items"))for(var item:names.getAsJsonObject("items").entrySet())for(var variant:item.getValue().getAsJsonObject().entrySet()) {
                        String key=variant.getValue().getAsString();
                        if(!key.isBlank())candidate.put(item.getKey()+"#"+variant.getKey(),new Entry(modId,key));
                    }
                }
                for(var e:candidate.entrySet()) {
                    if(conflicts.contains(e.getKey()))continue;
                    Entry previous=result.putIfAbsent(e.getKey(),e.getValue());
                    if(previous!=null&&!previous.equals(e.getValue())){result.remove(e.getKey());conflicts.add(e.getKey());}
                }
            }catch(Exception invalid){/* Do not partially install a malformed candidate catalogue. */}
        }
        return Map.copyOf(result);
    }
}
