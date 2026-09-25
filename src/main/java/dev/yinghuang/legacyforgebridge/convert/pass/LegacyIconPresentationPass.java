package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyIconTableAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Replaces hash-proven provisional models or explicit placeholders using source icon tables. */
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
        int replaced=0, placeholderReplaced=0, variantCount=0;Set<String> stillUnresolved=new TreeSet<>(), complete=new HashSet<>();
        for(var result:analysis){
            JsonObject def=definitions.get((result.block()?"blocks:":"items:")+result.registryName());if(def==null)continue;
            String id=def.get("id").getAsString();String[] parts=id.split(":",2);if(parts.length!=2)continue;
            String ns=parts[0],path=parts[1];
            Path mainModel=staging.resolve("assets/"+ns+"/models/"+(result.block()?"block/":"item/")+path+".json");
            if(!isUnresolved(mainModel) && !LegacyPresentationOwnership.owns(staging,mainModel))continue; // Never overwrite source/specialized models.
            // A specialized native item renderer remains authoritative even when its unused
            // backing block JSON is the old unresolved marker.
            if(!plainDefinition(staging.resolve("assets/"+ns+"/items/"+path+".json"),ns,path))continue;
            JsonObject report=new JsonObject();report.addProperty("sourceClass",def.has("sourceClass")?def.get("sourceClass").getAsString():"");
            report.addProperty("limitation",result.limitation());JsonObject variants=new JsonObject(), states=new JsonObject();
            Map<String,String> uniqueModels=new LinkedHashMap<>(), uniqueDefinitions=new LinkedHashMap<>();Set<Integer> supported=new HashSet<>();
            JsonObject oldModel=read(mainModel);String unresolvedModel=ns+":block/"+path+"_lfb_unresolved";
            for(var variant:result.variants()){
                List<String> sprites=new ArrayList<>();boolean valid=true;
                for(String icon:variant.faceIcons()){String sprite=resolve(staging,icon,result.block());if(sprite==null){valid=false;break;}sprites.add(sprite);}
                if(!valid)continue;
                if(result.block()&&(variant.renderType()==1||variant.renderType()==6)&&new HashSet<>(sprites).size()!=1)continue;
                if(result.block()) {
                    for(int side=0;side<sprites.size();side++)sprites.set(side,tintSprite(staging,sprites.get(side),variant.tints().get(side)));
                }
                JsonObject model=result.block()?blockModel(variant,sprites):itemModel(sprites);
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
                if(!result.block()) {
                    JsonArray tints=new JsonArray();
                    for(int color:variant.tints()){JsonObject tint=new JsonObject();tint.addProperty("type","minecraft:constant");tint.addProperty("value",color);tints.add(tint);}
                    node.add("tints",tints);
                }
                String sameDefinition=uniqueDefinitions.get(itemRoot.toString());
                if(sameDefinition!=null)itemDefinition=sameDefinition;
                else {uniqueDefinitions.put(itemRoot.toString(),itemDefinition);write(staging.resolve("assets/"+ns+"/items/lfb_meta/"+path+"/"+variant.metadata()+".json"),itemRoot);}
                if(variant.metadata()==0) {
                    Path defaultPath=staging.resolve("assets/"+ns+"/items/"+path+".json");
                    if(plainDefinition(defaultPath,ns,path))write(defaultPath,itemRoot);
                }
                variants.addProperty(String.valueOf(variant.metadata()),itemDefinition);supported.add(variant.metadata());variantCount++;
                if(result.block()){JsonObject state=new JsonObject();state.addProperty("model",modelId);states.add("legacy_meta="+variant.metadata(),state);}
            }
            if(!supported.contains(0)){
                // Never publish a new default model unless meta zero actually resolved.
                stillUnresolved.add(id);report.addProperty("defaultResolved",false);
            }else{
                replaced++;if(isUnresolvedObject(oldModel))placeholderReplaced++;report.addProperty("defaultResolved",true);items.add(id,variants);
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
            report.addProperty("uniqueItemDefinitionsGenerated",uniqueDefinitions.size());
            report.addProperty("metadataVariantsResolved",supported.size());report.addProperty("metadataProbeLimitExclusive",result.block()?16:256);
            report.addProperty("fullRendererEquivalenceProven",false);
            results.add(id,report);
        }
        evidence.addProperty("schemaVersion",1);evidence.addProperty("sourceSha256",context.sourceHash());
        evidence.addProperty("defaultPlaceholderModelsReplaced",placeholderReplaced);evidence.addProperty("sourceDefaultModelsReplaced",replaced);evidence.addProperty("provisionalTextureModelsReplaced",replaced-placeholderReplaced);evidence.addProperty("metadataItemDefinitionsGenerated",variantCount);
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
        return isUnresolvedObject(json);
    }
    private static boolean isUnresolvedObject(JsonObject json){
        return json.size()==1&&json.has("parent")&&Set.of("minecraft:block/magenta_glazed_terracotta","minecraft:item/barrier").contains(json.get("parent").getAsString());
    }
    static String resolve(Path staging,String raw,boolean block)throws IOException{
        int colon=raw.indexOf(':');String ns=colon<0?"minecraft":raw.substring(0,colon),p=colon<0?raw:raw.substring(colon+1);
        if(p.endsWith(".png"))p=p.substring(0,p.length()-4);
        if(ns.equals("minecraft")&&!block) {
            String bare=p.replaceFirst("^items?/", "");
            if(bare.equals("book_written"))p="item/written_book";
        }
        // LegacyIconTableAnalyzer emits modern vanilla sprite identifiers only after its
        // source-side vanilla registry/name mapping has been proven. Vanilla assets are supplied
        // by Minecraft itself at runtime and are intentionally absent from the converted staging
        // tree, so do not require a copied PNG for an already-normalized modern identifier.
        if(ns.equals("minecraft")&&(p.startsWith("block/")||p.startsWith("item/"))
                &&p.matches("[a-z0-9_./-]+"))return ns+":"+p;
        List<String> candidates=new ArrayList<>();String old=block?"blocks/":"items/",modern=block?"block/":"item/";
        if(p.startsWith(old)||p.startsWith(modern))candidates.add(p);else {candidates.add(old+p);candidates.add(modern+p);}
        List<String> found=new ArrayList<>();
        for(String candidate:candidates){Path file=staging.resolve("assets/"+ns+"/textures/"+candidate+".png").normalize();
            if(!file.startsWith(staging))return null;
            if(Files.isRegularFile(file))found.add(ns+":"+candidate);
            else if(ns.equals("minecraft")&&candidate.startsWith(modern)&&(LegacyIconPresentationPass.class.getResource("/assets/minecraft/textures/"+candidate+".png")!=null||Set.of("item/string","item/sugar","item/egg","item/feather","item/book","item/paper","item/snowball","item/written_book").contains(candidate)))found.add(ns+":"+candidate);
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
    private static JsonObject itemModel(List<String> sprites){JsonObject model=new JsonObject(),textures=new JsonObject();model.addProperty("parent","minecraft:item/generated");for(int i=0;i<sprites.size();i++)textures.addProperty("layer"+i,sprites.get(i));model.add("textures",textures);return model;}
    private static boolean plainDefinition(Path path,String namespace,String id)throws IOException {
        if(!Files.isRegularFile(path))return true;
        JsonObject root=read(path);if(!root.has("model")||!root.get("model").isJsonObject())return false;
        JsonObject model=root.getAsJsonObject("model");
        return model.has("type")&&model.get("type").getAsString().equals("minecraft:model")&&model.has("model")
            && Set.of(namespace+":item/"+id,namespace+":block/"+id).contains(model.get("model").getAsString());
    }
    /** Bake only a source-proven constant tint; preserve alpha and every animation frame. */
    static String tintSprite(Path staging,String sprite,int color)throws IOException {
        if(color==0xFFFFFF)return sprite;
        String[] id=sprite.split(":",2);Path source=staging.resolve("assets/"+id[0]+"/textures/"+id[1]+".png");
        if(!Files.isRegularFile(source))throw new IOException("Cannot bake source tint without its PNG: "+sprite);
        BufferedImage input=ImageIO.read(source.toFile());if(input==null)throw new IOException("Invalid source PNG: "+source);
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((sprite+"#"+color).getBytes(StandardCharsets.UTF_8))).substring(0,24);}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        String generated="block/lfb_tinted/"+hash;Path target=staging.resolve("assets/"+id[0]+"/textures/"+generated+".png");
        if(!Files.exists(target)) {
            BufferedImage output=new BufferedImage(input.getWidth(),input.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<input.getHeight();y++)for(int x=0;x<input.getWidth();x++){
                int pixel=input.getRGB(x,y),r=((pixel>>>16)&255)*((color>>>16)&255)/255,g=((pixel>>>8)&255)*((color>>>8)&255)/255,b=(pixel&255)*(color&255)/255;
                output.setRGB(x,y,(pixel&0xFF000000)|(r<<16)|(g<<8)|b);
            }
            Files.createDirectories(target.getParent());if(!ImageIO.write(output,"PNG",target.toFile()))throw new IOException("PNG writer unavailable");
            Path metadata=source.resolveSibling(source.getFileName()+".mcmeta");
            if(Files.isRegularFile(metadata))Files.copy(metadata,target.resolveSibling(target.getFileName()+".mcmeta"),StandardCopyOption.REPLACE_EXISTING);
        }
        return id[0]+":"+generated;
    }
    private static JsonObject blockModel(LegacyIconTableAnalyzer.Variant v,List<String> sprites){
        JsonObject model=new JsonObject(),textures=new JsonObject();
        model.addProperty("parent","minecraft:block/block");
        if(v.renderType()==1||v.renderType()==6){model.addProperty("parent",v.renderType()==6?"minecraft:block/crop":"minecraft:block/cross");textures.addProperty(v.renderType()==6?"crop":"cross",sprites.getFirst());model.add("textures",textures);return model;}
        JsonObject element=new JsonObject(),faces=new JsonObject();JsonArray from=new JsonArray(),to=new JsonArray();
        for(int axis=0;axis<3;axis++){from.add(v.bounds().get(axis)*16);to.add(v.bounds().get(axis+3)*16);}element.add("from",from);element.add("to",to);
        for(int side=0;side<6;side++){String face=FACES.get(side);textures.addProperty(face,sprites.get(side));JsonObject f=new JsonObject();f.addProperty("texture","#"+face);faces.add(face,f);}
        textures.addProperty("particle",sprites.getFirst());element.add("faces",faces);JsonArray elements=new JsonArray();elements.add(element);model.add("textures",textures);model.add("elements",elements);return model;
    }
    private static JsonObject read(Path path)throws IOException{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject value)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);}
}
