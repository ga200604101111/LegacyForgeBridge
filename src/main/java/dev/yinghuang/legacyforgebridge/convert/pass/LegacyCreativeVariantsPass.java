package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyIconTableAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Candidate-owned, source-enumerated creative subtypes shared by local UI and the Via bridge. */
public final class LegacyCreativeVariantsPass implements ConversionPass {
    public static final String PATH="legacyforgebridge/creative-variants.json";
    @Override public String id(){return "source-creative-variants";}
    @Override public void apply(ConversionContext context)throws IOException {
        var path=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(path))return;
        JsonObject content=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String,String> ids=new LinkedHashMap<>();
        for(String category:List.of("blocks","items"))if(content.has(category))for(JsonElement e:content.getAsJsonArray(category)) {
            JsonObject d=e.getAsJsonObject();if(d.has("id")&&d.has("legacyRegistryName"))ids.put(category+":"+d.get("legacyRegistryName").getAsString(),d.get("id").getAsString());
        }
        var registry=new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        JsonObject items=new JsonObject(),exclusions=new JsonObject();int stacks=0;
        for(var result:new LegacyIconTableAnalyzer().analyzeCreative(context.sourceJar(),registry.registrations())){
            String id=ids.get((result.block()?"blocks:":"items:")+result.registryName());if(id==null)continue;
            if(!result.proven()){exclusions.addProperty(id,result.limitation());continue;}
            JsonArray values=new JsonArray();for(int meta:result.metadata())values.add(meta);
            items.add(id,values);stacks+=values.size();
        }
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("provedIdentities",items.size());root.addProperty("enumeratedStacks",stacks);
        root.add("items",items);root.add("exclusions",exclusions);
        var output=context.stagingDir().resolve(PATH);Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n",StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-CREATIVE-0001",SupportLevel.ADAPTED,
                "Source creative enumeration: identities="+items.size()+", stacks="+stacks+", exclusions="+exclusions.size()+". Unproved callbacks retain their previous default entry.");
    }
}
