package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyOscillatingModelBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyOscillatingModelInventoryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyOscillatingModelPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits proof-only rules for client-only oscillating decorative BlockContainer families. */
public final class LegacyOscillatingModelBlockPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/oscillating-model-block-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-oscillating-model-blocks";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyOscillatingModelBlockAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;

        Map<String,String> ids=generatedBlockIds(context.stagingDir());
        JsonObject root=new JsonObject();
        root.addProperty("schemaVersion",3);
        root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();
        int emitted=0,worldProofs=0,inventoryProofs=0;
        LegacyOscillatingModelPresentationAnalyzer worldAnalyzer=new LegacyOscillatingModelPresentationAnalyzer();
        LegacyOscillatingModelInventoryAnalyzer inventoryAnalyzer=new LegacyOscillatingModelInventoryAnalyzer();

        for(var rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());
            if(id==null)continue;
            JsonObject value=new JsonObject();
            value.addProperty("id",id);
            value.addProperty("sourceBlockClass",rule.sourceBlockClass());
            value.addProperty("sourceTileClass",rule.sourceTileClass());
            value.addProperty("legacyTileId",rule.legacyTileId());
            JsonArray orientation=new JsonArray();
            for(int meta:rule.placementMetaByYawQuadrant())orientation.add(meta);
            value.add("placementMetaByYawQuadrant",orientation);
            value.addProperty("fullCube",rule.fullCube());
            value.addProperty("lightEmission",rule.lightEmission());
            value.addProperty("nonOpaque",rule.nonOpaque());
            value.addProperty("nonNormalRender",rule.nonNormalRender());
            value.addProperty("legacyRenderPass",rule.renderPass());

            JsonObject animation=new JsonObject();
            animation.addProperty("angleField",rule.angleField());
            animation.addProperty("directionField",rule.directionField());
            animation.addProperty("randomInitialBound",rule.randomInitialBound());
            animation.addProperty("stepDegrees",rule.stepDegrees());
            animation.addProperty("lowerBoundDegrees",rule.lowerBoundDegrees());
            animation.addProperty("upperBoundDegrees",rule.upperBoundDegrees());
            animation.addProperty("clientOnly",rule.clientOnly());
            animation.addProperty("nbtPersistent",rule.nbtPersistent());
            value.add("animation",animation);
            value.addProperty("animationProofComplete",true);

            var world=worldAnalyzer.analyze(context.sourceJar(),rule);
            boolean worldComplete=world.presentation().isPresent();
            value.addProperty("presentationProofComplete",worldComplete);
            JsonArray worldDiagnostics=new JsonArray();
            world.diagnostics().forEach(worldDiagnostics::add);
            value.add("presentationDiagnostics",worldDiagnostics);

            boolean inventoryComplete=false,runtimeComplete=false;
            if(worldComplete){
                worldProofs++;
                var presentation=world.presentation().orElseThrow();
                value.add("presentation",presentationJson(presentation));
                var inventory=inventoryAnalyzer.analyze(context.sourceJar(),rule,presentation);
                inventoryComplete=inventory.proof().isPresent();
                JsonArray inventoryDiagnostics=new JsonArray();
                inventory.diagnostics().forEach(inventoryDiagnostics::add);
                value.add("inventoryPresentationDiagnostics",inventoryDiagnostics);
                if(inventoryComplete){
                    inventoryProofs++;
                    var inventoryProof=inventory.proof().orElseThrow();
                    value.add("inventoryPresentation",inventoryJson(inventoryProof));
                    writeInventoryPresentation(context.stagingDir(),id,presentation,inventoryProof);
                    LegacySpecialBlockModelWriter.write(context.stagingDir(),id,"minecraft:block/red_wool");
                    runtimeComplete=true;
                }
            }else{
                value.add("inventoryPresentationDiagnostics",new JsonArray());
            }

            value.addProperty("inventoryPresentationProofComplete",inventoryComplete);
            value.addProperty("worldPresentationRuntimeComplete",runtimeComplete);
            value.addProperty("inventoryPresentationRuntimeComplete",runtimeComplete);
            value.addProperty("runtimeComplete",runtimeComplete);
            rules.add(value);
            emitted++;
        }

        root.add("rules",rules);
        JsonArray skipped=new JsonArray();
        for(var item:analysis.skipped()){
            JsonObject value=new JsonObject();
            value.addProperty("registryName",item.registryName());
            value.addProperty("sourceBlockClass",item.sourceBlockClass());
            value.addProperty("reason",item.reason());
            skipped.add(value);
        }
        root.add("skipped",skipped);
        root.addProperty("presentationProofCompleteRules",worldProofs);
        root.addProperty("inventoryPresentationProofCompleteRules",inventoryProofs);

        Path output=context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message->context.diagnostics().warning(
                "LFB-CONVERT-OSCILLATING-0002",SupportLevel.MANUAL_REQUIRED,message));
        if(emitted>0)context.diagnostics().info(
                "LFB-CONVERT-OSCILLATING-0001",SupportLevel.RUNTIME_BRIDGE,
                "Proven client-only oscillating decorative BlockEntity semantics for "+emitted
                        +" block(s), worldPresentationProof="+worldProofs
                        +", inventoryPresentationProof="+inventoryProofs
                        +"; runtime remains gated.");
    }

    private static void writeInventoryPresentation(Path staging,String idValue,
                                                   LegacyOscillatingModelPresentationAnalyzer.Presentation p,
                                                   LegacyOscillatingModelInventoryAnalyzer.Proof inventory)throws Exception{
        int separator=idValue.indexOf(':');
        if(separator<=0||separator==idValue.length()-1)throw new IllegalArgumentException("Invalid oscillating id "+idValue);
        String namespace=idValue.substring(0,separator),path=idValue.substring(separator+1),baseName=path+"_oscillating_base";
        JsonObject base=new JsonObject();base.addProperty("parent","minecraft:block/block");
        writeJson(staging.resolve("assets/"+namespace+"/models/item/"+baseName+".json"),base);
        JsonObject special=new JsonObject();
        special.addProperty("type","legacyforgebridge:oscillating_model");
        special.addProperty("texture",p.texture());
        special.addProperty("texture_width",p.modelTextureWidth());
        special.addProperty("texture_height",p.modelTextureHeight());
        JsonArray parts=new JsonArray();
        for(var source:p.parts()){
            JsonObject part=new JsonObject();
            part.addProperty("field",source.field());part.addProperty("u",source.u());part.addProperty("v",source.v());
            part.addProperty("x",source.x());part.addProperty("y",source.y());part.addProperty("z",source.z());
            part.addProperty("width",source.width());part.addProperty("height",source.height());part.addProperty("depth",source.depth());
            part.addProperty("pivot_x",source.pivotX());part.addProperty("pivot_y",source.pivotY());part.addProperty("pivot_z",source.pivotZ());
            part.addProperty("mirror",source.mirror());part.addProperty("base_x_rot",source.baseXRot());
            part.addProperty("base_y_rot",source.baseYRot());part.addProperty("base_z_rot",source.baseZRot());part.addProperty("animated",source.animated());
            parts.add(part);
        }
        special.add("parts",parts);
        special.addProperty("yaw_degrees",inventory.yawDegrees());
        special.addProperty("translate_y",inventory.translateY());
        special.addProperty("scale",inventory.scale());
        special.addProperty("dynamic_angle_degrees",inventory.dynamicAngleDegrees());
        JsonObject model=new JsonObject();model.addProperty("type","minecraft:special");model.addProperty("base",namespace+":item/"+baseName);model.add("model",special);
        JsonObject item=new JsonObject();item.add("model",model);
        writeJson(staging.resolve("assets/"+namespace+"/items/"+path+".json"),item);
    }

    private static void writeJson(Path path,JsonObject value)throws Exception{
        Files.createDirectories(path.getParent());Files.writeString(path,GSON.toJson(value)+"\n",StandardCharsets.UTF_8);
    }

    private static JsonObject presentationJson(LegacyOscillatingModelPresentationAnalyzer.Presentation p){
        JsonObject value=new JsonObject();
        value.addProperty("sourceRendererClass",p.sourceRendererClass());
        value.addProperty("clientTileId",p.clientTileId());
        value.addProperty("sourceModelClass",p.sourceModelClass());
        value.addProperty("texture",p.texture());
        value.addProperty("imageWidth",p.imageWidth());
        value.addProperty("imageHeight",p.imageHeight());
        value.addProperty("modelTextureWidth",p.modelTextureWidth());
        value.addProperty("modelTextureHeight",p.modelTextureHeight());
        value.addProperty("animatedPartField",p.animatedPartField());
        value.addProperty("modelScale",p.modelScale());
        value.addProperty("translateX",p.translateX());
        value.addProperty("translateY",p.translateY());
        value.addProperty("translateZ",p.translateZ());
        value.addProperty("metadataMask",p.metadataMask());
        value.addProperty("yawDegreesPerMeta",p.yawDegreesPerMeta());
        value.addProperty("yawOffsetDegrees",p.yawOffsetDegrees());
        value.addProperty("whiteColor",p.whiteColor());
        value.addProperty("dynamicAngleUsesDegrees",p.dynamicAngleUsesDegrees());
        value.addProperty("inventoryPresentationProven",p.inventoryPresentationProven());
        JsonArray parts=new JsonArray();
        for(var part:p.parts()){
            JsonObject item=new JsonObject();
            item.addProperty("field",part.field());
            item.addProperty("u",part.u());item.addProperty("v",part.v());
            item.addProperty("x",part.x());item.addProperty("y",part.y());item.addProperty("z",part.z());
            item.addProperty("width",part.width());item.addProperty("height",part.height());item.addProperty("depth",part.depth());
            item.addProperty("pivotX",part.pivotX());item.addProperty("pivotY",part.pivotY());item.addProperty("pivotZ",part.pivotZ());
            item.addProperty("mirror",part.mirror());
            item.addProperty("baseXRot",part.baseXRot());item.addProperty("baseYRot",part.baseYRot());item.addProperty("baseZRot",part.baseZRot());
            item.addProperty("animated",part.animated());
            parts.add(item);
        }
        value.add("parts",parts);
        return value;
    }

    private static JsonObject inventoryJson(LegacyOscillatingModelInventoryAnalyzer.Proof p){
        JsonObject value=new JsonObject();
        value.addProperty("handlerClass",p.handlerClass());
        value.addProperty("uidField",p.uidField());
        value.addProperty("routeClass",p.routeClass());
        value.addProperty("inventoryMapField",p.inventoryMapField());
        value.addProperty("inventoryRendererClass",p.inventoryRendererClass());
        value.addProperty("sourceRendererClass",p.sourceRendererClass());
        value.addProperty("yawDegrees",p.yawDegrees());
        value.addProperty("translateY",p.translateY());
        value.addProperty("scale",p.scale());
        value.addProperty("dynamicAngleDegrees",p.dynamicAngleDegrees());
        return value;
    }

    private static Map<String,String> generatedBlockIds(Path staging)throws Exception{
        Path path=staging.resolve("legacyforgebridge/converted-content.json");
        if(!Files.isRegularFile(path))return Map.of();
        Map<String,String> result=new LinkedHashMap<>();
        try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            JsonObject content=JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray blocks=content.getAsJsonArray("blocks");
            if(blocks!=null)for(var element:blocks){
                if(!element.isJsonObject())continue;
                JsonObject block=element.getAsJsonObject();
                if(block.has("sourceClass")&&block.has("id"))result.put(block.get("sourceClass").getAsString(),block.get("id").getAsString());
            }
        }
        return result;
    }
}
