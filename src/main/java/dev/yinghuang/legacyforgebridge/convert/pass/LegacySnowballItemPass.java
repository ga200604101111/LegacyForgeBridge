package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySnowballItemAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reclassifies only source-proven vanilla-semantics ItemSnowball items to the modern snowball runtime. */
public final class LegacySnowballItemPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/snowball-item-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-snowball-items";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacySnowballItemAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;
        Path contentPath=context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        if(!Files.isRegularFile(contentPath)){context.diagnostics().warning("LFB-CONVERT-SNOWBALL-0004",SupportLevel.MANUAL_REQUIRED,"Source-proven ItemSnowball rules exist but converted content identities are unavailable.");return;}
        JsonObject content;try(Reader reader=Files.newBufferedReader(contentPath,StandardCharsets.UTF_8)){content=JsonParser.parseReader(reader).getAsJsonObject();}
        Map<String,JsonObject> items=new LinkedHashMap<>();JsonArray itemValues=content.getAsJsonArray("items");if(itemValues!=null)for(var element:itemValues)if(element.isJsonObject()){JsonObject item=element.getAsJsonObject();if(item.has("legacyRegistryName")&&item.has("sourceClass"))items.put(key(item.get("legacyRegistryName").getAsString(),item.get("sourceClass").getAsString()),item);}
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());JsonArray rules=new JsonArray();int unmapped=0;
        for(var rule:analysis.rules()){
            JsonObject item=items.get(key(rule.registryName(),rule.sourceClass()));if(item==null){unmapped++;continue;}item.addProperty("kind","snowball");JsonObject value=new JsonObject();value.addProperty("id",item.get("id").getAsString());value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceClass",rule.sourceClass());value.addProperty("vanillaUseSemanticsProven",true);value.addProperty("runtimeComplete",true);rules.add(value);
        }
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",item.registryName());if(item.sourceClass()!=null)value.addProperty("sourceClass",item.sourceClass());value.addProperty("reason",item.reason());skipped.add(value);context.diagnostics().warning("LFB-CONVERT-SNOWBALL-0002",SupportLevel.RUNTIME_BRIDGE,"Skipped custom legacy ItemSnowball "+item.registryName()+": "+item.reason());}root.add("skipped",skipped);root.addProperty("runtimeCompleteRules",rules.size());root.addProperty("skippedRules",skipped.size());
        Files.writeString(contentPath,GSON.toJson(content)+"\n",StandardCharsets.UTF_8);Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(unmapped>0)context.diagnostics().warning("LFB-CONVERT-SNOWBALL-0003",SupportLevel.MANUAL_REQUIRED,"Source-proven ItemSnowball rules without generated item identity: "+unmapped+".");
        if(!rules.isEmpty())context.diagnostics().info("LFB-CONVERT-SNOWBALL-0001",SupportLevel.ADAPTED,"Materialized inherited vanilla ItemSnowball runtime through modern SnowballItem: "+rules.size()+".");
    }
    private static String key(String registryName,String sourceClass){return registryName+"\u0000"+sourceClass;}
}
