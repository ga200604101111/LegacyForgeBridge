package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyHeldItemVisibilityAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Materializes held-own-BlockItem visibility as client metadata plus hidden/visible blockstate models. */
public final class LegacyHeldItemVisibilityPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/held-item-visibility.json";private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "held-own-blockitem-visibility";}
    @Override public void apply(ConversionContext context)throws IOException{
        Path staging=context.stagingDir(),contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath);Map<String,JsonObject> defs=new HashMap<>();if(content.has("blocks"))for(JsonElement e:content.getAsJsonArray("blocks")){JsonObject d=e.getAsJsonObject();if(d.has("legacyRegistryName"))defs.put(d.get("legacyRegistryName").getAsString(),d);}
        var analysis=new LegacyHeldItemVisibilityAnalyzer().analyze(context.sourceJar());JsonArray rules=new JsonArray();int converted=0;
        for(var source:analysis.rules()){
            JsonObject def=defs.get(source.registryName());if(def==null)continue;String id=def.get("id").getAsString();String[] split=id.split(":",2);if(split.length!=2)continue;String ns=split[0],path=split[1];
            Path statePath=staging.resolve("assets/"+ns+"/blockstates/"+path+".json");if(!Files.isRegularFile(statePath))continue;JsonObject old=read(statePath);if(!old.has("variants")||!old.get("variants").isJsonObject())continue;JsonObject oldStates=old.getAsJsonObject("variants");
            JsonObject hidden=new JsonObject();hidden.addProperty("parent","minecraft:block/block");String hiddenId=ns+":block/"+path+"_lfb_hidden";write(staging.resolve("assets/"+ns+"/models/block/"+path+"_lfb_hidden.json"),hidden);
            JsonObject states=new JsonObject();boolean complete=true;for(int meta=0;meta<16;meta++){String key="legacy_meta="+meta;JsonElement prior=oldStates.get(key);if(prior==null||!prior.isJsonObject()||!prior.getAsJsonObject().has("model")){complete=false;break;}JsonObject state=new JsonObject();state.addProperty("model",(meta&source.visibleOrMask())!=0?prior.getAsJsonObject().get("model").getAsString():hiddenId);states.add(key,state);}
            if(!complete)continue;JsonObject blockstate=new JsonObject();blockstate.add("variants",states);write(statePath,blockstate);
            JsonObject rule=new JsonObject();rule.addProperty("id",id);rule.addProperty("sourceClass",source.sourceBlockClass());rule.addProperty("visibleOrMask",source.visibleOrMask());rule.addProperty("hiddenAndMask",source.hiddenAndMask());rule.addProperty("clientMetadataToggleRuntime",true);rules.add(rule);converted++;
        }
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());root.addProperty("runtimeComplete",true);root.add("rules",rules);root.addProperty("convertedBlocks",converted);write(staging.resolve(OUTPUT),root);
        if(converted>0)context.diagnostics().info("LFB-CONVERT-HELD-VIS-0001",SupportLevel.ADAPTED,"Converted held-own-BlockItem visibility blocks="+converted+" with client metadata/model toggles.");
    }
    private static JsonObject read(Path p)throws IOException{return JsonParser.parseString(Files.readString(p,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path p,JsonObject v)throws IOException{Files.createDirectories(p.getParent());Files.writeString(p,JSON.toJson(v)+"\n",StandardCharsets.UTF_8);}
}
