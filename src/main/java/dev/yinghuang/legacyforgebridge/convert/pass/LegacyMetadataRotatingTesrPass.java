package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyMetadataRotatingTesrAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes source-proven client presentation for TESRs whose roll speed is raw block metadata. */
public final class LegacyMetadataRotatingTesrPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/metadata-rotating-tesr-rules.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-metadata-rotating-tesr";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyMetadataRotatingTesrAnalyzer().analyze(context.sourceJar());
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
            value.addProperty("animatedPart",rule.animatedPart());value.addProperty("modelScale",rule.modelScale());
            value.addProperty("translateX",rule.translateX());value.addProperty("translateY",rule.translateY());value.addProperty("translateZ",rule.translateZ());
            value.addProperty("metadataMask",rule.metadataMask());value.addProperty("degreesPerMetadataPerTick",rule.degreesPerMetadataPerTick());
            value.addProperty("inventoryStaticZeroAngle",rule.inventoryStaticZeroAngle());
            JsonArray cuboids=new JsonArray();for(var c:rule.cuboids()){
                JsonObject cube=new JsonObject();cube.addProperty("field",c.field());cube.addProperty("u",c.u());cube.addProperty("v",c.v());
                cube.addProperty("x",c.x());cube.addProperty("y",c.y());cube.addProperty("z",c.z());
                cube.addProperty("width",c.width());cube.addProperty("height",c.height());cube.addProperty("depth",c.depth());
                cube.addProperty("pivotX",c.pivotX());cube.addProperty("pivotY",c.pivotY());cube.addProperty("pivotZ",c.pivotZ());
                cube.addProperty("mirror",c.mirror());cuboids.add(cube);
            }value.add("cuboids",cuboids);
            value.addProperty("worldPresentationRuntimeComplete",true);
            value.addProperty("inventoryPresentationRuntimeComplete",true);
            value.addProperty("runtimeComplete",true);
            writeInventory(context.stagingDir(),id,rule);
            LegacySpecialBlockModelWriter.write(context.stagingDir(),id,"minecraft:block/stone");
            rules.add(value);runtime++;
        }
        root.add("rules",rules);root.addProperty("runtimeCompleteRules",runtime);
        JsonArray skipped=new JsonArray();for(var s:analysis.skipped()){JsonObject v=new JsonObject();v.addProperty("registryName",s.registryName());v.addProperty("sourceBlockClass",s.sourceBlockClass());v.addProperty("reason",s.reason());skipped.add(v);}root.add("skipped",skipped);
        JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);root.add("analysisDiagnostics",diagnostics);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(runtime>0)context.diagnostics().info("LFB-CONVERT-META-ROTATE-0001",SupportLevel.ADAPTED,
                "Source-proven metadata-speed rotating TESR presentation rules="+runtime+". Gameplay TileEntity state remains legacy-server authoritative.");
    }

    private static void writeInventory(Path staging,String idValue,LegacyMetadataRotatingTesrAnalyzer.Rule rule)throws Exception{
        int split=idValue.indexOf(':');if(split<=0)throw new IllegalArgumentException("Invalid id "+idValue);
        String namespace=idValue.substring(0,split),path=idValue.substring(split+1),baseName=path+"_metadata_rotating_base";
        JsonObject base=new JsonObject();base.addProperty("parent","minecraft:block/block");
        writeJson(staging.resolve("assets/"+namespace+"/models/item/"+baseName+".json"),base);
        JsonObject special=new JsonObject();special.addProperty("type","legacyforgebridge:metadata_rotating_model");
        special.addProperty("texture",rule.texture());special.addProperty("texture_width",rule.textureWidth());special.addProperty("texture_height",rule.textureHeight());
        JsonArray cubes=new JsonArray();for(var c:rule.cuboids()){
            JsonObject cube=new JsonObject();cube.addProperty("u",c.u());cube.addProperty("v",c.v());cube.addProperty("x",c.x());cube.addProperty("y",c.y());cube.addProperty("z",c.z());
            cube.addProperty("width",c.width());cube.addProperty("height",c.height());cube.addProperty("depth",c.depth());
            cube.addProperty("pivot_x",c.pivotX());cube.addProperty("pivot_y",c.pivotY());cube.addProperty("pivot_z",c.pivotZ());cube.addProperty("mirror",c.mirror());cubes.add(cube);
        }special.add("cuboids",cubes);special.addProperty("translate_x",0F);special.addProperty("translate_y",0F);special.addProperty("translate_z",0F);special.addProperty("scale",1F);
        JsonObject model=new JsonObject();model.addProperty("type","minecraft:special");model.addProperty("base",namespace+":item/"+baseName);model.add("model",special);
        JsonObject item=new JsonObject();item.add("model",model);writeJson(staging.resolve("assets/"+namespace+"/items/"+path+".json"),item);
    }
    private static void writeJson(Path path,JsonObject value)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
    private static Map<String,String> blockIds(Path staging)throws Exception{
        Path path=staging.resolve(LegacyClientContentBaselinePass.CONTENT);if(!Files.isRegularFile(path))return Map.of();
        Map<String,String> out=new LinkedHashMap<>();try(Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            JsonArray blocks=JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("blocks");if(blocks!=null)for(JsonElement e:blocks){
                if(!e.isJsonObject())continue;JsonObject b=e.getAsJsonObject();if(b.has("sourceClass")&&b.has("id"))out.put(b.get("sourceClass").getAsString(),b.get("id").getAsString());
            }
        }return out;
    }
}
