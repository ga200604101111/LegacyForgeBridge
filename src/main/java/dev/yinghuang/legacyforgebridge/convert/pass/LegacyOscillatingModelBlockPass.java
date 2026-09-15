package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyOscillatingModelBlockAnalyzer;
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
    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyOscillatingModelBlockAnalyzer().analyze(context.sourceJar());if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;
        Map<String,String> ids=generatedBlockIds(context.stagingDir());JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());JsonArray rules=new JsonArray();int emitted=0;
        for(var rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());if(id==null)continue;JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("sourceBlockClass",rule.sourceBlockClass());value.addProperty("sourceTileClass",rule.sourceTileClass());value.addProperty("legacyTileId",rule.legacyTileId());JsonArray orientation=new JsonArray();for(int meta:rule.placementMetaByYawQuadrant())orientation.add(meta);value.add("placementMetaByYawQuadrant",orientation);value.addProperty("fullCube",rule.fullCube());value.addProperty("lightEmission",rule.lightEmission());value.addProperty("nonOpaque",rule.nonOpaque());value.addProperty("nonNormalRender",rule.nonNormalRender());value.addProperty("legacyRenderPass",rule.renderPass());JsonObject animation=new JsonObject();animation.addProperty("angleField",rule.angleField());animation.addProperty("directionField",rule.directionField());animation.addProperty("randomInitialBound",rule.randomInitialBound());animation.addProperty("stepDegrees",rule.stepDegrees());animation.addProperty("lowerBoundDegrees",rule.lowerBoundDegrees());animation.addProperty("upperBoundDegrees",rule.upperBoundDegrees());animation.addProperty("clientOnly",rule.clientOnly());animation.addProperty("nbtPersistent",rule.nbtPersistent());value.add("animation",animation);value.addProperty("animationProofComplete",true);value.addProperty("presentationProofComplete",false);value.addProperty("runtimeComplete",false);rules.add(value);emitted++;}
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("registryName",item.registryName());value.addProperty("sourceBlockClass",item.sourceBlockClass());value.addProperty("reason",item.reason());skipped.add(value);}root.add("skipped",skipped);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);analysis.diagnostics().forEach(message->context.diagnostics().warning("LFB-CONVERT-OSCILLATING-0002",SupportLevel.MANUAL_REQUIRED,message));if(emitted>0)context.diagnostics().info("LFB-CONVERT-OSCILLATING-0001",SupportLevel.RUNTIME_BRIDGE,"Proven client-only oscillating decorative BlockEntity semantics for "+emitted+" block(s); presentation/runtime remain gated.");
    }
    private static Map<String,String> generatedBlockIds(Path staging)throws Exception{Path path=staging.resolve("legacyforgebridge/converted-content.json");if(!Files.isRegularFile(path))return Map.of();Map<String,String> result=new LinkedHashMap<>();try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){JsonObject content=JsonParser.parseReader(reader).getAsJsonObject();JsonArray blocks=content.getAsJsonArray("blocks");if(blocks!=null)for(var element:blocks){if(!element.isJsonObject())continue;JsonObject block=element.getAsJsonObject();if(block.has("sourceClass")&&block.has("id"))result.put(block.get("sourceClass").getAsString(),block.get("id").getAsString());}}return result;}
}
