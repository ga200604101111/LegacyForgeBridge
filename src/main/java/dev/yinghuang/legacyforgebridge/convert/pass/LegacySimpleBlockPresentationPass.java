package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacySimpleBlockRendererAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Final model ownership pass for source-proven simple legacy block renderer families.
 *
 * <p>This runs after generic icon/geometry recovery so a proven CROSS/CROP renderer cannot be
 * accidentally left as an ordinary cube merely because an earlier presentation stage recovered
 * a usable texture.</p>
 */
public final class LegacySimpleBlockPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/simple-block-presentation.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-simple-block-presentation";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path staging=context.stagingDir();
        Path contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath);Map<String,String> ids=new HashMap<>();
        if(content.has("blocks"))for(JsonElement element:content.getAsJsonArray("blocks")){
            JsonObject block=element.getAsJsonObject();
            if(block.has("legacyRegistryName")&&block.has("id"))ids.put(block.get("legacyRegistryName").getAsString(),block.get("id").getAsString());
        }

        var analysis=new LegacySimpleBlockRendererAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();int written=0;
        for(var rule:analysis.rules()){
            if(rule.mode()==LegacySimpleBlockRendererAnalyzer.Mode.HELD_ITEM_CROSS)continue;
            String id=ids.get(rule.registryName());if(id==null)continue;String[] split=id.split(":",2);if(split.length!=2)continue;
            String ns=split[0],path=split[1];Path modelPath=staging.resolve("assets/"+ns+"/models/block/"+path+".json");
            if(!Files.isRegularFile(modelPath))continue;
            JsonObject current=read(modelPath);String texture=singleTexture(current);
            if(texture==null)continue;

            JsonObject evidence=new JsonObject();evidence.addProperty("id",id);evidence.addProperty("legacyRegistryName",rule.registryName());
            evidence.addProperty("sourceBlockClass",rule.sourceBlockClass());evidence.addProperty("sourceRendererClass",rule.rendererClass());
            evidence.addProperty("mode",rule.mode().name());evidence.addProperty("texture",texture);

            switch(rule.mode()){
                case CROSS -> {
                    write(modelPath,simpleModel("minecraft:block/cross","cross",texture));
                    writeAllStates(staging,ns,path,ns+":block/"+path);
                    written++;
                }
                case CROP -> {
                    write(modelPath,simpleModel("minecraft:block/crop","crop",texture));
                    writeAllStates(staging,ns,path,ns+":block/"+path);
                    written++;
                }
                case META_ZERO_CROP_ELSE_STANDARD -> {
                    JsonObject standard=current.deepCopy();
                    String cropId=ns+":block/"+path+"_lfb_meta_0_crop";
                    write(modelPath(staging,cropId),simpleModel("minecraft:block/crop","crop",texture));
                    write(modelPath,standard);
                    JsonObject variants=new JsonObject();
                    for(int meta=0;meta<16;meta++){JsonObject state=new JsonObject();state.addProperty("model",meta==0?cropId:ns+":block/"+path);variants.add("legacy_meta="+meta,state);}
                    JsonObject blockstate=new JsonObject();blockstate.add("variants",variants);write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),blockstate);
                    written++;
                }
                default -> { continue; }
            }
            evidence.addProperty("finalModelOwnership",true);rules.add(evidence);
        }
        root.add("rules",rules);root.addProperty("finalModelsWritten",written);
        Path output=staging.resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(written>0)context.diagnostics().info("LFB-CONVERT-SIMPLE-RENDER-0001",SupportLevel.ADAPTED,
                "Final simple-renderer block models written="+written+"; source-proven CROSS/CROP ownership overrides generic cube presentation.");
    }

    private static String singleTexture(JsonObject model){
        if(!model.has("textures")||!model.get("textures").isJsonObject())return null;
        JsonObject textures=model.getAsJsonObject("textures");LinkedHashSet<String> concrete=new LinkedHashSet<>();
        for(var entry:textures.entrySet())if(entry.getValue().isJsonPrimitive()){
            String value=entry.getValue().getAsString();if(value!=null&&!value.isBlank()&&!value.startsWith("#"))concrete.add(value);
        }
        return concrete.size()==1?concrete.getFirst():null;
    }
    private static JsonObject simpleModel(String parent,String key,String texture){
        JsonObject model=new JsonObject(),textures=new JsonObject();model.addProperty("parent",parent);textures.addProperty(key,texture);model.add("textures",textures);return model;
    }
    private static void writeAllStates(Path staging,String ns,String path,String modelId)throws Exception{
        JsonObject variants=new JsonObject();for(int meta=0;meta<16;meta++){JsonObject state=new JsonObject();state.addProperty("model",modelId);variants.add("legacy_meta="+meta,state);}
        JsonObject root=new JsonObject();root.add("variants",variants);write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),root);
    }
    private static Path modelPath(Path staging,String id){String[] p=id.split(":",2);return staging.resolve("assets/"+p[0]+"/models/"+p[1]+".json");}
    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject json)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(json)+"\n",StandardCharsets.UTF_8);}
}
