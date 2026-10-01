package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometry;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec;
import dev.yinghuang.legacyforgebridge.convert.LegacyLiquidBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.behavior.Rev233LiquidCompat;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Materializes source-proven 1.7 BlockLiquid presentation into converted native block models,
 * empty collision geometry and a client render-layer sidecar.
 */
public final class LegacyLiquidPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/liquid-presentation.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-liquid-presentation";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path staging=context.stagingDir(),contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath);Map<String,String> ids=new LinkedHashMap<>();
        JsonArray blocks=content.getAsJsonArray("blocks");
        if(blocks!=null)for(JsonElement element:blocks){
            if(!element.isJsonObject())continue;JsonObject block=element.getAsJsonObject();
            if(block.has("legacyRegistryName")&&block.has("id"))
                ids.put(block.get("legacyRegistryName").getAsString(),block.get("id").getAsString());
        }

        var analysis=new LegacyLiquidBlockAnalyzer().analyze(context.sourceJar());
        Path geometryPath=staging.resolve(LegacyGeometrySpec.PATH);
        JsonObject geometry=Files.isRegularFile(geometryPath)?read(geometryPath):new JsonObject();
        geometry.addProperty("schemaVersion",1);geometry.addProperty("sourceSha256",context.sourceHash());
        JsonObject geometryBlocks=geometry.has("blocks")&&geometry.get("blocks").isJsonObject()
                ?geometry.getAsJsonObject("blocks"):new JsonObject();
        JsonObject exclusions=geometry.has("excluded")&&geometry.get("excluded").isJsonObject()
                ?geometry.getAsJsonObject("excluded"):new JsonObject();

        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("runtimeImplementationWired",true);root.addProperty("renderLayer","TRANSLUCENT");
        JsonArray rules=new JsonArray();int written=0,overwritten=0;

        for(var rule:analysis.rules()){
            String id=ids.get(rule.registryName());if(id==null)continue;
            String[] split=id.split(":",2);if(split.length!=2)continue;String ns=split[0],path=split[1];
            if(geometryBlocks.has(id))overwritten++;

            JsonObject states=new JsonObject(),variants=new JsonObject();
            for(int meta=0;meta<16;meta++){
                double height=rule.height(meta);
                LegacyGeometry.Box box=new LegacyGeometry.Box(0D,0D,0D,1D,height,1D);
                List<String> sprites=List.of(rule.kind().stillTexture(),rule.kind().stillTexture(),
                        rule.kind().flowTexture(),rule.kind().flowTexture(),rule.kind().flowTexture(),rule.kind().flowTexture());
                JsonObject model=(JsonObject)Rev233LiquidCompat.tintModel(LegacyBlockGeometryPass.model(List.of(box),sprites));
                String modelId=ns+":block/lfb_liquid/"+path+"/"+meta;
                write(modelPath(staging,modelId),model);
                JsonObject state=new JsonObject();state.addProperty("model",modelId);states.add("legacy_meta="+meta,state);

                JsonObject spec=new JsonObject();spec.add("bounds",JSON.toJsonTree(box.values()));
                spec.add("inventoryBounds",JSON.toJsonTree(box.values()));spec.addProperty("copyFace",-1);
                spec.addProperty("edges",true);spec.addProperty("collision","empty");variants.add(String.valueOf(meta),spec);
                if(meta==0){
                    Path main=staging.resolve("assets/"+ns+"/models/block/"+path+".json");
                    write(main,model);LegacyPresentationOwnership.revoke(staging,main);
                }
            }
            JsonObject blockstate=new JsonObject();blockstate.add("variants",states);
            write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),blockstate);

            JsonObject spec=new JsonObject();spec.addProperty("family","box");spec.addProperty("opaque",false);
            spec.addProperty("sourceClass",rule.sourceClass());
            spec.addProperty("proof","Exact legacy BlockLiquid lineage, vanilla water/lava Material, renderType 4 and empty collision.");
            spec.addProperty("sourceRendererEquivalent",false);spec.add("variants",variants);geometryBlocks.add(id,spec);
            exclusions.remove(rule.registryName());

            JsonObject out=new JsonObject();out.addProperty("id",id);out.addProperty("sourceClass",rule.sourceClass());
            out.addProperty("kind",rule.kind().name());out.addProperty("renderType",rule.renderType());
            out.addProperty("collisionEmpty",rule.collisionEmpty());out.addProperty("translucent",true);
            out.addProperty("metadataLevelModels",16);rules.add(out);written++;
        }

        geometry.add("blocks",geometryBlocks);geometry.add("excluded",exclusions);
        // Strictly reparse before publication so the runtime never consumes a malformed merge.
        LegacyGeometrySpec.parse(geometry);write(geometryPath,geometry);
        root.add("rules",rules);root.addProperty("runtimeRules",rules.size());root.addProperty("overwrittenGeometryRules",overwritten);
        JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){
            JsonObject value=new JsonObject();value.addProperty("registryName",item.registryName());
            value.addProperty("sourceClass",item.sourceClass());value.addProperty("reason",item.reason());skipped.add(value);
        }
        root.add("skipped",skipped);write(staging.resolve(OUTPUT),root);
        if(written>0)context.diagnostics().info("LFB-CONVERT-LIQUID-0001",SupportLevel.ADAPTED,
                "Source-proven legacy liquid presentation blocks="+written+"; metadata water level, empty collision and translucent render-layer ownership materialized; overwrittenPriorGeometry="+overwritten+".");
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning("LFB-CONVERT-LIQUID-0002",SupportLevel.RUNTIME_BRIDGE,diagnostic);
    }

    private static Path modelPath(Path staging,String id){String[] split=id.split(":",2);return staging.resolve("assets/"+split[0]+"/models/"+split[1]+".json");}
    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject value)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
}
