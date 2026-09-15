package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

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
        int inventoryRuntime=0;
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
            boolean inventoryComplete=false;
            if(complete){
                LegacySingleInputProcessorPresentationAnalyzer.Presentation proof=analysis.presentation().orElseThrow();
                proven++;
                guiRuntime++;
                worldRuntime++;
                machine.add("presentation",presentationJson(proof));
                if(proof.inventoryUsesZeroRotation()){
                    writeInventoryPresentation(context.stagingDir(),string(machine,"id"),proof);
                    inventoryComplete=true;
                    inventoryRuntime++;
                }
            }
            machine.addProperty("inventoryPresentationRuntimeComplete",inventoryComplete);
            machine.addProperty("particlePresentationRuntimeComplete",false);
            machine.addProperty("sourcePresentationComplete",false);
            machine.addProperty("runtimeComplete",false);
        }
        root.addProperty("presentationProofCompleteMachines",proven);
        root.addProperty("guiPresentationRuntimeCompleteMachines",guiRuntime);
        root.addProperty("worldPresentationRuntimeCompleteMachines",worldRuntime);
        root.addProperty("inventoryPresentationRuntimeCompleteMachines",inventoryRuntime);
        Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(proven>0){
            context.diagnostics().info("LFB-CONVERT-PROCESSOR-PRESENTATION-0001",SupportLevel.ADAPTED,
                    "Proven and enabled source GUI/world presentation for "+proven+" converted processor(s); inventory3D="+inventoryRuntime+".");
            context.diagnostics().warning("LFB-CONVERT-PROCESSOR-PRESENTATION-0002",SupportLevel.RUNTIME_BRIDGE,
                    "Processor client particle presentation remains unresolved; full source presentation is not claimed.");
        }
    }

    private static void writeInventoryPresentation(Path staging,String idValue,
                                                   LegacySingleInputProcessorPresentationAnalyzer.Presentation proof)throws Exception{
        int separator=idValue.indexOf(':');
        if(separator<=0||separator==idValue.length()-1)throw new IllegalArgumentException("Invalid converted processor id "+idValue);
        String namespace=idValue.substring(0,separator);
        String path=idValue.substring(separator+1);
        String baseName=path+"_processor_base";

        JsonObject base=new JsonObject();
        base.addProperty("parent","minecraft:block/block");
        writeJson(staging.resolve("assets/"+namespace+"/models/item/"+baseName+".json"),base);

        JsonObject special=new JsonObject();
        special.addProperty("type","legacyforgebridge:processor");
        special.addProperty("texture",proof.entityTexture());
        special.addProperty("texture_width",proof.modelTextureWidth());
        special.addProperty("texture_height",proof.modelTextureHeight());
        special.add("rotating_lower",specialCuboid(proof.rotatingLower()));
        special.add("static_upper",specialCuboid(proof.staticUpper()));
        special.addProperty("centered",proof.centeredAtBlock());

        JsonObject model=new JsonObject();
        model.addProperty("type","minecraft:special");
        model.addProperty("base",namespace+":item/"+baseName);
        model.add("model",special);
        JsonObject item=new JsonObject();
        item.add("model",model);
        writeJson(staging.resolve("assets/"+namespace+"/items/"+path+".json"),item);
    }

    private static JsonObject specialCuboid(LegacySingleInputProcessorPresentationAnalyzer.Cuboid source){
        JsonObject value=new JsonObject();
        value.addProperty("u",source.u());value.addProperty("v",source.v());
        value.addProperty("x",source.x());value.addProperty("y",source.y());value.addProperty("z",source.z());
        value.addProperty("width",source.width());value.addProperty("height",source.height());value.addProperty("depth",source.depth());
        return value;
    }

    private static void writeJson(Path path,JsonObject value)throws Exception{
        Files.createDirectories(path.getParent());
        Files.writeString(path,GSON.toJson(value)+"\n",StandardCharsets.UTF_8);
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
