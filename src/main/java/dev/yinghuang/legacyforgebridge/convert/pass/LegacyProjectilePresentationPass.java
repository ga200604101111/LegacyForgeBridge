package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyProjectilePresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes source-proven remote projectile presentation rules without executing legacy classes. */
public final class LegacyProjectilePresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/projectile-presentation-rules.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-projectile-presentation";}

    @Override public void apply(ConversionContext context)throws Exception{
        Path contentPath=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=JsonParser.parseString(Files.readString(contentPath,StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String,String> itemIds=new LinkedHashMap<>();
        JsonArray items=content.getAsJsonArray("items");
        if(items!=null)for(JsonElement element:items){
            if(!element.isJsonObject())continue;JsonObject item=element.getAsJsonObject();
            if(item.has("sourceClass")&&item.has("id"))itemIds.put(item.get("sourceClass").getAsString(),item.get("id").getAsString());
        }

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty()&&analysis.diagnostics().isEmpty())return;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("legacyModId",context.metadata().primary().modId());
        root.addProperty("runtimeImplementationWired",true);root.addProperty("remoteSpawnRuntimeWired",true);
        root.addProperty("clientRendererRuntimeWired",true);

        JsonArray rules=new JsonArray();
        for(var rule:analysis.rules()){
            String itemId=itemIds.get(rule.sourceItemClass());if(itemId==null)continue;
            JsonObject value=new JsonObject();
            value.addProperty("id",context.metadata().fabricId()+":"+modernPath(rule.registryName()));
            value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceClass",rule.sourceClass());
            value.addProperty("rendererClass",rule.rendererClass());value.addProperty("legacyNumericId",rule.legacyNumericId());
            value.addProperty("trackingRange",rule.trackingRange());value.addProperty("updateFrequency",rule.updateFrequency());
            value.addProperty("velocityUpdates",rule.velocityUpdates());value.addProperty("width",rule.width());value.addProperty("height",rule.height());
            value.addProperty("baseFamily",rule.baseFamily().name());value.addProperty("adapter",rule.adapter().name());
            value.addProperty("itemId",itemId);value.addProperty("sourceItemClass",rule.sourceItemClass());
            value.addProperty("sourceItemRegistryName",rule.sourceItemRegistryName());
            value.addProperty("metadataWatcherIndex",rule.metadataWatcherIndex());
            value.addProperty("metadataWatcherWireType",rule.metadataWatcherWireType());
            value.addProperty("metadataOffset",rule.metadataOffset());value.addProperty("defaultItemMetadata",rule.defaultItemMetadata());
            if(rule.fixedTexture()!=null)value.addProperty("fixedTexture",rule.fixedTexture());
            value.addProperty("proof",rule.proof());value.addProperty("runtimeComplete",true);rules.add(value);
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteRules",rules.size());

        JsonArray skipped=new JsonArray();for(var rule:analysis.skipped()){
            JsonObject value=new JsonObject();if(rule.registryName()!=null)value.addProperty("registryName",rule.registryName());
            if(rule.sourceClass()!=null)value.addProperty("sourceClass",rule.sourceClass());value.addProperty("reason",rule.reason());skipped.add(value);
        }root.add("skipped",skipped);
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("analysisDiagnostics",diagnostics);

        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());
        Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(!rules.isEmpty())context.diagnostics().info("LFB-CONVERT-PROJECTILE-0001",SupportLevel.ADAPTED,
                "Source-proven remote projectile presentation rules="+rules.size()+"; FML throwable velocity and item-bound rendering are candidate-owned.");
        for(var skip:analysis.skipped())context.diagnostics().warning("LFB-CONVERT-PROJECTILE-0002",SupportLevel.RUNTIME_BRIDGE,
                "Projectile presentation not admitted for "+skip.registryName()+": "+skip.reason());
    }

    private static String modernPath(String raw){
        String path=raw==null?"":raw.trim().toLowerCase(Locale.ROOT).replace('\\','/')
                .replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(path.startsWith("/"))path=path.substring(1);return path.isBlank()?"legacy_projectile":path;
    }
}
