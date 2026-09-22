package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyRotatingAssemblyEntityRegistry;
import dev.yinghuang.legacyforgebridge.convert.LegacyRotatingAssemblyEntityAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Publishes source-proven large rotating legacy Entity assembly presentation/runtime rules. */
public final class LegacyRotatingAssemblyEntityPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/rotating-assembly-entity-rules.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-rotating-assembly-entity";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyRotatingAssemblyEntityAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty()&&analysis.diagnostics().isEmpty())return;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("legacyModId",context.metadata().primary().modId());
        root.addProperty("runtimeImplementationWired",true);root.addProperty("remoteSpawnRuntimeWired",true);
        root.addProperty("clientRendererRuntimeWired",true);root.addProperty("legacyWatcherRuntimeWired",true);
        JsonArray rules=new JsonArray();
        for(var rule:analysis.rules()){
            JsonObject value=new JsonObject();
            value.addProperty("id",context.metadata().fabricId()+":"+modernPath(rule.registryName()));
            value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceClass",rule.sourceClass());
            value.addProperty("rendererClass",rule.rendererClass());value.addProperty("sourceModelClass",rule.sourceModelClass());
            value.addProperty("legacyNumericId",rule.legacyNumericId());value.addProperty("trackingRange",rule.trackingRange());
            value.addProperty("updateFrequency",rule.updateFrequency());value.addProperty("velocityUpdates",rule.velocityUpdates());
            value.addProperty("adapter",rule.adapter().name());value.addProperty("modelScale",rule.modelScale());
            value.addProperty("modelTextureWidth",rule.modelTextureWidth());value.addProperty("modelTextureHeight",rule.modelTextureHeight());
            value.addProperty("directionWatcher",rule.directionWatcher());value.addProperty("sizeWatcher",rule.sizeWatcher());
            value.addProperty("countWatcher",rule.countWatcher());value.addProperty("textureWatcher",rule.textureWatcher());
            value.addProperty("reverseWatcher",rule.reverseWatcher());
            value.addProperty("directionDefault",rule.directionDefault());value.addProperty("sizeDefault",rule.sizeDefault());
            value.addProperty("sizeMin",rule.sizeMin());value.addProperty("sizeMax",rule.sizeMax());
            value.addProperty("countDefault",rule.countDefault());value.addProperty("textureDefault",rule.textureDefault());
            value.addProperty("reverseDefault",rule.reverseDefault());value.addProperty("countBase",rule.countBase());
            value.addProperty("countMax",rule.countMax());value.addProperty("fixedRepeatCount",rule.fixedRepeatCount());
            value.addProperty("secondaryPhaseDegrees",rule.secondaryPhaseDegrees());
            JsonArray textures=new JsonArray();rule.textures().forEach(textures::add);value.add("textures",textures);
            value.add("staticParts",parts(rule.staticParts()));value.add("repeatedPrimary",parts(rule.repeatedPrimary()));
            value.add("repeatedSecondary",parts(rule.repeatedSecondary()));
            value.addProperty("physicalCollision",rule.physicalCollision());value.addProperty("playerAttackRemoves",rule.playerAttackRemoves());
            value.addProperty("randomInitialPhase",rule.randomInitialPhase());value.addProperty("runtimeComplete",true);
            try{LegacyRotatingAssemblyEntityRegistry.validateCandidateRule(value,context.metadata().primary().modId());}
            catch(RuntimeException invalid){throw new IllegalStateException("Rotating assembly runtime schema rejected source-proven rule "+rule.registryName()+"#"+rule.legacyNumericId(),invalid);}
            rules.add(value);
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteRules",rules.size());
        JsonArray skipped=new JsonArray();for(var skip:analysis.skipped()){JsonObject value=new JsonObject();
            if(skip.registryName()!=null)value.addProperty("registryName",skip.registryName());
            if(skip.sourceClass()!=null)value.addProperty("sourceClass",skip.sourceClass());
            value.addProperty("reason",skip.reason());skipped.add(value);}root.add("skipped",skipped);
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("analysisDiagnostics",diagnostics);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());
        Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(!rules.isEmpty())context.diagnostics().info("LFB-CONVERT-ROTATING-ENTITY-0001",SupportLevel.ADAPTED,
                "Source-proven rotating assembly Entity runtime rules="+rules.size()+"; legacy server remains authoritative for interaction/lifecycle.");
        for(var skip:analysis.skipped())context.diagnostics().warning("LFB-CONVERT-ROTATING-ENTITY-0002",SupportLevel.RUNTIME_BRIDGE,
                "Rotating assembly Entity not admitted for "+skip.registryName()+": "+skip.reason());
    }

    private static JsonArray parts(java.util.List<LegacyRotatingAssemblyEntityAnalyzer.Cuboid> source){
        JsonArray out=new JsonArray();for(var c:source){JsonObject p=new JsonObject();p.addProperty("field",c.field());p.addProperty("u",c.u());p.addProperty("v",c.v());
            p.addProperty("x",c.x());p.addProperty("y",c.y());p.addProperty("z",c.z());p.addProperty("width",c.width());p.addProperty("height",c.height());p.addProperty("depth",c.depth());
            p.addProperty("pivotX",c.pivotX());p.addProperty("pivotY",c.pivotY());p.addProperty("pivotZ",c.pivotZ());
            p.addProperty("xRot",c.xRot());p.addProperty("yRot",c.yRot());p.addProperty("zRot",c.zRot());p.addProperty("mirror",c.mirror());out.add(p);}return out;
    }
    private static String modernPath(String raw){
        String path=raw==null?"":raw.trim().toLowerCase(java.util.Locale.ROOT).replace('\\','/').replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(path.startsWith("/"))path=path.substring(1);return path.isBlank()?"legacy_rotating_entity":path;
    }
}
