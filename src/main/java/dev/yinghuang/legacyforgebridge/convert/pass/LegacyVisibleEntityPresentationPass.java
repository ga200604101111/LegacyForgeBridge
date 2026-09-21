package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyVisibleEntityPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/** Publishes bounded visible-Entity adapters proven entirely from the source JAR. */
public final class LegacyVisibleEntityPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/visible-entity-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-visible-entity-presentation";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyVisibleEntityPresentationAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.diagnostics().isEmpty())return;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("legacyModId",context.metadata().primary().modId());root.addProperty("runtimeImplementationWired",true);
        root.addProperty("remoteSpawnRuntimeWired",true);root.addProperty("clientRendererRuntimeWired",true);
        JsonArray rules=new JsonArray();
        for(var rule:analysis.rules()){
            JsonObject value=new JsonObject();value.addProperty("id",context.metadata().fabricId()+":"+modernPath(rule.registryName()));
            value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceClass",rule.sourceClass());
            value.addProperty("rendererClass",rule.rendererClass());value.addProperty("adapter",rule.adapter().name());
            value.addProperty("legacyNumericId",rule.legacyNumericId());value.addProperty("trackingRange",rule.trackingRange());
            value.addProperty("updateFrequency",rule.updateFrequency());value.addProperty("velocityUpdates",rule.velocityUpdates());
            value.addProperty("width",rule.width());value.addProperty("height",rule.height());
            value.addProperty("modelTextureWidth",rule.modelTextureWidth());value.addProperty("modelTextureHeight",rule.modelTextureHeight());
            if(rule.fixedTexture()!=null)value.addProperty("fixedTexture",rule.fixedTexture());
            JsonArray parts=new JsonArray();for(var part:rule.parts()){
                JsonObject p=new JsonObject();p.addProperty("field",part.field());p.addProperty("u",part.u());p.addProperty("v",part.v());
                p.addProperty("x",part.x());p.addProperty("y",part.y());p.addProperty("z",part.z());
                p.addProperty("width",part.width());p.addProperty("height",part.height());p.addProperty("depth",part.depth());
                p.addProperty("pivotX",part.pivotX());p.addProperty("pivotY",part.pivotY());p.addProperty("pivotZ",part.pivotZ());
                p.addProperty("xRot",part.xRot());p.addProperty("yRot",part.yRot());p.addProperty("zRot",part.zRot());p.addProperty("mirror",part.mirror());parts.add(p);
            }value.add("parts",parts);
            JsonObject watchers=new JsonObject();rule.watcherIndices().forEach(watchers::addProperty);value.add("watchers",watchers);
            JsonObject watcherTypes=new JsonObject();rule.watcherTypes().forEach((index,type)->watcherTypes.addProperty(String.valueOf(index),type));value.add("watcherTypes",watcherTypes);
            JsonArray textures=new JsonArray();for(var texture:rule.textureVariants()){
                JsonObject t=new JsonObject();t.addProperty("value",texture.value());t.addProperty("texture",texture.texture());t.addProperty("translucent",texture.translucent());textures.add(t);
            }value.add("textureVariants",textures);
            JsonArray palette=new JsonArray();rule.palette().forEach(palette::add);value.add("palette",palette);
            value.addProperty("itemWatcherBase",rule.itemWatcherBase());value.addProperty("itemWatcherCount",rule.itemWatcherCount());
            value.addProperty("proof",rule.proof());value.addProperty("runtimeComplete",true);rules.add(value);
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteRules",rules.size());
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("analysisDiagnostics",diagnostics);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(!rules.isEmpty())context.diagnostics().info("LFB-CONVERT-VISIBLE-ENTITY-0001",SupportLevel.ADAPTED,
                "Source-proven visible Entity adapters="+rules.size()+"; slide-panel/cushion/tray renderer families are candidate-owned and source classes remain non-executed.");
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning("LFB-CONVERT-VISIBLE-ENTITY-0002",SupportLevel.RUNTIME_BRIDGE,diagnostic);
    }

    private static String modernPath(String raw){
        String path=raw==null?"":raw.trim().toLowerCase(Locale.ROOT).replace('\\','/').replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(path.startsWith("/"))path=path.substring(1);return path.isBlank()?"legacy_visual_entity":path;
    }
}
