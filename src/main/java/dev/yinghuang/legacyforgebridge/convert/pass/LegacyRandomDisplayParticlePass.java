package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyParticle1710;
import dev.yinghuang.legacyforgebridge.convert.LegacyRandomDisplayParticleAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Publishes source-proven randomDisplayTick particle rules with exact platform mappings only. */
public final class LegacyRandomDisplayParticlePass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/random-display-particles.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-random-display-particles";}

    @Override public void apply(ConversionContext context)throws Exception{
        Map<String,String> ids=blockIds(context.stagingDir());if(ids.isEmpty())return;
        var analysis=new LegacyRandomDisplayParticleAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("runtimeImplementationWired",true);JsonArray rules=new JsonArray(),skipped=new JsonArray();
        for(var rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());if(id==null)continue;
            if(rule.particles().stream().anyMatch(name->!LegacyParticle1710.supported(name))){
                JsonObject s=new JsonObject();s.addProperty("registryName",rule.registryName());s.addProperty("sourceBlockClass",rule.sourceBlockClass());
                s.addProperty("reason","At least one source legacy particle name has no exact modern platform mapping.");skipped.add(s);continue;
            }
            JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("sourceBlockClass",rule.sourceBlockClass());
            JsonArray particles=new JsonArray();rule.particles().forEach(particles::add);value.add("particles",particles);
            value.addProperty("centerX",rule.centerX());value.addProperty("centerY",rule.centerY());value.addProperty("centerZ",rule.centerZ());
            value.addProperty("spreadX",rule.spreadX());value.addProperty("spreadZ",rule.spreadZ());
            value.addProperty("zeroVelocity",true);rules.add(value);
        }
        for(var item:analysis.skipped()){
            JsonObject value=new JsonObject();value.addProperty("registryName",item.registryName());value.addProperty("sourceBlockClass",item.sourceBlockClass());value.addProperty("reason",item.reason());skipped.add(value);
        }
        root.add("rules",rules);root.add("skipped",skipped);root.addProperty("runtimeRules",rules.size());
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(!rules.isEmpty())context.diagnostics().info("LFB-CONVERT-PARTICLE-0001",SupportLevel.ADAPTED,
                "Source-proven legacy random-display particle runtimes="+rules.size()+".");
    }

    private static Map<String,String> blockIds(Path staging)throws Exception{
        Path path=staging.resolve(LegacyClientContentBaselinePass.CONTENT);if(!Files.isRegularFile(path))return Map.of();
        Map<String,String> out=new LinkedHashMap<>();try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            JsonArray blocks=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("blocks");if(blocks!=null)for(JsonElement e:blocks){
                if(!e.isJsonObject())continue;JsonObject b=e.getAsJsonObject();if(b.has("sourceClass")&&b.has("id"))out.put(b.get("sourceClass").getAsString(),b.get("id").getAsString());
            }
        }return out;
    }
}
