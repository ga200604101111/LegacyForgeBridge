package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyProjectilePresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyFixedModelProjectilePreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyModelBoxMesh1710;
import dev.yinghuang.legacyforgebridge.convert.LegacyVanillaRegistry1710;
import dev.yinghuang.legacyforgebridge.convert.LegacyVanillaStackDataFix;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes source-proven remote projectile presentation rules without executing legacy classes. */
public final class LegacyProjectilePresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/projectile-presentation-rules.json";
    /** Source-only census; this file is never a live renderer/EntityType rule input. */
    public static final String FIXED_MODEL_PREFLIGHT_OUTPUT=
            "legacyforgebridge/projectile-fixed-model-preflight.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    record PresentationItem(String modernId,int runtimeMetadata,String sourceKind) {
        PresentationItem {
            if(modernId==null||modernId.isBlank()||runtimeMetadata<0||sourceKind==null||sourceKind.isBlank())
                throw new IllegalArgumentException("Invalid projectile presentation item");
        }
    }

    @Override public String id(){return "legacy-projectile-presentation";}

    @Override public void apply(ConversionContext context)throws Exception{
        Path contentPath=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        // A completely separate, non-executable proof surface: existing projectile rules
        // and their runtime readiness flags must not change when this optional census changes.
        try {
            publishFixedModelPreflight(context,
                    new LegacyFixedModelProjectilePreflight().analyze(context.sourceJar()));
        } catch (IOException | RuntimeException unavailable) {
            context.diagnostics().warning("LFB-CONVERT-PROJECTILE-0003", SupportLevel.AUTO,
                    "Fixed-model projectile evidence unavailable; no new runtime adapter admitted ("
                            + unavailable.getClass().getSimpleName() + ").");
        }
        JsonObject content=JsonParser.parseString(Files.readString(contentPath,StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String,String> itemIds=new LinkedHashMap<>(),uniqueClassIds=new LinkedHashMap<>();
        Set<String> ambiguousKeys=new HashSet<>(),ambiguousClasses=new HashSet<>();
        JsonArray items=content.getAsJsonArray("items");
        if(items!=null)for(JsonElement element:items){
            if(!element.isJsonObject())continue;JsonObject item=element.getAsJsonObject();
            if(!item.has("sourceClass")||!item.has("id"))continue;
            String sourceClass=item.get("sourceClass").getAsString(),id=item.get("id").getAsString();
            String previousClass=uniqueClassIds.putIfAbsent(sourceClass,id);
            if(previousClass!=null&&!previousClass.equals(id)){uniqueClassIds.remove(sourceClass);ambiguousClasses.add(sourceClass);}
            if(item.has("legacyRegistryName")){
                String key=itemKey(sourceClass,item.get("legacyRegistryName").getAsString());
                String previous=itemIds.putIfAbsent(key,id);
                if(previous!=null&&!previous.equals(id)){itemIds.remove(key);ambiguousKeys.add(key);}
            }
        }

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty()&&analysis.diagnostics().isEmpty())return;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("legacyModId",context.metadata().primary().modId());
        root.addProperty("runtimeImplementationWired",true);root.addProperty("remoteSpawnRuntimeWired",true);
        root.addProperty("clientRendererRuntimeWired",true);

        JsonArray rules=new JsonArray();
        for(var rule:analysis.rules()){
            PresentationItem presentation=resolvePresentationItem(rule,itemIds,uniqueClassIds,ambiguousKeys,ambiguousClasses);
            if(presentation==null)continue;
            String itemId=presentation.modernId();
            JsonObject value=new JsonObject();
            value.addProperty("id",context.metadata().fabricId()+":"+modernPath(rule.registryName()));
            value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceClass",rule.sourceClass());
            value.addProperty("rendererClass",rule.rendererClass());value.addProperty("legacyNumericId",rule.legacyNumericId());
            value.addProperty("trackingRange",rule.trackingRange());value.addProperty("updateFrequency",rule.updateFrequency());
            value.addProperty("velocityUpdates",rule.velocityUpdates());value.addProperty("width",rule.width());value.addProperty("height",rule.height());
            value.addProperty("baseFamily",rule.baseFamily().name());value.addProperty("adapter",rule.adapter().name());
            value.addProperty("itemId",itemId);value.addProperty("sourceItemClass",rule.sourceItemClass());
            value.addProperty("sourceItemRegistryName",rule.sourceItemRegistryName());
            value.addProperty("presentationItemSource",presentation.sourceKind());
            value.addProperty("legacyPresentationMetadata",rule.defaultItemMetadata());
            value.addProperty("metadataWatcherIndex",rule.metadataWatcherIndex());
            value.addProperty("metadataWatcherWireType",rule.metadataWatcherWireType());
            value.addProperty("metadataOffset",rule.metadataOffset());value.addProperty("defaultItemMetadata",presentation.runtimeMetadata());
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


    static JsonObject fixedModelPreflightManifest(String sourceSha256, String legacyModId,
                                                   LegacyFixedModelProjectilePreflight.Analysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        JsonObject root=new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", sourceSha256);
        root.addProperty("legacyModId", legacyModId);
        root.addProperty("presentationFamily", "FIXED_CUBOID_PROJECTILE_PREFLIGHT");
        root.addProperty("runtimeWired", false);
        root.addProperty("launcherDataflowProven", false);
        root.addProperty("fmlSpawnRuntimeProven", false);
        root.addProperty("clientFullbrightProven", false);
        JsonArray candidates=new JsonArray();
        int sourceLauncherProven=0;
        int sourceFullbrightProven=0;
        for (var candidate : analysis.candidates()) {
            var proof=candidate.geometry();
            // Encode only bounded, source-proven mesh metadata. The executable renderer remains
            // disabled; consumers must never interpret this preflight as a runtime rule.
            var sourceMesh=LegacyModelBoxMesh1710.build(proof);
            JsonObject entry=new JsonObject();
            entry.addProperty("registryName", candidate.registryName());
            entry.addProperty("sourceClass", candidate.entityClass());
            entry.addProperty("rendererClass", candidate.rendererClass());
            entry.addProperty("modelClass", proof.modelClass());
            entry.addProperty("texture", proof.texture());
            entry.addProperty("textureWidth", proof.textureWidth());
            entry.addProperty("textureHeight", proof.textureHeight());
            entry.addProperty("scale", proof.scale());
            entry.addProperty("legacyModelBoxFaceProof", true);
            entry.addProperty("modelBoxFaceCount",sourceMesh.quads().size());
            entry.addProperty("modelBoxVertexCount",sourceMesh.vertexCount());
            entry.addProperty("modelBoxTriangleCount",sourceMesh.triangleCount());
            entry.addProperty("sourceAxisAngleBakedIntoMesh",sourceMesh.sourceAxisAngleBaked());
            entry.addProperty("angle", proof.angle());
            entry.addProperty("axisX", proof.axisX());
            entry.addProperty("axisY", proof.axisY());
            entry.addProperty("axisZ", proof.axisZ());
            entry.addProperty("runtimeReady", false);
            boolean launcherProven=candidate.launcherProof().isPresent();
            entry.addProperty("sourceLauncherDataflowProven", launcherProven);
            boolean fullbrightProven=candidate.fullbrightProof().isPresent();
            entry.addProperty("sourceConstantFullbright1710Proven", fullbrightProven);
            if (fullbrightProven) {
                sourceFullbrightProven++;
                var brightness=candidate.fullbrightProof().orElseThrow();
                entry.addProperty("legacyBrightness",brightness.brightness());
                entry.addProperty("legacyPackedLight",brightness.packedLight());
                entry.addProperty("sourceBrightnessGetter",brightness.brightnessMethod());
                entry.addProperty("sourcePackedLightGetter",brightness.lightmapMethod());
            }
            if (launcherProven) {
                sourceLauncherProven++;
                var launcher=candidate.launcherProof().orElseThrow();
                entry.addProperty("launcherRegistryName",launcher.registryName());
                entry.addProperty("launcherSourceItemClass",launcher.sourceItemClass());
                entry.addProperty("launcherCallbackOwner",launcher.declaringOwner());
                entry.addProperty("launcherCallbackName",launcher.callbackName());
                entry.addProperty("launcherCallbackDescriptor",launcher.callbackDescriptor());
                entry.addProperty("launcherCallbackKind",launcher.callback().name());
            }
            JsonArray cuboids=new JsonArray();
            for (var part : proof.cuboids()) {
                JsonObject cuboid=new JsonObject();
                cuboid.addProperty("name",part.name());cuboid.addProperty("u",part.u());cuboid.addProperty("v",part.v());
                cuboid.addProperty("x",part.x());cuboid.addProperty("y",part.y());cuboid.addProperty("z",part.z());
                cuboid.addProperty("width",part.width());cuboid.addProperty("height",part.height());
                cuboid.addProperty("depth",part.depth());cuboid.addProperty("pivotX",part.pivotX());
                cuboid.addProperty("pivotY",part.pivotY());cuboid.addProperty("pivotZ",part.pivotZ());
                cuboids.add(cuboid);
            }
            entry.add("cuboids",cuboids);
            candidates.add(entry);
        }
        root.add("candidates",candidates);
        root.addProperty("provenRendererCandidates",candidates.size());
        root.addProperty("sourceLauncherProofCandidateCount",sourceLauncherProven);
        root.addProperty("sourceConstantFullbrightCandidateCount",sourceFullbrightProven);
        root.addProperty("launcherDataflowProven",candidates.size()>0&&sourceLauncherProven==candidates.size());
        JsonArray skipped=new JsonArray();
        for (var candidate : analysis.skipped()) {
            JsonObject entry=new JsonObject();
            if (candidate.registryName()!=null) entry.addProperty("registryName",candidate.registryName());
            if (candidate.entityClass()!=null) entry.addProperty("sourceClass",candidate.entityClass());
            entry.addProperty("reason",candidate.reason());
            skipped.add(entry);
        }
        root.add("skipped",skipped);
        return root;
    }

    private static void publishFixedModelPreflight(
            ConversionContext context, LegacyFixedModelProjectilePreflight.Analysis analysis) throws IOException {
        if (analysis.candidates().isEmpty() && analysis.skipped().isEmpty()) return;
        Path output=context.stagingDir().resolve(FIXED_MODEL_PREFLIGHT_OUTPUT);
        Files.createDirectories(output.getParent());
        JsonObject json=fixedModelPreflightManifest(
                context.sourceHash(), context.metadata().primary().modId(), analysis);
        Files.writeString(output,JSON.toJson(json)+"\n",StandardCharsets.UTF_8);
        if (!analysis.candidates().isEmpty()) context.diagnostics().info(
                "LFB-CONVERT-PROJECTILE-0004",SupportLevel.AUTO,
                "Fixed-model projectile geometry source-proof candidates="+analysis.candidates().size()
                        + "; runtime/launcher/lighting adaptation remains unwired.");
    }

    static PresentationItem resolvePresentationItem(
            LegacyProjectilePresentationAnalyzer.Rule rule,
            Map<String,String> itemIds,
            Map<String,String> uniqueClassIds,
            Set<String> ambiguousKeys,
            Set<String> ambiguousClasses
    ){
        if(rule==null)return null;
        if(LegacyVanillaRegistry1710.ITEMS_OWNER.equals(rule.sourceItemClass())){
            if(rule.metadataWatcherIndex()>=0||rule.metadataWatcherWireType()>=0||rule.metadataOffset()!=0)return null;
            try{
                LegacyVanillaStackDataFix.ModernStack modern=LegacyVanillaStackDataFix.upgrade(
                        rule.sourceItemRegistryName(),rule.defaultItemMetadata());
                if(modern.hasComponents())return null;
                return new PresentationItem(modern.id(),0,"VANILLA_1710_DFU");
            }catch(RuntimeException unresolved){
                return null;
            }
        }

        String key=itemKey(rule.sourceItemClass(),rule.sourceItemRegistryName());
        String itemId=ambiguousKeys.contains(key)?null:itemIds.get(key);
        if(itemId==null&&!ambiguousClasses.contains(rule.sourceItemClass()))
            itemId=uniqueClassIds.get(rule.sourceItemClass());
        return itemId==null?null:new PresentationItem(itemId,rule.defaultItemMetadata(),"CONVERTED_MOD_ITEM");
    }

    private static String itemKey(String sourceClass,String registryName){
        return sourceClass+"\u0000"+registryName;
    }

    private static String modernPath(String raw){
        String path=raw==null?"":raw.trim().toLowerCase(Locale.ROOT).replace('\\','/')
                .replaceAll("[^a-z0-9/._-]","_").replaceAll("_+","_");
        while(path.startsWith("/"))path=path.substring(1);return path.isBlank()?"legacy_projectile":path;
    }
}
