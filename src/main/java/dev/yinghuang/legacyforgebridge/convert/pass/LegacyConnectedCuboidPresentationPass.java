package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometry;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec;
import dev.yinghuang.legacyforgebridge.convert.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Final presentation owner for source-proven connected pillar/beam renderers.
 *
 * <p>This pass deliberately does not depend on the generic icon interpreter being able to execute
 * legacy reflection or constructor aliases. Geometry and material provider are already proved by
 * {@link LegacyConnectedCuboidRendererAnalyzer}; this stage turns that evidence into final modern
 * models and merges the runtime connected-cuboid rule.</p>
 */
public final class LegacyConnectedCuboidPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/connected-cuboid-presentation.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-connected-cuboid-presentation";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path staging=context.stagingDir(),contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;

        JsonObject content=read(contentPath);Map<String,String> ids=new HashMap<>();
        if(content.has("blocks"))for(JsonElement element:content.getAsJsonArray("blocks")){
            JsonObject block=element.getAsJsonObject();
            if(block.has("legacyRegistryName")&&block.has("id"))
                ids.put(block.get("legacyRegistryName").getAsString(),block.get("id").getAsString());
        }

        LegacyRegistryAnalyzer.Analysis registry=new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        Map<String,String> sourceFieldBlocks=new HashMap<>();
        for(var binding:registry.fieldBindings())if(binding.kind()==LegacyRegistryAnalyzer.Kind.BLOCK)
            sourceFieldBlocks.put(binding.owner()+"#"+binding.name(),binding.registryName());

        var analysis=new LegacyConnectedCuboidRendererAnalyzer().analyze(context.sourceJar());
        Path geometryPath=staging.resolve(LegacyGeometrySpec.PATH);
        JsonObject geometry=Files.isRegularFile(geometryPath)?read(geometryPath):new JsonObject();
        if(!geometry.has("schemaVersion"))geometry.addProperty("schemaVersion",1);
        if(!geometry.has("sourceSha256"))geometry.addProperty("sourceSha256",context.sourceHash());
        JsonObject blocks=geometry.has("blocks")&&geometry.get("blocks").isJsonObject()?geometry.getAsJsonObject("blocks"):new JsonObject();
        JsonObject excluded=geometry.has("excluded")&&geometry.get("excluded").isJsonObject()?geometry.getAsJsonObject("excluded"):new JsonObject();

        JsonArray evidence=new JsonArray();int written=0,alreadyOwned=0;
        for(var rule:analysis.rules()){
            String id=ids.get(rule.registryName());if(id==null)continue;
            if(blocks.has(id)){alreadyOwned++;continue;} // Generic geometry already produced an equivalent connected rule.
            String[] split=id.split(":",2);if(split.length!=2)continue;String ns=split[0],path=split[1];

            JsonObject variants=new JsonObject(),states=currentVariants(staging,ns,path);
            int supported=rule.axisLocked()?6:16;
            boolean complete=true;
            for(int meta=0;meta<supported;meta++){
                String texture=materialTexture(staging,rule.materialSource(),meta,ids,sourceFieldBlocks);
                if(texture==null){complete=false;break;}
                List<String> sprites=Collections.nCopies(6,texture);
                LegacyGeometry.Box core=LegacyGeometry.connectedCore(
                        rule.minWidth(),rule.maxWidth(),rule.minHeight(),rule.maxHeight(),rule.axisLocked(),meta);
                String worldId=ns+":block/lfb_connected/"+path+"/"+meta;
                String itemId=ns+":item/lfb_connected/"+path+"/"+meta;
                write(modelPath(staging,worldId),LegacyBlockGeometryPass.model(List.of(core),sprites));
                write(modelPath(staging,itemId),LegacyBlockGeometryPass.model(List.of(core),sprites));

                JsonObject state=new JsonObject();state.addProperty("model",worldId);states.add("legacy_meta="+meta,state);
                JsonObject variant=new JsonObject();
                variant.add("bounds",JSON.toJsonTree(core.values()));
                variant.add("inventoryBounds",JSON.toJsonTree(core.values()));
                variant.addProperty("copyFace",-1);variant.addProperty("edges",true);variant.addProperty("collision",rule.collision());
                variants.add(String.valueOf(meta),variant);

                JsonObject itemRoot=new JsonObject(),itemModel=new JsonObject();
                itemModel.addProperty("type","minecraft:model");itemModel.addProperty("model",itemId);itemRoot.add("model",itemModel);
                write(staging.resolve("assets/"+ns+"/items/lfb_connected/"+path+"/"+meta+".json"),itemRoot);
                if(meta==0){
                    write(staging.resolve("assets/"+ns+"/models/block/"+path+".json"),LegacyBlockGeometryPass.model(List.of(core),sprites));
                    write(staging.resolve("assets/"+ns+"/models/item/"+path+".json"),LegacyBlockGeometryPass.model(List.of(core),sprites));
                    write(staging.resolve("assets/"+ns+"/items/"+path+".json"),itemRoot);
                }
            }
            if(!complete||variants.size()!=supported)continue;

            JsonObject blockstate=new JsonObject();blockstate.add("variants",states);
            write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),blockstate);

            JsonObject spec=new JsonObject();spec.addProperty("family","connected_cuboid");spec.addProperty("opaque",false);
            spec.addProperty("sourceClass",rule.sourceClass());spec.addProperty("proof",rule.proof());spec.addProperty("sourceRendererEquivalent",false);
            JsonObject connected=new JsonObject();
            connected.addProperty("minWidth",rule.minWidth());connected.addProperty("maxWidth",rule.maxWidth());
            connected.addProperty("minHeight",rule.minHeight());connected.addProperty("maxHeight",rule.maxHeight());
            connected.addProperty("axisLocked",rule.axisLocked());connected.addProperty("sameMetadataOnly",rule.sameMetadataOnly());
            connected.addProperty("connectFullBlocks",rule.connectFullBlocks());connected.addProperty("connectWood",rule.connectWood());
            connected.addProperty("connectRock",rule.connectRock());spec.add("connectedCuboid",connected);spec.add("variants",variants);
            blocks.add(id,spec);excluded.remove(rule.registryName());

            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("legacyRegistryName",rule.registryName());
            row.addProperty("sourceClass",rule.sourceClass());row.addProperty("rendererClass",rule.rendererClass());
            row.addProperty("materialMode",rule.materialSource().mode());row.addProperty("supportedMetadata",supported);
            row.addProperty("finalModelOwnership",true);evidence.add(row);written++;
        }

        geometry.add("blocks",blocks);geometry.add("excluded",excluded);geometry.addProperty("nativeGeometryAdapters",blocks.size());
        if(!geometry.has("inventoryMaterialFallbackVariants"))geometry.addProperty("inventoryMaterialFallbackVariants",0);
        if(!geometry.has("fullRendererEquivalenceProven"))geometry.addProperty("fullRendererEquivalenceProven",false);
        LegacyGeometrySpec.parse(geometry);write(geometryPath,geometry);

        JsonObject report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("sourceSha256",context.sourceHash());
        report.addProperty("finalConnectedModelsWritten",written);report.addProperty("alreadyOwnedConnectedModels",alreadyOwned);report.add("rules",evidence);
        write(staging.resolve(OUTPUT),report);
        if(written>0)context.diagnostics().info("LFB-CONVERT-CONNECTED-0001",SupportLevel.ADAPTED,
                "Final connected pillar/beam presentation written="+written+"; constructor/reflection material providers are preserved without executing source classes.");
    }

    private static String materialTexture(Path staging,LegacyConnectedCuboidRendererAnalyzer.MaterialSource material,int stateMeta,
                                          Map<String,String> ids,Map<String,String> sourceFieldBlocks)throws Exception{
        if(material.mode().equals("self"))return null; // Existing generic geometry is the owner for source-self materials.
        var field=material.field();if(field==null)return null;int meta=material.metadataFromState()?stateMeta:material.metadata();
        var vanilla=LegacyVanillaRegistry1710.resolve(field.owner(),field.name());
        if(vanilla.isPresent()){
            if(vanilla.get().kind()!=LegacyRegistryAnalyzer.Kind.BLOCK)return null;
            return vanillaTexture(vanilla.get().registryName(),meta);
        }
        String sourceRegistry=sourceFieldBlocks.get(field.owner()+"#"+field.name());
        String sourceId=sourceRegistry==null?null:ids.get(sourceRegistry);
        return sourceId==null?null:modelTexture(staging,sourceId,meta);
    }

    static String vanillaTexture(String legacyBlock,int metadata){
        String[] wood={"oak","spruce","birch","jungle","acacia","dark_oak"};
        return switch(legacyBlock){
            case "planks" -> "minecraft:block/"+wood[Math.floorMod(metadata,6)]+"_planks";
            case "log" -> "minecraft:block/"+wood[Math.floorMod(metadata,4)]+"_log";
            case "log2" -> "minecraft:block/"+wood[4+Math.floorMod(metadata,2)]+"_log";
            default -> null;
        };
    }

    private static String modelTexture(Path staging,String id,int metadata)throws Exception{
        String[] split=id.split(":",2);if(split.length!=2)return null;String ns=split[0],path=split[1];
        Path statesPath=staging.resolve("assets/"+ns+"/blockstates/"+path+".json");if(!Files.isRegularFile(statesPath))return null;
        JsonObject states=read(statesPath);if(!states.has("variants"))return null;
        JsonElement state=states.getAsJsonObject("variants").get("legacy_meta="+metadata);if(state==null||!state.isJsonObject())return null;
        JsonObject stateObj=state.getAsJsonObject();if(!stateObj.has("model"))return null;String modelId=stateObj.get("model").getAsString();
        Path model=modelPath(staging,modelId);return Files.isRegularFile(model)?extractTexture(staging,model,new HashSet<>(),0):null;
    }

    private static String extractTexture(Path staging,Path modelPath,Set<Path> seen,int depth)throws Exception{
        if(depth>16||!seen.add(modelPath)||!Files.isRegularFile(modelPath))return null;JsonObject model=read(modelPath);
        JsonObject textures=model.has("textures")&&model.get("textures").isJsonObject()?model.getAsJsonObject("textures"):new JsonObject();
        for(String key:List.of("north","side","all","particle","cross","crop","up","down")){
            if(!textures.has(key)||!textures.get(key).isJsonPrimitive())continue;String value=textures.get(key).getAsString();
            for(int i=0;i<16&&value.startsWith("#");i++){String ref=value.substring(1);if(!textures.has(ref))return null;value=textures.get(ref).getAsString();}
            if(!value.startsWith("#")&&value.contains(":"))return value;
        }
        if(model.has("parent")&&model.get("parent").isJsonPrimitive()){
            String parent=model.get("parent").getAsString();
            if(parent.contains(":")&&!parent.startsWith("minecraft:block/")&&!parent.startsWith("minecraft:item/"))
                return extractTexture(staging,modelPath(staging,parent),seen,depth+1);
        }
        return null;
    }

    private static JsonObject currentVariants(Path staging,String ns,String path)throws Exception{
        Path file=staging.resolve("assets/"+ns+"/blockstates/"+path+".json");
        if(Files.isRegularFile(file)){JsonObject root=read(file);if(root.has("variants")&&root.get("variants").isJsonObject())return root.getAsJsonObject("variants").deepCopy();}
        return new JsonObject();
    }
    private static Path modelPath(Path staging,String id){String[] p=id.split(":",2);return p.length==2?staging.resolve("assets/"+p[0]+"/models/"+p[1]+".json"):staging.resolve("__invalid__");}
    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject value)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
}
