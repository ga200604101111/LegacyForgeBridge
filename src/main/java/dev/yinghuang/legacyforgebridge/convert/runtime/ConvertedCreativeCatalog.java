package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Source-enumerated creative variants; absent proof is distinct from a proved empty output. */
public final class ConvertedCreativeCatalog {
    private record Entry(String mod,List<Integer> metadata) { }
    private static volatile Map<String,Entry> entries;
    private ConvertedCreativeCatalog(){ }
    public static List<Integer> metadata(String item) {
        var map=entries;if(map==null){synchronized(ConvertedCreativeCatalog.class){if(entries==null)entries=load();map=entries;}}
        Entry e=map.get(item);return e==null||ConvertedModCatalog.isMarkedStale(e.mod())?List.of(0):e.metadata();
    }
    static Map<String,List<Integer>> parse(JsonObject root) {
        if(!root.has("schemaVersion")||root.get("schemaVersion").getAsBigDecimal().intValueExact()!=1)throw new IllegalArgumentException("Unknown creative schema");
        Map<String,List<Integer>> parsed=new LinkedHashMap<>();JsonObject items=root.getAsJsonObject("items");
        if(items==null||items.size()>16384)throw new IllegalArgumentException("Invalid creative catalogue");
        for(var entry:items.entrySet()){
            if(!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||!entry.getValue().isJsonArray())throw new IllegalArgumentException("Invalid creative identity");
            JsonArray array=entry.getValue().getAsJsonArray();if(array.size()>4096)throw new IllegalArgumentException("Creative output too large");
            LinkedHashSet<Integer> values=new LinkedHashSet<>();
            for(JsonElement v:array){int n=v.getAsBigDecimal().intValueExact();if(n<0||n>65535)throw new IllegalArgumentException("Invalid creative metadata");values.add(n);}
            parsed.put(entry.getKey(),List.copyOf(values));
        }
        return Map.copyOf(parsed);
    }
    private static Map<String,Entry> load(){
        Map<String,Entry> found=new HashMap<>();Set<String> conflicts=new HashSet<>();
        for(var mod:FabricLoader.getInstance().getAllMods()){
            var path=mod.findPath("legacyforgebridge/creative-variants.json");if(path.isEmpty())continue;
            try{
                var candidate=parse(JsonParser.parseString(Files.readString(path.get(),StandardCharsets.UTF_8)).getAsJsonObject());
                for(var e:candidate.entrySet()){
                    if(conflicts.contains(e.getKey()))continue;
                    Entry value=new Entry(mod.getMetadata().getId(),e.getValue()),previous=found.putIfAbsent(e.getKey(),value);
                    if(previous!=null&&!previous.equals(value)){found.remove(e.getKey());conflicts.add(e.getKey());}
                }
            }catch(Exception invalid){/* An invalid optional map never partially populates a creative tab. */}
        }
        return Map.copyOf(found);
    }
}
