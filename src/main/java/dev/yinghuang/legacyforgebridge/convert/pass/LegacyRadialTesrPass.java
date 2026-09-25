package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyRadialConditionalAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRadialTesrAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes source-proven radial TESR bases plus separately proven metadata-selected sub-groups. */
public final class LegacyRadialTesrPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/radial-tesr-rules.json";
    public static final String CONDITIONAL_OUTPUT="legacyforgebridge/radial-tesr-conditional-rules.json";
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
            value.addProperty("inventoryPresentationRuntimeComplete",true);
            value.addProperty("sourcePresentationComplete",rule.conditionalModelCalls()==0);
            writeInventoryPresentation(context.stagingDir(),id,rule);
            LegacySpecialBlockModelWriter.write(context.stagingDir(),id,"minecraft:block/stone");
            rules.add(value);runtime++;
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteBaseRules",runtime);
        JsonArray skipped=new JsonArray();for(var s:analysis.skipped()){JsonObject v=new JsonObject();v.addProperty("registryName",s.registryName());v.addProperty("sourceBlockClass",s.sourceBlockClass());v.addProperty("reason",s.reason());skipped.add(v);}root.add("skipped",skipped);
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("analysisDiagnostics",diagnostics);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);

        int conditionalRuntime=writeConditionalRules(context,ids,analysis.rules());
        if(runtime>0)context.diagnostics().info("LFB-CONVERT-RADIAL-0001",SupportLevel.ADAPTED,
                "Source-proven radial TESR base runtime rules="+runtime+"; source-proven conditional subgroup rules="+conditionalRuntime+".");
    }

    private static int writeConditionalRules(ConversionContext context,Map<String,String> ids,List<LegacyRadialTesrAnalyzer.Rule> bases)throws Exception{
        var analysis=new LegacyRadialConditionalAnalyzer().analyze(context.sourceJar(),bases);
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return 0;
        for(var skipped:analysis.skipped())if(ids.containsKey(skipped.sourceBlockClass()))
            context.diagnostics().warning("LFB-CONVERT-RADIAL-0002",SupportLevel.RUNTIME_BRIDGE,
                    "Conditional radial TESR presentation remains unadapted for "+skipped.registryName()+": "+skipped.reason());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();int runtime=0;
        for(var rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());if(id==null)continue;
            JsonObject value=new JsonObject();value.addProperty("id",id);
            value.addProperty("sourceBlockClass",rule.sourceBlockClass());value.addProperty("sourceTileClass",rule.sourceTileClass());
            value.addProperty("sourceRendererClass",rule.sourceRendererClass());value.addProperty("sourceModelClass",rule.sourceModelClass());
            value.addProperty("metadataShift",rule.metadataShift());value.addProperty("selectorMask",rule.selectorMask());
            value.addProperty("coveredModelCalls",rule.coveredModelCalls());
            value.addProperty("conditionalPresentationRuntimeComplete",true);
            JsonArray groups=new JsonArray();
            for(var group:rule.groups()){
                JsonObject groupValue=new JsonObject();groupValue.addProperty("selectorValue",group.selectorValue());
                JsonArray parts=new JsonArray();
                for(var part:group.parts()){
                    JsonObject partValue=new JsonObject();var c=part.cuboid();JsonObject cube=new JsonObject();
                    cube.addProperty("u",c.u());cube.addProperty("v",c.v());cube.addProperty("x",c.x());cube.addProperty("y",c.y());cube.addProperty("z",c.z());
                    cube.addProperty("width",c.width());cube.addProperty("height",c.height());cube.addProperty("depth",c.depth());
                    cube.addProperty("pivotX",c.pivotX());cube.addProperty("pivotY",c.pivotY());cube.addProperty("pivotZ",c.pivotZ());partValue.add("cuboid",cube);
                    JsonArray poses=new JsonArray();for(var pose:part.poses()){
                        JsonObject poseValue=new JsonObject();poseValue.addProperty("xRot",pose.xRot());poseValue.addProperty("yRot",pose.yRot());poseValue.addProperty("zRot",pose.zRot());poses.add(poseValue);
                    }partValue.add("poses",poses);
                    if(part.animation()!=null){
                        JsonObject animation=new JsonObject();animation.addProperty("axis",part.animation().axis().name());
                        animation.addProperty("degreesPerTick",part.animation().degreesPerTick());animation.addProperty("periodTicks",part.animation().periodTicks());
                        animation.addProperty("randomizedPhase",part.animation().randomizedPhase());partValue.add("animation",animation);
                    }
                    parts.add(partValue);
                }
                groupValue.add("parts",parts);groups.add(groupValue);
            }
            value.add("groups",groups);rules.add(value);runtime++;
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteRules",runtime);
        JsonArray skipped=new JsonArray();for(var entry:analysis.skipped()){
            JsonObject value=new JsonObject();value.addProperty("registryName",entry.registryName());value.addProperty("sourceBlockClass",entry.sourceBlockClass());value.addProperty("reason",entry.reason());skipped.add(value);
        }root.add("skipped",skipped);
        Path output=context.stagingDir().resolve(CONDITIONAL_OUTPUT);Files.createDirectories(output.getParent());
        Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        return runtime;
    }

    private static void writeInventoryPresentation(Path staging,String idValue,LegacyRadialTesrAnalyzer.Rule rule)throws Exception{
        int separator=idValue.indexOf(':');
        if(separator<=0||separator==idValue.length()-1)throw new IllegalArgumentException("Invalid radial id "+idValue);
        String namespace=idValue.substring(0,separator),path=idValue.substring(separator+1),baseName=path+"_radial_base";

        JsonObject base=new JsonObject();base.addProperty("parent","minecraft:block/block");
        writeJson(staging.resolve("assets/"+namespace+"/models/item/"+baseName+".json"),base);

        JsonObject special=new JsonObject();
        special.addProperty("type","legacyforgebridge:radial_model");
        special.addProperty("texture",rule.texture());
        special.addProperty("texture_width",rule.textureWidth());
        special.addProperty("texture_height",rule.textureHeight());
        var source=rule.cuboid();JsonObject cuboid=new JsonObject();
        cuboid.addProperty("u",source.u());cuboid.addProperty("v",source.v());
        cuboid.addProperty("x",source.x());cuboid.addProperty("y",source.y());cuboid.addProperty("z",source.z());
        cuboid.addProperty("width",source.width());cuboid.addProperty("height",source.height());cuboid.addProperty("depth",source.depth());
        cuboid.addProperty("pivot_x",source.pivotX());cuboid.addProperty("pivot_y",source.pivotY());cuboid.addProperty("pivot_z",source.pivotZ());
        special.add("cuboid",cuboid);
        JsonArray poses=new JsonArray();for(var sourcePose:rule.poses()){
            JsonObject pose=new JsonObject();pose.addProperty("x_rot",sourcePose.xRot());pose.addProperty("y_rot",sourcePose.yRot());pose.addProperty("z_rot",sourcePose.zRot());poses.add(pose);
        }
        special.add("poses",poses);
        special.addProperty("translate_y",rule.inventoryTranslateY());
        special.addProperty("scale",rule.inventoryScale());

        JsonObject model=new JsonObject();model.addProperty("type","minecraft:special");model.addProperty("base",namespace+":item/"+baseName);model.add("model",special);
        JsonObject item=new JsonObject();item.add("model",model);
        writeJson(staging.resolve("assets/"+namespace+"/items/"+path+".json"),item);
    }

    private static void writeJson(Path path,JsonObject value)throws Exception{
        Files.createDirectories(path.getParent());
        Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);
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
