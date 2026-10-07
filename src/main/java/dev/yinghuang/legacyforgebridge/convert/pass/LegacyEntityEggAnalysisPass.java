package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityEggAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Persists source-proven legacy entity-egg definitions for later presentation/behavior stages. */
public final class LegacyEntityEggAnalysisPass implements ConversionPass {
    public static final String PATH="legacyforgebridge/entity-eggs.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-entity-egg-analysis";}

    @Override public void apply(ConversionContext context)throws Exception{
        write(context,new LegacyEntityEggAnalyzer().analyze(context.sourceJar()));
    }

    static void write(ConversionContext context,LegacyEntityEggAnalyzer.Analysis analysis)throws Exception{
        JsonObject root=new JsonObject();
        root.addProperty("schemaVersion",1);
        root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();
        for(var rule:analysis.rules()){
            JsonObject value=new JsonObject();
            value.addProperty("registryName",rule.registryName());
            value.addProperty("sourceClass",rule.sourceClass());
            value.addProperty("numericId",rule.numericId());
            value.addProperty("primaryColor",rule.primaryColor());
            value.addProperty("secondaryColor",rule.secondaryColor());
            value.addProperty("sourceOwner",rule.sourceOwner());
            value.addProperty("sourceMethod",rule.sourceMethod());
            rules.add(value);
        }
        root.add("rules",rules);
        JsonArray diagnostics=new JsonArray();
        analysis.diagnostics().forEach(diagnostics::add);
        root.add("diagnostics",diagnostics);

        Path output=context.stagingDir().resolve(PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);

        if(!analysis.rules().isEmpty())context.diagnostics().info(
                "LFB-CONVERT-ENTITY-EGG-0001",SupportLevel.ADAPTED,
                "Recovered "+analysis.rules().size()+" source-proven legacy entity egg colour definitions.");
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning(
                "LFB-CONVERT-ENTITY-EGG-0002",SupportLevel.MANUAL_REQUIRED,diagnostic);
    }
}
