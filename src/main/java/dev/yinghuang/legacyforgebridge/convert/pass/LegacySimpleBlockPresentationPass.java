package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegisteredBlockRenderTypeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySimpleBlockRendererAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Final model ownership pass for source-proven simple legacy block renderer families.
 *
 * <p>This runs after generic icon/geometry recovery so a proven CROSS/CROP renderer cannot be
 * accidentally left as an ordinary cube merely because an earlier presentation stage recovered
 * a usable texture. Direct 1.7 vanilla render types 1/6 are admitted by the same source proof and
 * keep their already-materialized metadata-specific textures.</p>
 */
public final class LegacySimpleBlockPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/simple-block-presentation.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    private enum FinalMode { CROSS, CROP, META_ZERO_CROP_ELSE_STANDARD }
    private record Candidate(String registryName,String sourceBlockClass,String sourceRendererClass,FinalMode mode,String proof,
                             LegacySimpleBlockRendererAnalyzer.Bounds bounds,boolean emptyCollision,
                             LegacySimpleBlockRendererAnalyzer.RenderOffset renderOffset,boolean flatInventory) { }

    @Override public String id(){return "legacy-simple-block-presentation";}

    @Override
    public void apply(ConversionContext context)throws Exception{
        Path staging=context.stagingDir();
        Path contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath);Map<String,String> ids=new HashMap<>();
        if(content.has("blocks"))for(JsonElement element:content.getAsJsonArray("blocks")){
            JsonObject block=element.getAsJsonObject();
            if(block.has("legacyRegistryName")&&block.has("id"))ids.put(block.get("legacyRegistryName").getAsString(),block.get("id").getAsString());
        }

        List<Candidate> candidates=new ArrayList<>();Set<String> customOwned=new HashSet<>();
        var simple=new LegacySimpleBlockRendererAnalyzer().analyze(context.sourceJar());
        for(var rule:simple.rules()){
            customOwned.add(rule.registryName());
            if(rule.mode()==LegacySimpleBlockRendererAnalyzer.Mode.HELD_ITEM_CROSS)continue;
            FinalMode mode=switch(rule.mode()){
                case CROSS->FinalMode.CROSS;
                case CROP->FinalMode.CROP;
                case META_ZERO_CROP_ELSE_STANDARD->FinalMode.META_ZERO_CROP_ELSE_STANDARD;
                default->null;
            };
            if(mode!=null)candidates.add(new Candidate(rule.registryName(),rule.sourceBlockClass(),rule.sourceRendererClass(),mode,
                    "source-bound custom renderer",rule.bounds(),rule.emptyCollision(),rule.renderOffset(),rule.flatInventory()));
        }

        var renderTypes=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(context.sourceJar());
        for(var rule:renderTypes.rules()){
            if(customOwned.contains(rule.registryName())||rule.renderIdentity().constant()==null)continue;
            int type=rule.renderIdentity().constant();
            FinalMode mode=type==1?FinalMode.CROSS:type==6?FinalMode.CROP:null;
            if(mode!=null)candidates.add(new Candidate(rule.registryName(),rule.sourceBlockClass(),null,mode,
                    "direct legacy vanilla renderType="+type,null,false,LegacySimpleBlockRendererAnalyzer.RenderOffset.NONE,false));
        }
        candidates.sort(Comparator.comparing(Candidate::registryName));

        Path geometryPath=staging.resolve(dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec.PATH);
        JsonObject geometryRoot=Files.isRegularFile(geometryPath)?read(geometryPath):new JsonObject();
        if(!geometryRoot.has("schemaVersion"))geometryRoot.addProperty("schemaVersion",1);
        if(!geometryRoot.has("sourceSha256"))geometryRoot.addProperty("sourceSha256",context.sourceHash());
        if(!geometryRoot.has("blocks")||!geometryRoot.get("blocks").isJsonObject())geometryRoot.add("blocks",new JsonObject());
        JsonObject geometryBlocks=geometryRoot.getAsJsonObject("blocks");int mergedShapes=0;

        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        JsonArray rules=new JsonArray();int written=0;
        for(Candidate rule:candidates){
            String id=ids.get(rule.registryName());if(id==null)continue;String[] split=id.split(":",2);if(split.length!=2)continue;
            String ns=split[0],path=split[1];Path modelPath=staging.resolve("assets/"+ns+"/models/block/"+path+".json");
            if(!Files.isRegularFile(modelPath))continue;

            boolean success;
            String texture=null;
            if(rule.mode()==FinalMode.CROSS||rule.mode()==FinalMode.CROP){
                String parent=rule.mode()==FinalMode.CROSS?"minecraft:block/cross":"minecraft:block/crop";
                String key=rule.mode()==FinalMode.CROSS?"cross":"crop";
                success=rewriteReferencedModels(staging,ns,path,parent,key);
            }else{
                JsonObject current=read(modelPath);texture=singleTexture(current);if(texture==null)continue;
                String cropId=ns+":block/"+path+"_lfb_meta_0_crop";
                write(modelPath(staging,cropId),simpleModel("minecraft:block/crop","crop",texture));
                JsonObject variants=new JsonObject();
                for(int meta=0;meta<16;meta++){
                    JsonObject state=new JsonObject();state.addProperty("model",meta==0?cropId:ns+":block/"+path);
                    variants.add("legacy_meta="+meta,state);
                }
                JsonObject blockstate=new JsonObject();blockstate.add("variants",variants);
                write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),blockstate);success=true;
            }
            if(!success)continue;
            String flatInventoryTexture=null,flatInventoryMaterializedTexture=null;
            if(rule.flatInventory()){
                flatInventoryTexture=sourceFlatInventoryTexture(staging,rule.registryName(),rule.sourceBlockClass());
                String itemTexture=null;
                if(flatInventoryTexture!=null){
                    flatInventoryMaterializedTexture=materializeFlatInventoryTexture(staging,ns,path,flatInventoryTexture);
                    itemTexture=flatInventoryMaterializedTexture;
                }
                if(itemTexture==null){
                    JsonObject projected=read(modelPath);itemTexture=singleTexture(projected);
                }
                if(itemTexture==null)continue;
                Path itemModel=staging.resolve("assets/"+ns+"/models/item/"+path+".json");
                write(itemModel,simpleModel("minecraft:item/generated","layer0",itemTexture));
                LegacyPresentationOwnership.revoke(staging,itemModel);
                restoreFlatInventoryOwnership(staging,id,ns,path);
            }

            JsonObject evidence=new JsonObject();evidence.addProperty("id",id);evidence.addProperty("legacyRegistryName",rule.registryName());
            evidence.addProperty("sourceBlockClass",rule.sourceBlockClass());
            if(rule.sourceRendererClass()!=null)evidence.addProperty("sourceRendererClass",rule.sourceRendererClass());
            evidence.addProperty("mode",rule.mode().name());evidence.addProperty("proof",rule.proof());
            evidence.addProperty("renderOffset",rule.renderOffset().name());
            evidence.addProperty("flatInventory",rule.flatInventory());
            if(flatInventoryTexture!=null){
                evidence.addProperty("flatInventoryTexture",flatInventoryTexture);
                evidence.addProperty("flatInventoryTextureProof","unique source item texture exact-normalized against registry/source identity");
            }
            if(flatInventoryMaterializedTexture!=null)evidence.addProperty("flatInventoryMaterializedTexture",flatInventoryMaterializedTexture);
            if(texture!=null)evidence.addProperty("texture",texture);
            if(rule.bounds()!=null){
                evidence.add("sourceBounds",boundsArray(rule.bounds()));
                evidence.addProperty("emptyCollision",rule.emptyCollision());
                if(!geometryBlocks.has(id)){
                    geometryBlocks.add(id,geometryRule(rule));
                }
            }
            // Collision/selection bounds do not authorize replacing a crossed plant mesh by
            // the six faces of its bounding box. Retain bounds, but leave rendering to JSON.
            if(geometryBlocks.has(id)) {
                JsonObject geometry=geometryBlocks.getAsJsonObject(id);
                geometry.addProperty("modelOwned",true);
                geometry.addProperty("opaque",false);
                if(rule.renderOffset()==LegacySimpleBlockRendererAnalyzer.RenderOffset.XYZ)geometry.addProperty("renderOffset","xyz");
                mergedShapes++;
            }
            evidence.addProperty("finalModelOwnership",true);rules.add(evidence);written++;
        }
        root.add("rules",rules);root.addProperty("finalModelsWritten",written);root.addProperty("shapeRulesMerged",mergedShapes);
        if(mergedShapes>0){
            dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec.parse(geometryRoot);
            write(geometryPath,geometryRoot);
        }
        Path output=staging.resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(written>0)context.diagnostics().info("LFB-CONVERT-SIMPLE-RENDER-0001",SupportLevel.ADAPTED,
                "Final simple-renderer block models written="+written+", source-proven shape rules merged="+mergedShapes
                        +"; CROSS/CROP presentation stays model-owned while native geometry carries bounds/collision.");
    }

    private static JsonObject geometryRule(Candidate rule){
        JsonObject spec=new JsonObject();spec.addProperty("family","box");spec.addProperty("opaque",false);spec.addProperty("modelOwned",true);
        spec.addProperty("sourceClass",rule.sourceBlockClass());spec.addProperty("proof","Source constructor bounds + simple CROSS/CROP renderer; collision="+(rule.emptyCollision()?"empty":"inherited"));
        JsonObject variants=new JsonObject();
        for(int meta=0;meta<16;meta++){
            JsonObject variant=new JsonObject();variant.add("bounds",boundsArray(rule.bounds()));variant.add("inventoryBounds",boundsArray(rule.bounds()));
            variant.addProperty("copyFace",-1);variant.addProperty("edges",true);variant.addProperty("collision",rule.emptyCollision()?"empty":"inherited");
            variants.add(String.valueOf(meta),variant);
        }
        spec.add("variants",variants);return spec;
    }

    private static JsonArray boundsArray(LegacySimpleBlockRendererAnalyzer.Bounds b){
        JsonArray values=new JsonArray();values.add(b.minX());values.add(b.minY());values.add(b.minZ());values.add(b.maxX());values.add(b.maxY());values.add(b.maxZ());return values;
    }

    private static boolean rewriteReferencedModels(Path staging,String ns,String path,String parent,String textureKey)throws Exception{
        Path main=staging.resolve("assets/"+ns+"/models/block/"+path+".json");
        LinkedHashSet<String> refs=new LinkedHashSet<>();refs.add(ns+":block/"+path);
        Path statePath=staging.resolve("assets/"+ns+"/blockstates/"+path+".json");
        boolean stateComplete=false;
        if(Files.isRegularFile(statePath)){
            JsonObject blockstate=read(statePath);
            if(blockstate.has("variants")&&blockstate.get("variants").isJsonObject()){
                stateComplete=true;
                for(var entry:blockstate.getAsJsonObject("variants").entrySet()){
                    if(!entry.getValue().isJsonObject()){stateComplete=false;break;}
                    JsonElement model=entry.getValue().getAsJsonObject().get("model");
                    if(model==null||!model.isJsonPrimitive()){stateComplete=false;break;}
                    refs.add(model.getAsString());
                }
            }
        }
        int rewritten=0;
        for(String ref:refs){
            String[] split=ref.split(":",2);if(split.length!=2)continue;
            Path model=modelPath(staging,ref);if(!Files.isRegularFile(model))continue;
            JsonObject current=read(model);String texture=singleTexture(current);if(texture==null)continue;
            write(model,simpleModel(parent,textureKey,texture));LegacyPresentationOwnership.revoke(staging,model);rewritten++;
        }
        if(rewritten==0)return false;
        if(!stateComplete)writeAllStates(staging,ns,path,ns+":block/"+path);
        return true;
    }

    private static String singleTexture(JsonObject model){
        if(!model.has("textures")||!model.get("textures").isJsonObject())return null;
        JsonObject textures=model.getAsJsonObject("textures");LinkedHashSet<String> concrete=new LinkedHashSet<>();
        for(var entry:textures.entrySet())if(entry.getValue().isJsonPrimitive()){
            String value=entry.getValue().getAsString();if(value!=null&&!value.isBlank()&&!value.startsWith("#"))concrete.add(value);
        }
        return concrete.size()==1?concrete.getFirst():null;
    }
    /**
     * A flat Forge inventory renderer proves that the BlockItem is a 2D sprite, but it does not
     * prove that the world block texture is that sprite. Prefer one uniquely matching source item
     * texture; ambiguity stays fail-closed and the caller falls back to the proven world material.
     */
    static String sourceFlatInventoryTexture(Path staging,String registryName,String sourceClass)throws Exception{
        LinkedHashSet<String> seeds=new LinkedHashSet<>();
        addFlatTextureSeed(seeds,registryName);
        if(sourceClass!=null){
            int slash=sourceClass.lastIndexOf('/');
            addFlatTextureSeed(seeds,slash<0?sourceClass:sourceClass.substring(slash+1));
        }
        if(seeds.isEmpty())return null;
        Path assets=staging.resolve("assets");if(!Files.isDirectory(assets))return null;
        LinkedHashSet<String> matches=new LinkedHashSet<>();
        try(var walk=Files.walk(assets)){
            for(Path file:walk.filter(Files::isRegularFile).sorted().toList()){
                Path relative=assets.relativize(file);
                if(relative.getNameCount()<4||!"textures".equalsIgnoreCase(relative.getName(1).toString()))continue;
                String directory=relative.getName(2).toString().toLowerCase(Locale.ROOT);
                if(!Set.of("item","items").contains(directory))continue;
                String filename=file.getFileName().toString();
                if(!filename.toLowerCase(Locale.ROOT).endsWith(".png"))continue;
                String stem=filename.substring(0,filename.length()-4);
                if(!seeds.contains(normalizeFlatTextureName(stem)))continue;
                String namespace=relative.getName(0).toString().toLowerCase(Locale.ROOT);
                String resourcePath=relative.subpath(2,relative.getNameCount()).toString().replace('\\','/');
                resourcePath=resourcePath.substring(0,resourcePath.length()-4);
                String resource=namespace+":"+resourcePath;
                if(resource.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))matches.add(resource);
            }
        }
        return matches.size()==1?matches.getFirst():null;
    }
    /**
     * Materialize a proven legacy flat BlockItem sprite into the generated modern item texture
     * namespace immediately. Minecraft 1.21.11 separates item and block atlases, so keeping an
     * old textures/items reference until a later pass leaves this source-owned special case too
     * easy to orphan. A modern textures/item path is discovered by the vanilla items atlas.
     */
    static String materializeFlatInventoryTexture(Path staging,String targetNamespace,String targetPath,String sourceResource)throws Exception{
        String[] source=sourceResource==null?new String[0]:sourceResource.split(":",2);
        if(source.length!=2||!targetNamespace.matches("[a-z0-9_.-]+")||!targetPath.matches("[a-z0-9/._-]+"))return null;
        Path sourceFile=staging.resolve("assets/"+source[0]+"/textures/"+source[1]+".png").normalize();
        if(!sourceFile.startsWith(staging)||!Files.isRegularFile(sourceFile))return null;
        Path target=staging.resolve("assets/"+targetNamespace+"/textures/item/lfb_flat/"+targetPath+".png").normalize();
        if(!target.startsWith(staging))return null;
        Files.createDirectories(target.getParent());
        Files.copy(sourceFile,target,StandardCopyOption.REPLACE_EXISTING);
        Path sourceMeta=sourceFile.resolveSibling(sourceFile.getFileName()+".mcmeta");
        if(Files.isRegularFile(sourceMeta)){
            Path targetMeta=target.resolveSibling(target.getFileName()+".mcmeta");
            Files.copy(sourceMeta,targetMeta,StandardCopyOption.REPLACE_EXISTING);
        }
        return targetNamespace+":item/lfb_flat/"+targetPath;
    }

    /**
     * Final ownership handoff for a source-proven flat BlockItem.
     *
     * <p>The geometry pass runs earlier and can install metadata-specific ITEM_MODEL definitions
     * under lfb_geometry. Those are valid provisional held models for ordinary blocks, but they
     * must not survive after the later simple-renderer proof establishes that the source inventory
     * renderer is flat. Repoint only geometry-owned entries for this exact block identity; any
     * independently proven metadata presentation is preserved.</p>
     */
    static void restoreFlatInventoryOwnership(Path staging,String id,String namespace,String path)throws Exception{
        if(id==null||namespace==null||path==null
                ||!id.equals(namespace+":"+path)
                ||!namespace.matches("[a-z0-9_.-]+")||!path.matches("[a-z0-9/._-]+"))return;
        Path itemDefinition=staging.resolve("assets/"+namespace+"/items/"+path+".json");
        write(itemDefinition,itemDefinition(namespace+":item/"+path));

        Path iconPath=staging.resolve(LegacyIconPresentationPass.OUTPUT);
        if(!Files.isRegularFile(iconPath))return;
        JsonObject root=read(iconPath);
        if(!root.has("items")||!root.get("items").isJsonObject())return;
        JsonObject items=root.getAsJsonObject("items");
        if(!items.has(id)||!items.get(id).isJsonObject())return;
        JsonObject variants=items.getAsJsonObject(id);
        String geometryPrefix=namespace+":lfb_geometry/"+path+"/";
        String flatDefinition=namespace+":"+path;
        boolean changed=false;
        for(var entry:new ArrayList<>(variants.entrySet())){
            JsonElement value=entry.getValue();
            if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()
                    &&value.getAsString().startsWith(geometryPrefix)){
                variants.addProperty(entry.getKey(),flatDefinition);
                changed=true;
            }
        }
        if(changed)write(iconPath,root);
    }

    private static JsonObject itemDefinition(String model){
        JsonObject root=new JsonObject(),node=new JsonObject();
        node.addProperty("type","minecraft:model");node.addProperty("model",model);root.add("model",node);
        return root;
    }

    private static void addFlatTextureSeed(Set<String> output,String raw){
        String value=normalizeFlatTextureName(raw);if(value.isBlank())return;output.add(value);
        boolean changed;
        do{
            changed=false;
            for(String prefix:List.of("tileentity","block","item","entity","render","model")){
                if(value.startsWith(prefix)&&value.length()>prefix.length()){
                    value=value.substring(prefix.length());output.add(value);changed=true;break;
                }
            }
        }while(changed);
    }
    private static String normalizeFlatTextureName(String raw){
        return raw==null?"":raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }

    private static JsonObject simpleModel(String parent,String key,String texture){
        JsonObject model=new JsonObject(),textures=new JsonObject();model.addProperty("parent",parent);textures.addProperty(key,texture);model.add("textures",textures);return model;
    }
    private static void writeAllStates(Path staging,String ns,String path,String modelId)throws Exception{
        JsonObject variants=new JsonObject();for(int meta=0;meta<16;meta++){JsonObject state=new JsonObject();state.addProperty("model",modelId);variants.add("legacy_meta="+meta,state);}
        JsonObject root=new JsonObject();root.add("variants",variants);write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),root);
    }
    private static Path modelPath(Path staging,String id){String[] p=id.split(":",2);return staging.resolve("assets/"+p[0]+"/models/"+p[1]+".json");}
    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject json)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(json)+"\n",StandardCharsets.UTF_8);}
}
