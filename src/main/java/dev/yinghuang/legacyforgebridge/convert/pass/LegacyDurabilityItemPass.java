package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyDurabilityItemAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Applies source-proven max-damage to every converted item and publishes narrow bar semantics. */
public final class LegacyDurabilityItemPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/durability-item-rules.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-durability-items";}

    @Override public void apply(ConversionContext context)throws Exception{
        Path contentPath=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=JsonParser.parseString(Files.readString(contentPath,StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray items=content.getAsJsonArray("items");if(items==null)return;
        Map<String,JsonObject> byRegistration=new LinkedHashMap<>();
        for(JsonElement element:items)if(element.isJsonObject()){
            JsonObject item=element.getAsJsonObject();
            if(item.has("legacyRegistryName")&&item.has("sourceClass"))
                byRegistration.put(key(item.get("legacyRegistryName").getAsString(),item.get("sourceClass").getAsString()),item);
        }

        var analysis=new LegacyDurabilityItemAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();int mapped=0,unmapped=0,inverse=0;
        for(var rule:analysis.rules()){
            JsonObject item=byRegistration.get(key(rule.registryName(),rule.sourceClass()));
            if(item==null||!item.has("id")){unmapped++;continue;}
            item.addProperty("durability",rule.durability());
            JsonObject value=new JsonObject();value.addProperty("id",item.get("id").getAsString());
            value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceClass",rule.sourceClass());
            value.addProperty("durability",rule.durability());value.addProperty("alwaysShowBar",rule.alwaysShowBar());
            value.addProperty("inverseProgressBar",rule.inverseProgressBar());value.addProperty("runtimeComplete",true);
            rules.add(value);mapped++;if(rule.inverseProgressBar())inverse++;
        }
        root.add("rules",rules);root.addProperty("mappedRules",mapped);root.addProperty("inverseProgressRules",inverse);
        root.addProperty("unmappedRules",unmapped);
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("diagnostics",diagnostics);
        Files.writeString(contentPath,JSON.toJson(content)+"\n",StandardCharsets.UTF_8);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());
        Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(mapped>0)context.diagnostics().info("LFB-CONVERT-DURABILITY-0001",SupportLevel.ADAPTED,
                "Source-proven item durability rules="+mapped+", inverse progress bars="+inverse+".");
        if(unmapped>0)context.diagnostics().warning("LFB-CONVERT-DURABILITY-0002",SupportLevel.MANUAL_REQUIRED,
                "Source-proven durability rules without generated item identity="+unmapped+".");
    }

    private static String key(String registryName,String sourceClass){return registryName+"\u0000"+sourceClass;}
}
