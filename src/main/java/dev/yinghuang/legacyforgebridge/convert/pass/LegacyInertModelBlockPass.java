package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyInertModelBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyInertModelPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits source-proven inert BlockContainer topology and fixed-cuboid TESR evidence; runtime stays gated. */
public final class LegacyInertModelBlockPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/inert-model-block-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-inert-model-blocks";}
    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyInertModelBlockAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;
        Map<String,String> ids=generatedBlockIds(context.stagingDir());JsonObject root=new JsonObject();root.addProperty("schemaVersion",2);root.addProperty("sourceSha256",context.sourceHash());JsonArray rules=new JsonArray();int unmapped=0,provenPresentation=0,inventoryTransform=0;
        LegacyInertModelPresentationAnalyzer presentationAnalyzer=new LegacyInertModelPresentationAnalyzer();
        for(var rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());if(id==null){unmapped++;continue;}
            JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("sourceBlockClass",rule.sourceBlockClass());value.addProperty("sourceTileClass",rule.sourceTileClass());value.addProperty("legacyTileId",rule.legacyTileId());
            JsonObject bounds=new JsonObject();bounds.addProperty("minX",rule.bounds().minX());bounds.addProperty("minY",rule.bounds().minY());bounds.addProperty("minZ",rule.bounds().minZ());bounds.addProperty("maxX",rule.bounds().maxX());bounds.addProperty("maxY",rule.bounds().maxY());bounds.addProperty("maxZ",rule.bounds().maxZ());value.add("bounds",bounds);
            value.addProperty("sourceLightLevel",rule.sourceLightLevel());value.addProperty("modernLightEmission",rule.modernLightEmission());value.addProperty("orientation",rule.orientation());value.addProperty("nonOpaque",rule.nonOpaque());value.addProperty("nonNormalRender",rule.nonNormalRender());value.addProperty("legacyRenderPass",rule.renderPass());value.addProperty("topologyProofComplete",true);
            var presentation=presentationAnalyzer.analyze(context.sourceJar(),rule);boolean complete=presentation.presentation().isPresent();value.addProperty("presentationProofComplete",complete);JsonArray presentationDiagnostics=new JsonArray();presentation.diagnostics().forEach(presentationDiagnostics::add);value.add("presentationDiagnostics",presentationDiagnostics);
            value.addProperty("worldPresentationRuntimeComplete",false);value.addProperty("inventoryPresentationRuntimeComplete",false);value.addProperty("runtimeComplete",false);
            if(complete){provenPresentation++;var proof=presentation.presentation().orElseThrow();if(proof.inventoryTransformProven())inventoryTransform++;value.add("presentation",presentationJson(proof));}
            rules.add(value);
        }
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("registryName",item.registryName());value.addProperty("sourceBlockClass",item.sourceBlockClass());value.addProperty("reason",item.reason());skipped.add(value);}root.add("skipped",skipped);root.addProperty("presentationProofCompleteRules",provenPresentation);root.addProperty("inventoryTransformProvenRules",inventoryTransform);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message->context.diagnostics().warning("LFB-CONVERT-INERT-0002",SupportLevel.MANUAL_REQUIRED,message));
        if(unmapped>0)context.diagnostics().warning("LFB-CONVERT-INERT-0003",SupportLevel.MANUAL_REQUIRED,"Source-proven inert model blocks without generated block identity: "+unmapped+".");
        if(!rules.isEmpty())context.diagnostics().info("LFB-CONVERT-INERT-0001",SupportLevel.RUNTIME_BRIDGE,"Proven inert BlockContainer topology: "+rules.size()+", fixed-cuboid presentation="+provenPresentation+"; runtime remains gated.");
    }
    private static JsonObject presentationJson(LegacyInertModelPresentationAnalyzer.Presentation p){JsonObject value=new JsonObject();value.addProperty("sourceRendererClass",p.sourceRendererClass());value.addProperty("sourceModelClass",p.sourceModelClass());value.addProperty("texture",p.texture());value.addProperty("imageWidth",p.imageWidth());value.addProperty("imageHeight",p.imageHeight());value.addProperty("modelTextureWidth",p.modelTextureWidth());value.addProperty("modelTextureHeight",p.modelTextureHeight());value.addProperty("modelScale",p.modelScale());value.addProperty("translateX",p.translateX());value.addProperty("translateY",p.translateY());value.addProperty("translateZ",p.translateZ());value.addProperty("metadataMask",p.metadataMask());value.addProperty("yawDegreesPerMeta",p.yawDegreesPerMeta());value.addProperty("whiteColor",p.whiteColor());value.addProperty("inventoryTransformProven",p.inventoryTransformProven());value.addProperty("inventoryTranslateY",p.inventoryTranslateY());value.addProperty("inventoryScale",p.inventoryScale());JsonArray cuboids=new JsonArray();for(var c:p.cuboids()){JsonObject box=new JsonObject();box.addProperty("field",c.field());box.addProperty("u",c.u());box.addProperty("v",c.v());box.addProperty("x",c.x());box.addProperty("y",c.y());box.addProperty("z",c.z());box.addProperty("width",c.width());box.addProperty("height",c.height());box.addProperty("depth",c.depth());box.addProperty("pivotX",c.pivotX());box.addProperty("pivotY",c.pivotY());box.addProperty("pivotZ",c.pivotZ());box.addProperty("worldX",c.worldX());box.addProperty("worldY",c.worldY());box.addProperty("worldZ",c.worldZ());cuboids.add(box);}value.add("cuboids",cuboids);return value;}
    private static Map<String,String> generatedBlockIds(Path staging)throws Exception{Path path=staging.resolve("legacyforgebridge/converted-content.json");if(!Files.isRegularFile(path))return Map.of();Map<String,String> result=new LinkedHashMap<>();try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){JsonObject content=JsonParser.parseReader(reader).getAsJsonObject();JsonArray blocks=content.getAsJsonArray("blocks");if(blocks!=null)for(var element:blocks){if(!element.isJsonObject())continue;JsonObject block=element.getAsJsonObject();if(block.has("sourceClass")&&block.has("id"))result.put(block.get("sourceClass").getAsString(),block.get("id").getAsString());}}return result;}
}
