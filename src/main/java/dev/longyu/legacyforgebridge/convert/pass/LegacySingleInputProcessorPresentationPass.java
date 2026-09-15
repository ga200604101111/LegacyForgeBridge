package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacySingleInputProcessorAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacySingleInputProcessorPresentationAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Additive presentation proof and runtime gate for already-emitted generic processor rules. */
public final class LegacySingleInputProcessorPresentationPass implements ConversionPass {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-single-input-processor-presentation";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path output=context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        if(!Files.isRegularFile(output))return;
        JsonObject root;
        try(Reader reader=Files.newBufferedReader(output,StandardCharsets.UTF_8)){
            root=JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonArray machines=root.getAsJsonArray("machines");
        if(machines==null||machines.isEmpty())return;

        Map<String,LegacySingleInputProcessorAnalyzer.Rule> rules=new HashMap<>();
        var topology=new LegacySingleInputProcessorAnalyzer().analyze(context.sourceJar());
        topology.rules().forEach(rule->rules.put(rule.sourceBlockClass(),rule));
        LegacySingleInputProcessorPresentationAnalyzer analyzer=new LegacySingleInputProcessorPresentationAnalyzer();
        int proven=0;
        int guiRuntime=0;
        int worldRuntime=0;
        for(JsonElement element:machines){
            if(!element.isJsonObject())continue;
            JsonObject machine=element.getAsJsonObject();
            String source=string(machine,"sourceBlockClass");
            LegacySingleInputProcessorAnalyzer.Rule rule=rules.get(source);
            if(rule==null)continue;
            var analysis=analyzer.analyze(context.sourceJar(),rule);
            boolean complete=analysis.presentation().isPresent();
            machine.addProperty("presentationProofComplete",complete);
            JsonArray diagnostics=new JsonArray();analysis.diagnostics().forEach(diagnostics::add);
            machine.add("presentationDiagnostics",diagnostics);
            machine.addProperty("guiPresentationRuntimeComplete",complete);
            machine.addProperty("worldPresentationRuntimeComplete",complete);
            machine.addProperty("metadataRollRuntimeComplete",complete);
            machine.addProperty("inventoryPresentationRuntimeComplete",false);
            machine.addProperty("particlePresentationRuntimeComplete",false);
            machine.addProperty("sourcePresentationComplete",false);
            machine.addProperty("runtimeComplete",false);
            if(complete){
                proven++;
                guiRuntime++;
                worldRuntime++;
                machine.add("presentation",presentationJson(analysis.presentation().orElseThrow()));
            }
        }
        root.addProperty("presentationProofCompleteMachines",proven);
        root.addProperty("guiPresentationRuntimeCompleteMachines",guiRuntime);
        root.addProperty("worldPresentationRuntimeCompleteMachines",worldRuntime);
        Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(proven>0){
            context.diagnostics().info("LFB-CONVERT-PROCESSOR-PRESENTATION-0001",SupportLevel.ADAPTED,
                    "Proven and enabled source GUI/world presentation for "+proven+" converted processor(s).");
            context.diagnostics().warning("LFB-CONVERT-PROCESSOR-PRESENTATION-0002",SupportLevel.RUNTIME_BRIDGE,
                    "Processor source inventory-item rendering and client particle presentation remain unresolved; full source presentation is not claimed.");
        }
    }

    private static JsonObject presentationJson(LegacySingleInputProcessorPresentationAnalyzer.Presentation proof){
        JsonObject value=new JsonObject();
        value.addProperty("sourceGuiClass",proof.sourceGuiClass());
        value.addProperty("guiTexture",proof.guiTexture());
        value.addProperty("guiTextureWidth",proof.guiTextureWidth());
        value.addProperty("guiTextureHeight",proof.guiTextureHeight());
        value.addProperty("guiWidth",proof.guiWidth());
        value.addProperty("guiHeight",proof.guiHeight());
        JsonObject motion=new JsonObject();
        motion.addProperty("x",proof.motionX());motion.addProperty("y",proof.motionY());motion.addProperty("u",proof.motionU());
        motion.addProperty("vStride",proof.motionVStride());motion.addProperty("width",proof.motionWidth());motion.addProperty("height",proof.motionHeight());motion.addProperty("frames",proof.motionFrames());
        value.add("motion",motion);
        JsonObject progress=new JsonObject();
        progress.addProperty("x",proof.progressX());progress.addProperty("y",proof.progressY());progress.addProperty("u",proof.progressU());
        progress.addProperty("vStride",proof.progressVStride());progress.addProperty("width",proof.progressWidth());progress.addProperty("height",proof.progressHeight());progress.addProperty("stages",proof.progressStages());
        value.add("progress",progress);
        value.addProperty("sourceRendererClass",proof.sourceRendererClass());
        value.addProperty("sourceModelClass",proof.sourceModelClass());
        value.addProperty("entityTexture",proof.entityTexture());
        value.addProperty("entityTextureWidth",proof.entityTextureWidth());
        value.addProperty("entityTextureHeight",proof.entityTextureHeight());
        value.addProperty("modelTextureWidth",proof.modelTextureWidth());
        value.addProperty("modelTextureHeight",proof.modelTextureHeight());
        value.add("rotatingLower",cuboid(proof.rotatingLower()));
        value.add("staticUpper",cuboid(proof.staticUpper()));
        value.addProperty("renderScale",proof.renderScale());
        value.addProperty("centeredAtBlock",proof.centeredAtBlock());
        value.addProperty("metadataDrivesRoll",proof.metadataDrivesRoll());
        value.addProperty("inventoryUsesZeroRotation",proof.inventoryUsesZeroRotation());
        return value;
    }

    private static JsonObject cuboid(LegacySingleInputProcessorPresentationAnalyzer.Cuboid source){
        JsonObject value=new JsonObject();
        value.addProperty("u",source.u());value.addProperty("v",source.v());value.addProperty("x",source.x());value.addProperty("y",source.y());value.addProperty("z",source.z());
        value.addProperty("width",source.width());value.addProperty("height",source.height());value.addProperty("depth",source.depth());
        return value;
    }

    private static String string(JsonObject value,String key){
        JsonElement element=value.get(key);return element!=null&&element.isJsonPrimitive()?element.getAsString():"";
    }
}
