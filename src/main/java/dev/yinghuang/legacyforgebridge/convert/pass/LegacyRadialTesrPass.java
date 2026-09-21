package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyRadialTesrAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes source-proven unconditional radial TESR bases without claiming conditional groups. */
public final class LegacyRadialTesrPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/radial-tesr-rules.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-radial-tesr-base";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyRadialTesrAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty()&&analysis.diagnostics().isEmpty())return;
        Map<String,String> ids=blockIds(context.stagingDir());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();int runtime=0;
        for(var rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());if(id==null)continue;
            JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("sourceBlockClass",rule.sourceBlockClass());
            value.addProperty("sourceTileClass",rule.sourceTileClass());value.addProperty("legacyTileId",rule.legacyTileId());
            value.addProperty("sourceRendererClass",rule.sourceRendererClass());value.addProperty("sourceModelClass",rule.sourceModelClass());
            value.addProperty("texture",rule.texture());value.addProperty("textureWidth",rule.textureWidth());value.addProperty("textureHeight",rule.textureHeight());
            JsonObject cube=new JsonObject();var c=rule.cuboid();
            cube.addProperty("u",c.u());cube.addProperty("v",c.v());cube.addProperty("x",c.x());cube.addProperty("y",c.y());cube.addProperty("z",c.z());
            cube.addProperty("width",c.width());cube.addProperty("height",c.height());cube.addProperty("depth",c.depth());
            cube.addProperty("pivotX",c.pivotX());cube.addProperty("pivotY",c.pivotY());cube.addProperty("pivotZ",c.pivotZ());value.add("cuboid",cube);
            JsonArray poses=new JsonArray();for(var p:rule.poses()){JsonObject o=new JsonObject();o.addProperty("xRot",p.xRot());o.addProperty("yRot",p.yRot());o.addProperty("zRot",p.zRot());poses.add(o);}value.add("poses",poses);
            value.addProperty("modelScale",rule.modelScale());value.addProperty("translateX",rule.translateX());value.addProperty("translateY",rule.translateY());value.addProperty("translateZ",rule.translateZ());
            value.addProperty("metadataMask",rule.metadataMask());value.addProperty("yawDegreesPerMeta",rule.yawDegreesPerMeta());
            value.addProperty("sourceLightLevel",rule.sourceLightLevel());value.addProperty("modernLightEmission",rule.modernLightEmission());
            value.addProperty("inventoryTranslateY",rule.inventoryTranslateY());value.addProperty("inventoryScale",rule.inventoryScale());
            value.addProperty("conditionalModelCalls",rule.conditionalModelCalls());
            value.addProperty("basePresentationRuntimeComplete",true);
            value.addProperty("sourcePresentationComplete",rule.conditionalModelCalls()==0);
            LegacySpecialBlockModelWriter.write(context.stagingDir(),id,"minecraft:block/stone");
            rules.add(value);runtime++;
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteBaseRules",runtime);
        JsonArray skipped=new JsonArray();for(var s:analysis.skipped()){JsonObject v=new JsonObject();v.addProperty("registryName",s.registryName());v.addProperty("sourceBlockClass",s.sourceBlockClass());v.addProperty("reason",s.reason());skipped.add(v);}root.add("skipped",skipped);
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("analysisDiagnostics",diagnostics);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(runtime>0)context.diagnostics().info("LFB-CONVERT-RADIAL-0001",SupportLevel.ADAPTED,
                "Source-proven radial TESR base runtime rules="+runtime+"; conditional model groups remain explicitly separate.");
    }

    private static Map<String,String> blockIds(Path staging)throws Exception{
        Path path=staging.resolve(LegacyClientContentBaselinePass.CONTENT);if(!Files.isRegularFile(path))return Map.of();
        Map<String,String> out=new LinkedHashMap<>();try(Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            JsonArray blocks=JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("blocks");if(blocks!=null)for(JsonElement e:blocks){
                if(!e.isJsonObject())continue;JsonObject b=e.getAsJsonObject();if(b.has("sourceClass")&&b.has("id"))out.put(b.get("sourceClass").getAsString(),b.get("id").getAsString());
            }
        }return out;
    }
}
