package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyIconTableAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Replaces only converter-owned unresolved models, using source icon/metadata tables, never names. */
public final class LegacyIconPresentationPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/icon-presentation.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> FACES=List.of("down","up","north","south","west","east");
    @Override public String id(){return "source-icon-metadata-presentation";}

    @Override public void apply(ConversionContext context) throws IOException {
        Path staging=context.stagingDir(), contentPath=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(contentPath))return;
        JsonObject content=read(contentPath), evidence=new JsonObject(), items=new JsonObject(), results=new JsonObject();
        Map<String,JsonObject> definitions=new HashMap<>();
        for(String kind:List.of("blocks","items")) if(content.has(kind))for(JsonElement el:content.getAsJsonArray(kind)){
            JsonObject def=el.getAsJsonObject();if(def.has("legacyRegistryName"))definitions.put(kind+":"+def.get("legacyRegistryName").getAsString(),def);
        }
        var registrations=new LegacyRegistryAnalyzer().analyze(context.sourceJar()).registrations();
        var analysis=new LegacyIconTableAnalyzer().analyze(context.sourceJar(),registrations);
        int replaced=0, variantCount=0;Set<String> stillUnresolved=new TreeSet<>(), complete=new HashSet<>();
        for(var result:analysis){
            JsonObject def=definitions.get((result.block()?"blocks:":"items:")+result.registryName());if(def==null)continue;
            String id=def.get("id").getAsString();String[] parts=id.split(":",2);if(parts.length!=2)continue;
            String ns=parts[0],path=parts[1];
            Path mainModel=staging.resolve("assets/"+ns+"/models/"+(result.block()?"block/":"item/")+path+".json");
            if(!isUnresolved(mainModel))continue; // Specialized/generated presentation remains authoritative.
            JsonObject report=new JsonObject();report.addProperty("sourceClass",def.has("sourceClass")?def.get("sourceClass").getAsString():"");
            report.addProperty("limitation",result.limitation());JsonObject variants=new JsonObject(), states=new JsonObject();
            Map<String,String> uniqueModels=new LinkedHashMap<>();Set<Integer> supported=new HashSet<>();
            JsonObject oldModel=read(mainModel);String unresolvedModel=ns+":block/"+path+"_lfb_unresolved";
            for(var variant:result.variants()){
                List<String> sprites=new ArrayList<>();boolean valid=true;
                for(String icon:variant.faceIcons()){String sprite=resolve(staging,icon,result.block());if(sprite==null){valid=false;break;}sprites.add(sprite);}
                if(!valid)continue;
                if(result.block()&&(variant.renderType()==1||variant.renderType()==6)&&new HashSet<>(sprites).size()!=1)continue;
                JsonObject model=result.block()?blockModel(variant,sprites):itemModel(sprites.getFirst());
                String fingerprint=model.toString();String modelId=uniqueModels.get(fingerprint);
                if(modelId==null){
                    modelId=ns+":"+(result.block()?"block/":"item/")+path+"_lfb_meta_"+variant.metadata();
                    write(staging.resolve("assets/"+ns+"/models/"+modelId.substring(modelId.indexOf(':')+1)+".json"),model);
                    uniqueModels.put(fingerprint,modelId);
                }
                if(variant.metadata()==0)write(mainModel,model);
                String inventoryModelId=modelId;
                if(result.block()&&!variant.bounds().equals(variant.inventoryBounds())){
                    inventoryModelId=ns+":item/"+path+"_lfb_meta_"+variant.metadata();
                    var inventoryVariant=new LegacyIconTableAnalyzer.Variant(variant.metadata(),variant.faceIcons(),variant.inventoryBounds(),variant.inventoryBounds(),variant.renderType());
                    write(staging.resolve("assets/"+ns+"/models/item/"+path+"_lfb_meta_"+variant.metadata()+".json"),blockModel(inventoryVariant,sprites));
                }
                String itemDefinition=ns+":lfb_meta/"+path+"/"+variant.metadata();
                JsonObject itemRoot=new JsonObject(), node=new JsonObject();node.addProperty("type","minecraft:model");node.addProperty("model",inventoryModelId);itemRoot.add("model",node);
                write(staging.resolve("assets/"+ns+"/items/lfb_meta/"+path+"/"+variant.metadata()+".json"),itemRoot);
                variants.addProperty(String.valueOf(variant.metadata()),itemDefinition);supported.add(variant.metadata());variantCount++;
                if(result.block()){JsonObject state=new JsonObject();state.addProperty("model",modelId);states.add("legacy_meta="+variant.metadata(),state);}
            }
            if(!supported.contains(0)){
                // Never publish a new default model unless meta zero actually resolved.
                stillUnresolved.add(id);report.addProperty("defaultResolved",false);
            }else{
                replaced++;report.addProperty("defaultResolved",true);items.add(id,variants);
                if(result.block()){
                    for(int meta=0;meta<16;meta++)if(!states.has("legacy_meta="+meta)){
                        write(staging.resolve("assets/"+ns+"/models/block/"+path+"_lfb_unresolved.json"),oldModel);
                        JsonObject state=new JsonObject();state.addProperty("model",unresolvedModel);states.add("legacy_meta="+meta,state);
                    }
                    JsonObject blockstate=new JsonObject();blockstate.add("variants",states);
                    write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),blockstate);
                }
                if(supported.size()==(result.block()?16:256))complete.add(id);else stillUnresolved.add(id);
            }
            report.addProperty("metadataVariantsResolved",supported.size());report.addProperty("metadataProbeLimitExclusive",result.block()?16:256);
            report.addProperty("fullRendererEquivalenceProven",false);
            results.add(id,report);
        }
        evidence.addProperty("schemaVersion",1);evidence.addProperty("sourceSha256",context.sourceHash());
        evidence.addProperty("defaultPlaceholderModelsReplaced",replaced);evidence.addProperty("metadataItemDefinitionsGenerated",variantCount);
        evidence.addProperty("fullRendererEquivalenceProven",false);evidence.add("items",items);evidence.add("results",results);
        evidence.add("remainingAnalyzedIdentities",JSON.toJsonTree(stillUnresolved));write(staging.resolve(OUTPUT),evidence);
        // Amend the baseline honestly: a present file is not evidence that every source metadata
        // or custom renderer has been converted. Preserve unresolved and partial identities.
        Path baselinePath=staging.resolve(LegacyClientContentBaselinePass.OUTPUT);
        if(Files.isRegularFile(baselinePath)){
            JsonObject baseline=read(baselinePath);JsonArray remaining=new JsonArray();
            if(baseline.has("unresolvedModels"))for(JsonElement el:baseline.getAsJsonArray("unresolvedModels"))if(!complete.contains(el.getAsString()))remaining.add(el);
            baseline.add("unresolvedModels",remaining);baseline.addProperty("unresolvedModelCount",remaining.size());
            baseline.addProperty("sourceIconDefaultsRecovered",replaced);baseline.addProperty("presentationIdentityComplete",false);
            write(baselinePath,baseline);
        }
        context.diagnostics().info("LFB-CONVERT-ICON-0001",SupportLevel.ADAPTED,
                "Recovered source icon defaults="+replaced+", metadata item definitions="+variantCount+". Custom renderer equivalence is not implied.");
    }
    private static boolean isUnresolved(Path path)throws IOException{
        if(!Files.isRegularFile(path))return false;JsonObject json=read(path);
        return json.size()==1&&json.has("parent")&&Set.of("minecraft:block/magenta_glazed_terracotta","minecraft:item/barrier").contains(json.get("parent").getAsString());
    }
    private static String resolve(Path staging,String raw,boolean block)throws IOException{
        int colon=raw.indexOf(':');String ns=colon<0?"minecraft":raw.substring(0,colon),p=colon<0?raw:raw.substring(colon+1);
        if(p.endsWith(".png"))p=p.substring(0,p.length()-4);
        List<String> candidates=new ArrayList<>();String old=block?"blocks/":"items/",modern=block?"block/":"item/";
        if(p.startsWith(old)||p.startsWith(modern))candidates.add(p);else {candidates.add(old+p);candidates.add(modern+p);}
        List<String> found=new ArrayList<>();
        for(String candidate:candidates){Path file=staging.resolve("assets/"+ns+"/textures/"+candidate+".png").normalize();
            if(!file.startsWith(staging))return null;
            if(Files.isRegularFile(file))found.add(ns+":"+candidate);
            else if(ns.equals("minecraft")&&candidate.startsWith(modern)&&LegacyIconPresentationPass.class.getResource("/assets/minecraft/textures/"+candidate+".png")!=null)found.add(ns+":"+candidate);
        }
        if(found.size()==1)return found.getFirst();
        // Case-insensitive matching is safe only when the exact icon path identifies one file.
        if(found.isEmpty()&&!ns.equals("minecraft")){
            Path root=staging.resolve("assets");if(!Files.isDirectory(root))return null;
            try(Stream<Path> stream=Files.walk(root)){
                for(Path file:stream.filter(Files::isRegularFile).toList()){
                    String rel=root.relativize(file).toString().replace('\\','/');
                    for(String candidate:candidates)if(rel.equalsIgnoreCase(ns+"/textures/"+candidate+".png")){
                        int at=rel.indexOf("/textures/");found.add(rel.substring(0,at)+":"+rel.substring(at+10,rel.length()-4));
                    }
                }
            }
        }
        return found.size()==1?found.getFirst():null;
    }
    private static JsonObject itemModel(String sprite){JsonObject model=new JsonObject(),textures=new JsonObject();model.addProperty("parent","minecraft:item/generated");textures.addProperty("layer0",sprite);model.add("textures",textures);return model;}
    private static JsonObject blockModel(LegacyIconTableAnalyzer.Variant v,List<String> sprites){
        JsonObject model=new JsonObject(),textures=new JsonObject();
        if(v.renderType()==1||v.renderType()==6){model.addProperty("parent",v.renderType()==6?"minecraft:block/crop":"minecraft:block/cross");textures.addProperty(v.renderType()==6?"crop":"cross",sprites.getFirst());model.add("textures",textures);return model;}
        JsonObject element=new JsonObject(),faces=new JsonObject();JsonArray from=new JsonArray(),to=new JsonArray();
        for(int axis=0;axis<3;axis++){from.add(v.bounds().get(axis)*16);to.add(v.bounds().get(axis+3)*16);}element.add("from",from);element.add("to",to);
        for(int side=0;side<6;side++){String face=FACES.get(side);textures.addProperty(face,sprites.get(side));JsonObject f=new JsonObject();f.addProperty("texture","#"+face);faces.add(face,f);}
        textures.addProperty("particle",sprites.getFirst());element.add("faces",faces);JsonArray elements=new JsonArray();elements.add(element);model.add("textures",textures);model.add("elements",elements);return model;
    }
    private static JsonObject read(Path path)throws IOException{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject value)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
}
