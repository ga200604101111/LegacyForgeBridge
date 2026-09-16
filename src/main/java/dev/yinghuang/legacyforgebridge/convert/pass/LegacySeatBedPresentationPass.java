package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySeatBedAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySeatBedPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits special client presentation only for source-proven two-part bed/seat TESR families. */
public final class LegacySeatBedPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/seat-bed-presentation-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-seat-bed-presentation";}

    @Override public void apply(ConversionContext context)throws Exception{
        LegacySeatBedAnalyzer.Analysis source=new LegacySeatBedAnalyzer().analyze(context.sourceJar());
        if(source.rules().isEmpty()&&source.skipped().isEmpty())return;
        Map<String,String> ids=generatedBlockIds(context.stagingDir());
        LegacySeatBedPresentationAnalyzer analyzer=new LegacySeatBedPresentationAnalyzer();
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();int complete=0,unmapped=0;
        for(var rule:source.rules()){
            if(!rule.specialPresentationRequired())continue;
            String id=ids.get(rule.sourceBlockClass());if(id==null){unmapped++;continue;}
            var analysis=analyzer.analyze(context.sourceJar(),rule);
            if(analysis.presentation().isEmpty()){
                analysis.diagnostics().forEach(message->context.diagnostics().warning("LFB-CONVERT-SEATBED-PRESENT-0002",SupportLevel.MANUAL_REQUIRED,id+": "+message));
                continue;
            }
            JsonObject value=presentationJson(analysis.presentation().orElseThrow());value.addProperty("id",id);value.addProperty("sourceBlockClass",rule.sourceBlockClass());value.addProperty("presentationProofComplete",true);value.addProperty("runtimeImplementationWired",true);rules.add(value);complete++;
        }
        root.add("rules",rules);root.addProperty("presentationRuntimeCompleteRules",complete);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(unmapped>0)context.diagnostics().warning("LFB-CONVERT-SEATBED-PRESENT-0003",SupportLevel.MANUAL_REQUIRED,"Source-proven seat-bed presentation without generated block identity: "+unmapped+".");
        if(complete>0)context.diagnostics().info("LFB-CONVERT-SEATBED-PRESENT-0001",SupportLevel.ADAPTED,"Proof-gated seat-bed special presentation runtimes: "+complete+".");
    }

    private static JsonObject presentationJson(LegacySeatBedPresentationAnalyzer.Presentation p){
        JsonObject v=new JsonObject();v.addProperty("sourceRendererClass",p.sourceRendererClass());v.addProperty("sourceModelClass",p.sourceModelClass());v.addProperty("footTexture",p.footTexture());v.addProperty("headTexture",p.headTexture());v.addProperty("imageWidth",p.imageWidth());v.addProperty("imageHeight",p.imageHeight());v.addProperty("modelTextureWidth",p.modelTextureWidth());v.addProperty("modelTextureHeight",p.modelTextureHeight());v.addProperty("modelScale",p.modelScale());v.addProperty("expandedRenderBoundsProven",p.expandedRenderBoundsProven());
        JsonArray parts=new JsonArray();for(var p0:p.parts()){JsonObject x=new JsonObject();x.addProperty("field",p0.field());x.addProperty("u",p0.u());x.addProperty("v",p0.v());x.addProperty("x",p0.x());x.addProperty("y",p0.y());x.addProperty("z",p0.z());x.addProperty("width",p0.width());x.addProperty("height",p0.height());x.addProperty("depth",p0.depth());x.addProperty("pivotX",p0.pivotX());x.addProperty("pivotY",p0.pivotY());x.addProperty("pivotZ",p0.pivotZ());x.addProperty("xRot",p0.xRot());x.addProperty("yRot",p0.yRot());x.addProperty("zRot",p0.zRot());x.addProperty("mirror",p0.mirror());parts.add(x);}v.add("parts",parts);
        v.add("footParts",strings(p.footParts()));v.add("headParts",strings(p.headParts()));v.add("translateXByDirection",floats(p.translateXByDirection()));v.add("translateZByDirection",floats(p.translateZByDirection()));v.add("yawDegreesByDirection",floats(p.yawDegreesByDirection()));return v;
    }
    private static JsonArray strings(java.util.List<String> values){JsonArray a=new JsonArray();values.forEach(a::add);return a;}
    private static JsonArray floats(java.util.List<Float> values){JsonArray a=new JsonArray();values.forEach(a::add);return a;}
    private static Map<String,String> generatedBlockIds(Path staging)throws Exception{Path path=staging.resolve("legacyforgebridge/converted-content.json");if(!Files.isRegularFile(path))return Map.of();Map<String,String> out=new LinkedHashMap<>();try(Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){JsonArray blocks=JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("blocks");if(blocks!=null)for(var e:blocks){if(!e.isJsonObject())continue;JsonObject b=e.getAsJsonObject();if(b.has("sourceClass")&&b.has("id"))out.put(b.get("sourceClass").getAsString(),b.get("id").getAsString());}}return out;}
}
