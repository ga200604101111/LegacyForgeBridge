package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyCreativeTabAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Emits source-proven custom CreativeTabs for any legacy mod without mod-specific tables. */
public final class LegacyCreativeTabPresentationPass implements ConversionPass {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-creative-tab-presentation";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path manifest=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(manifest))return;
        JsonObject root=JsonParser.parseString(Files.readString(manifest,StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray items=root.getAsJsonArray("items");
        if(items==null||items.isEmpty())return;
        JsonArray existing=root.getAsJsonArray("creativeTabs");
        if(existing!=null&&!existing.isEmpty())return;

        Map<String,List<JsonObject>> byName=new LinkedHashMap<>();
        for(JsonElement element:items){
            if(!element.isJsonObject())continue;
            JsonObject item=element.getAsJsonObject();
            add(byName,path(string(item,"id")),item);
            add(byName,normalize(string(item,"legacyRegistryName")),item);
        }

        var analysis=new LegacyCreativeTabAnalyzer().analyze(context.sourceJar());
        if(analysis.tabs().isEmpty())return;
        String namespace=context.metadata().fabricId();
        Set<String> usedIds=new LinkedHashSet<>();
        JsonArray tabs=new JsonArray();int assigned=0;
        for(var source:analysis.tabs()){
            LinkedHashMap<String,JsonObject> members=new LinkedHashMap<>();
            for(String sourceName:source.itemNames()){
                List<JsonObject> candidates=byName.getOrDefault(normalize(sourceName),List.of());
                if(candidates.size()!=1)continue;
                JsonObject item=candidates.getFirst();String id=string(item,"id");
                if(id!=null)members.putIfAbsent(id,item);
            }
            if(members.isEmpty())continue;
            String tabId=uniqueId(namespace,source.label(),source.fieldName(),usedIds);
            JsonObject tab=new JsonObject();tab.addProperty("id",tabId);
            if(source.label()!=null&&!source.label().isBlank())
                tab.addProperty("titleKey",LegacyLanguagePass.translationAlias(context,"itemGroup."+source.label()));
            else tab.addProperty("title",source.fieldName());
            String icon=resolveIcon(source.iconItemName(),byName,members.keySet().iterator().next());
            tab.addProperty("icon",icon);
            JsonArray ids=new JsonArray();
            for(var member:members.entrySet()){
                member.getValue().addProperty("creativeTab",tabId);
                ids.add(member.getKey());assigned++;
            }
            tab.add("items",ids);tabs.add(tab);
        }
        if(tabs.isEmpty())return;
        root.add("creativeTabs",tabs);
        Files.writeString(manifest,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-CREATIVE-0001",SupportLevel.ADAPTED,
                "Recovered source-defined custom creative tabs="+tabs.size()+", assignedItems="+assigned+".");
    }

    private static String resolveIcon(String source,Map<String,List<JsonObject>> byName,String fallback){
        List<JsonObject> candidates=byName.getOrDefault(normalize(source),List.of());
        if(candidates.size()==1){String id=string(candidates.getFirst(),"id");if(id!=null)return id;}
        return fallback;
    }
    private static void add(Map<String,List<JsonObject>> map,String key,JsonObject item){
        if(key==null||key.isBlank())return;map.computeIfAbsent(key,ignored->new ArrayList<>()).add(item);
    }
    private static String uniqueId(String namespace,String label,String field,Set<String> used){
        String path=sanitize(label);if(path.isBlank())path=sanitize(field);if(path.isBlank())path="legacy_tab";
        String id=namespace+":"+path;int suffix=2;while(!used.add(id))id=namespace+":"+path+"_"+suffix++;
        return id;
    }
    private static String sanitize(String raw){
        if(raw==null)return "";String value=raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(value.startsWith("_")||value.startsWith("/"))value=value.substring(1);
        while(value.endsWith("_")||value.endsWith("/"))value=value.substring(0,value.length()-1);
        return value;
    }
    private static String path(String id){if(id==null)return null;int split=id.indexOf(':');return normalize(split>=0?id.substring(split+1):id);}
    private static String normalize(String raw){
        if(raw==null)return null;String value=raw;
        if(value.startsWith("item."))value=value.substring(5);
        int split=value.indexOf(':');if(split>=0)value=value.substring(split+1);
        return value.toLowerCase(Locale.ROOT);
    }
    private static String string(JsonObject object,String key){
        JsonElement value=object.get(key);return value!=null&&value.isJsonPrimitive()?value.getAsString():null;
    }
}
