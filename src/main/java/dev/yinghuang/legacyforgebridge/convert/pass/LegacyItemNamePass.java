package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyIconTableAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Converts source-proven ItemStack name keys, independently from the renderer's coverage. */
public final class LegacyItemNamePass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/item-names.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "source-item-stack-names";}
    @Override public void apply(ConversionContext context)throws IOException {
        Path contentPath=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath),names=new JsonObject(),report=new JsonObject(),exclusions=new JsonObject();
        Map<String,JsonObject> definitions=new LinkedHashMap<>();
        for(String category:List.of("blocks","items"))if(content.has(category))for(JsonElement el:content.getAsJsonArray(category)) {
            JsonObject def=el.getAsJsonObject();
            if(def.has("legacyRegistryName"))definitions.put(category+":"+def.get("legacyRegistryName").getAsString(),def);
        }
        // Only existing language entries are accepted. The source's spelling, case and translated
        // values are retained. No name is reverse-guessed from a texture or from a modern item.
        Set<String> languageKeys=new HashSet<>();
        if(Files.isDirectory(context.stagingDir().resolve("assets")))try(var stream=Files.walk(context.stagingDir().resolve("assets"))) {
            for(Path file:stream.filter(Files::isRegularFile).filter(p->p.toString().replace('\\','/').contains("/lang/")&&p.toString().endsWith(".json")).sorted().toList()) {
                try {languageKeys.addAll(read(file).keySet());}catch(RuntimeException malformed){/* Do not invent missing translations. */}
            }
        }
        String prefix="lfb.converted."+context.metadata().fabricId()+".";
        var registrations=new LegacyRegistryAnalyzer().analyze(context.sourceJar()).registrations();
        var results=new LegacyIconTableAnalyzer().analyzeNames(context.sourceJar(),registrations);
        int defaults=0,variants=0,corrected=0;Set<String> distinctKeys=new HashSet<>();
        for(var result:results) {
            JsonObject def=definitions.get((result.block()?"blocks:":"items:")+result.registryName());if(def==null)continue;
            String id=def.get("id").getAsString();JsonObject keys=new JsonObject();
            for(var e:result.keys().entrySet()) {
                String key=prefix+e.getValue();
                if(languageKeys.contains(key)){keys.addProperty(String.valueOf(e.getKey()),key);variants++;distinctKeys.add(key);}
            }
            if(keys.has("0")){String key=keys.get("0").getAsString();if(!def.has("descriptionKey")||!key.equals(def.get("descriptionKey").getAsString()))corrected++;def.addProperty("descriptionKey",key);defaults++;}
            if(!keys.isEmpty())names.add(id,keys);
            if(keys.isEmpty()||!result.limitation().isEmpty())exclusions.addProperty(id,result.limitation().isEmpty()?"source language file has no matching key":result.limitation());
        }
        report.addProperty("schemaVersion",1);report.addProperty("sourceSha256",context.sourceHash());
        report.addProperty("defaultNameKeysCorrected",corrected);report.addProperty("distinctTranslationKeys",distinctKeys.size());
        report.addProperty("defaultNamesProven",defaults);report.addProperty("metadataNamesProven",variants);
        report.addProperty("metadataProbeLimitExclusive",256);report.add("items",names);report.add("exclusions",exclusions);
        report.addProperty("customNamesPreserved",true);report.addProperty("allDynamicNamesConverted",false);
        write(contentPath,content);write(context.stagingDir().resolve(OUTPUT),report);
        context.diagnostics().info("LFB-CONVERT-NAME-0001",SupportLevel.ADAPTED,
            "Recovered source default names="+defaults+", metadata name keys="+variants+". Custom/NBT-dependent display names remain separate.");
    }
    private static JsonObject read(Path path)throws IOException{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject value)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
}
