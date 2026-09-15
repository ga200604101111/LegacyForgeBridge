package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacySingleInputProcessorAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacySingleInputProcessorRecipeAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacySingleInputProcessorRecipeMaterializer;
import dev.longyu.legacyforgebridge.convert.LegacySingleInputProcessorRuntimeAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Emits source-proven topology and recipes for the first three-slot single-input processor family. */
public final class LegacySingleInputProcessorPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/single-input-processor-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return"legacy-single-input-processors";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacySingleInputProcessorAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;
        Map<String,String> blockIds=generatedBlockIds(context.stagingDir());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",2);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray machines=new JsonArray();int emittedRecipes=0,skippedRecipes=0,runtimeProofs=0;
        LegacySingleInputProcessorRuntimeAnalyzer runtimeAnalyzer=new LegacySingleInputProcessorRuntimeAnalyzer();
        for(var machine:analysis.rules()){
            String id=blockIds.get(machine.sourceBlockClass());
            if(id==null){context.diagnostics().warning("LFB-CONVERT-PROCESSOR-0003",SupportLevel.MANUAL_REQUIRED,"Source-proven processor has no generated block identity: "+machine.sourceBlockClass()+".");continue;}
            var runtime=runtimeAnalyzer.analyze(context.sourceJar(),machine);
            JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("sourceBlockClass",machine.sourceBlockClass());value.addProperty("sourceTileClass",machine.sourceTileClass());value.addProperty("legacyTileId",machine.legacyTileId());
            value.addProperty("slots",machine.slots());value.addProperty("stackLimit",machine.stackLimit());value.addProperty("inputSlot",machine.inputSlot());
            value.add("outputSlots",ints(machine.outputSlots()));value.add("topSlots",ints(machine.topSlots()));value.add("bottomSlots",ints(machine.bottomSlots()));value.add("sideSlots",ints(machine.sideSlots()));
            value.addProperty("processTicks",machine.processTicks());value.addProperty("interactionDistanceSq",machine.interactionDistanceSq());value.addProperty("legacyGuiId",machine.guiId());
            value.addProperty("recipeManagerOwner",machine.recipeManagerOwner());value.addProperty("recipeLookupName",machine.recipeLookupName());value.addProperty("recipeLookupDescriptor",machine.recipeLookupDescriptor());
            value.addProperty("comparator",machine.comparator());value.addProperty("dropContents",machine.dropContents());
            value.addProperty("sidedExtractionProven",runtime.sidedExtractionProven());
            value.addProperty("legacyEnergyApiPresent",runtime.legacyEnergyApiPresent());
            value.addProperty("minUseEnergy",runtime.minUseEnergy());value.addProperty("maxUseEnergy",runtime.maxUseEnergy());
            value.addProperty("energyNbtKey",runtime.energyNbtKey());value.addProperty("energyAccelerationProven",runtime.energyAccelerationProven());
            value.addProperty("runtimeProofComplete",runtime.complete());
            if(runtime.complete())runtimeProofs++;
            JsonArray runtimeDiagnostics=new JsonArray();runtime.diagnostics().forEach(runtimeDiagnostics::add);value.add("runtimeDiagnostics",runtimeDiagnostics);
            value.addProperty("sidedTransferRuntimeComplete",false);value.addProperty("progressNbtRuntimeComplete",false);value.addProperty("runtimeComplete",false);

            var recipes=new LegacySingleInputProcessorRecipeAnalyzer().analyze(context.sourceJar(),machine);JsonArray recipeJson=new JsonArray();
            for(var recipe:recipes.recipes()){
                var modern=LegacySingleInputProcessorRecipeMaterializer.materialize(recipe,context);
                if(modern.isEmpty()){skippedRecipes++;context.diagnostics().warning("LFB-CONVERT-PROCESSOR-0004",SupportLevel.MANUAL_REQUIRED,"Processor recipe could not be materialized without guessing: "+recipe.sourceOwner()+"."+recipe.sourceMethod()+".");continue;}
                recipeJson.add(modern.get());emittedRecipes++;
            }
            value.add("recipes",recipeJson);value.addProperty("sourceRecipeCount",recipes.recipes().size());value.addProperty("materializedRecipeCount",recipeJson.size());
            JsonArray recipeDiagnostics=new JsonArray();recipes.diagnostics().forEach(recipeDiagnostics::add);value.add("recipeDiagnostics",recipeDiagnostics);machines.add(value);
        }
        root.add("machines",machines);JsonArray skipped=new JsonArray();
        for(var candidate:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("registryName",candidate.registryName());value.addProperty("sourceBlockClass",candidate.sourceBlockClass());value.addProperty("reason",candidate.reason());skipped.add(value);}
        root.add("skippedMachines",skipped);root.addProperty("runtimeProofCompleteMachines",runtimeProofs);root.addProperty("materializedRecipes",emittedRecipes);root.addProperty("skippedRecipes",skippedRecipes);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message->context.diagnostics().warning("LFB-CONVERT-PROCESSOR-0002",SupportLevel.MANUAL_REQUIRED,message));
        if(!machines.isEmpty()){
            context.diagnostics().info("LFB-CONVERT-PROCESSOR-0001",SupportLevel.ADAPTED,"Materialized source-proven single-input processor topology/recipes: machines="+machines.size()+", runtimeProofs="+runtimeProofs+", recipes="+emittedRecipes+", skippedRecipes="+skippedRecipes+".");
            context.diagnostics().warning("LFB-CONVERT-PROCESSOR-0005",SupportLevel.RUNTIME_BRIDGE,"Processor topology, sided extraction, energy contract and recipes may be proven, but modern ticking/menu/client synchronization and external legacy energy transport are not complete yet.");
        }
    }

    private static JsonArray ints(List<Integer> values){JsonArray array=new JsonArray();values.forEach(array::add);return array;}
    private static Map<String,String> generatedBlockIds(Path staging)throws Exception{
        Path path=staging.resolve("legacyforgebridge/converted-content.json");if(!Files.isRegularFile(path))return Map.of();Map<String,String> result=new LinkedHashMap<>();
        try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){JsonObject content=JsonParser.parseReader(reader).getAsJsonObject();JsonArray blocks=content.getAsJsonArray("blocks");if(blocks!=null)for(var element:blocks){if(!element.isJsonObject())continue;JsonObject block=element.getAsJsonObject();if(block.has("sourceClass")&&block.has("id"))result.put(block.get("sourceClass").getAsString(),block.get("id").getAsString());}}
        return result;
    }
}
