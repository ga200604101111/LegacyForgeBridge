package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyIconTableAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Emits native modern item-model use-duration dispatch from source-proven legacy icon selectors. */
public final class LegacyUseDurationIconPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/use-duration-icon-presentation.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "source-use-duration-icon-presentation";}
    @Override public void apply(ConversionContext context)throws IOException{
        Path staging=context.stagingDir(),contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath);Map<String,JsonObject> definitions=new HashMap<>();
        if(content.has("items"))for(JsonElement element:content.getAsJsonArray("items")){JsonObject def=element.getAsJsonObject();if(def.has("legacyRegistryName"))definitions.put(def.get("legacyRegistryName").getAsString(),def);}
        LegacyRegistryAnalyzer.Analysis registry=new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        List<LegacyIconTableAnalyzer.UseDurationResult> analysis=new LegacyIconTableAnalyzer().analyzeUseDurationIcons(context.sourceJar(),registry);
        JsonObject root=new JsonObject(),results=new JsonObject();int converted=0,stages=0;
        for(var result:analysis){
            JsonObject def=definitions.get(result.registryName());if(def==null)continue;String id=def.get("id").getAsString();String[] split=id.split(":",2);if(split.length!=2)continue;String ns=split[0],path=split[1];
            JsonObject report=new JsonObject();report.addProperty("sourceClass",result.sourceClass());report.addProperty("limitation",result.limitation());
            if(result.baseIcon()==null||result.stages().isEmpty()){results.add(id,report);continue;}
            Path definition=staging.resolve("assets/"+ns+"/items/"+path+".json");String baseModel=ordinaryModel(definition);if(baseModel==null){report.addProperty("skipped","existing-specialized-item-definition");results.add(id,report);continue;}
            String baseSprite=LegacyIconPresentationPass.resolve(staging,result.baseIcon(),false);if(baseSprite==null){report.addProperty("skipped","base-icon-resource-unresolved");results.add(id,report);continue;}
            JsonArray entries=new JsonArray();boolean valid=true;int localStages=0;
            for(var stage:result.stages()){
                String sprite=LegacyIconPresentationPass.resolve(staging,stage.icon(),false);if(sprite==null){valid=false;break;}
                String modelId=ns+":item/"+path+"_lfb_use_"+stage.minimumElapsedTicks();write(staging.resolve("assets/"+ns+"/models/item/"+path+"_lfb_use_"+stage.minimumElapsedTicks()+".json"),generated(sprite));
                JsonObject entry=new JsonObject();entry.addProperty("threshold",stage.minimumElapsedTicks());entry.add("model",model(modelId));entries.add(entry);localStages++;
            }
            if(!valid){report.addProperty("skipped","stage-icon-resource-unresolved");results.add(id,report);continue;}
            JsonObject range=new JsonObject();range.addProperty("type","minecraft:range_dispatch");range.addProperty("property","minecraft:use_duration");range.add("fallback",model(baseModel));range.add("entries",entries);
            JsonObject condition=new JsonObject();condition.addProperty("type","minecraft:condition");condition.addProperty("property","minecraft:using_item");condition.add("on_false",model(baseModel));condition.add("on_true",range);
            JsonObject item=new JsonObject();item.add("model",condition);write(definition,item);converted++;stages+=localStages;
            report.addProperty("baseIcon",result.baseIcon());report.addProperty("baseModel",baseModel);report.addProperty("stageCount",localStages);report.addProperty("modernUseDurationDispatch",true);results.add(id,report);
        }
        root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());root.addProperty("convertedItems",converted);root.addProperty("useDurationStages",stages);root.add("results",results);write(staging.resolve(OUTPUT),root);
        if(converted>0)context.diagnostics().info("LFB-CONVERT-USE-ICON-0001",SupportLevel.ADAPTED,"Converted source-proven use-duration item icon selectors="+converted+", stages="+stages+" using native item-model dispatch.");
    }
    static JsonObject definitionForTest(String baseModel,List<Map.Entry<Integer,String>> stages){JsonArray entries=new JsonArray();for(var stage:stages){JsonObject e=new JsonObject();e.addProperty("threshold",stage.getKey());e.add("model",model(stage.getValue()));entries.add(e);}JsonObject range=new JsonObject();range.addProperty("type","minecraft:range_dispatch");range.addProperty("property","minecraft:use_duration");range.add("fallback",model(baseModel));range.add("entries",entries);JsonObject condition=new JsonObject();condition.addProperty("type","minecraft:condition");condition.addProperty("property","minecraft:using_item");condition.add("on_false",model(baseModel));condition.add("on_true",range);JsonObject root=new JsonObject();root.add("model",condition);return root;}
    private static JsonObject model(String id){JsonObject node=new JsonObject();node.addProperty("type","minecraft:model");node.addProperty("model",id);return node;}
    private static JsonObject generated(String sprite){JsonObject root=new JsonObject(),textures=new JsonObject();root.addProperty("parent","minecraft:item/generated");textures.addProperty("layer0",sprite);root.add("textures",textures);return root;}
    private static String ordinaryModel(Path path)throws IOException{if(!Files.isRegularFile(path))return null;JsonObject root=read(path);if(!root.has("model")||!root.get("model").isJsonObject())return null;JsonObject node=root.getAsJsonObject("model");return node.has("type")&&"minecraft:model".equals(node.get("type").getAsString())&&node.has("model")?node.get("model").getAsString():null;}
    private static JsonObject read(Path path)throws IOException{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject value)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
}
