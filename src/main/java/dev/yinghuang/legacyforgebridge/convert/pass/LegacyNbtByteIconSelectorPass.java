package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyNbtByteIconSelectorAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Materializes source-proven NBT-byte-selected legacy icon arrays into modern item model identities. */
public final class LegacyNbtByteIconSelectorPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/nbt-byte-icon-selectors.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-nbt-byte-icon-selector";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path staging=context.stagingDir(),contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=JsonParser.parseString(Files.readString(contentPath,StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String,JsonObject> bySource=new LinkedHashMap<>();
        JsonArray items=content.getAsJsonArray("items");
        if(items!=null)for(JsonElement element:items){
            if(!element.isJsonObject())continue;JsonObject item=element.getAsJsonObject();
            if(item.has("sourceClass")&&item.has("id"))bySource.put(item.get("sourceClass").getAsString(),item);
        }

        var analysis=new LegacyNbtByteIconSelectorAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();JsonArray skipped=new JsonArray();int written=0;
        for(var source:analysis.rules()){
            JsonObject def=bySource.get(source.sourceClass());if(def==null)continue;
            String id=def.get("id").getAsString();String[] split=id.split(":",2);if(split.length!=2)continue;
            String ns=split[0],path=split[1];List<String> definitions=new ArrayList<>();boolean complete=true;
            for(int index=0;index<source.variants();index++){
                String sprite=LegacyIconPresentationPass.resolve(staging,source.texturePrefix()+index,false);
                if(sprite==null){complete=false;break;}
                String modelId=ns+":item/"+path+"_lfb_nbt_"+index;
                JsonObject model=new JsonObject(),textures=new JsonObject();model.addProperty("parent","minecraft:item/generated");
                textures.addProperty("layer0",sprite);model.add("textures",textures);
                write(staging.resolve("assets/"+ns+"/models/item/"+path+"_lfb_nbt_"+index+".json"),model);

                String definitionId=ns+":lfb_nbt/"+path+"/"+index;
                JsonObject node=new JsonObject();node.addProperty("type","minecraft:model");node.addProperty("model",modelId);
                JsonObject itemDef=new JsonObject();itemDef.add("model",node);
                write(staging.resolve("assets/"+ns+"/items/lfb_nbt/"+path+"/"+index+".json"),itemDef);
                definitions.add(definitionId);
            }
            if(!complete){
                skipped.add(skip(id,source.sourceClass(),"One or more source-proven NBT icon sprites could not be resolved."));
                continue;
            }

            String defaultDefinition=definitions.get(source.defaultIndex());
            JsonObject defaultNode=new JsonObject();defaultNode.addProperty("type","minecraft:model");
            defaultNode.addProperty("model",ns+":item/"+path+"_lfb_nbt_"+source.defaultIndex());
            JsonObject defaultRoot=new JsonObject();defaultRoot.add("model",defaultNode);
            write(staging.resolve("assets/"+ns+"/items/"+path+".json"),defaultRoot);

            JsonObject rule=new JsonObject();rule.addProperty("id",id);rule.addProperty("sourceClass",source.sourceClass());
            rule.addProperty("nbtKey",source.nbtKey());rule.addProperty("defaultIndex",source.defaultIndex());
            rule.addProperty("variantCount",source.variants());rule.addProperty("texturePrefix",source.texturePrefix());
            JsonArray models=new JsonArray();definitions.forEach(models::add);rule.add("itemModels",models);
            rule.addProperty("runtimeComplete",true);rules.add(rule);written++;
        }
        for(var item:analysis.skipped())skipped.add(skip(item.registryName(),item.sourceClass(),item.reason()));
        root.add("rules",rules);root.add("skipped",skipped);root.addProperty("runtimeCompleteRules",written);
        Path output=staging.resolve(OUTPUT);Files.createDirectories(output.getParent());
        Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(written>0)context.diagnostics().info("LFB-CONVERT-NBT-ICON-0001",SupportLevel.ADAPTED,
                "Materialized source-proven NBT byte icon selector rules="+written+"; source NBT remains server-owned and only selects client item models.");
    }

    private static JsonObject skip(String id,String source,String reason){
        JsonObject value=new JsonObject();if(id!=null)value.addProperty("id",id);if(source!=null)value.addProperty("sourceClass",source);
        value.addProperty("reason",reason);return value;
    }
    private static void write(Path path,JsonObject value)throws Exception{
        Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);
    }
}
