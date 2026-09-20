package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometry;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockGeometryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Converts source geometry into native models and per-candidate runtime geometry rules. */
public final class LegacyBlockGeometryPass implements ConversionPass {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> FACES=List.of("down","up","north","south","west","east");
    // Matches the vanilla inventory stair silhouette (upper step on the west/left side).
    private static final int HELD_STAIR_METADATA=1;
    @Override public String id(){return "source-block-geometry";}
    @Override public void apply(ConversionContext context)throws IOException {
        Path staging=context.stagingDir(),manifest=staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(manifest))return;
        JsonObject content=read(manifest),root=new JsonObject(),blocks=new JsonObject(),exclusions=new JsonObject();
        Map<String,JsonObject> definitions=new HashMap<>();
        if(content.has("blocks"))for(var e:content.getAsJsonArray("blocks")){JsonObject d=e.getAsJsonObject();if(d.has("legacyRegistryName"))definitions.put(d.get("legacyRegistryName").getAsString(),d);}
        Path iconPath=staging.resolve(LegacyIconPresentationPass.OUTPUT);
        JsonObject icons=Files.isRegularFile(iconPath)?read(iconPath):new JsonObject();
        JsonObject itemMap=icons.has("items")?icons.getAsJsonObject("items"):new JsonObject();
        var analysis=new LegacyBlockGeometryAnalyzer().analyze(context.sourceJar());
        analysis.excluded().forEach(exclusions::addProperty);int count=0,materialFallbackCount=0;
        for(var rule:analysis.rules()) {
            var input=rule.input();JsonObject def=definitions.get(input.registryName());if(def==null)continue;
            String id=def.get("id").getAsString();String[] split=id.split(":",2);String ns=split[0],path=split[1];
            Path main=staging.resolve("assets/"+ns+"/models/block/"+path+".json"),itemDef=staging.resolve("assets/"+ns+"/items/"+path+".json");
            // The icon stage owns its own generated defaults. Other specialized rewrites remain authoritative.
            boolean priorIcon=icons.has("results")&&icons.getAsJsonObject("results").has(id)
                    &&icons.getAsJsonObject("results").getAsJsonObject(id).has("defaultResolved")
                    &&icons.getAsJsonObject("results").getAsJsonObject(id).get("defaultResolved").getAsBoolean();
            if(!Files.isRegularFile(main)||(!placeholder(read(main))&&!LegacyPresentationOwnership.owns(staging,main)&&!priorIcon)
                    ||!ordinaryItemDefinition(itemDef,ns,path,priorIcon)) {exclusions.addProperty(input.registryName(),"Existing specialized presentation retained");continue;}
            JsonObject oldModel=read(main),variants=new JsonObject(),states=new JsonObject(),itemVariants=new JsonObject();
            JsonObject fallback=Files.isRegularFile(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"))?read(staging.resolve("assets/"+ns+"/blockstates/"+path+".json")):null;
            for(var v:input.variants()) {
                List<String> sprites=new ArrayList<>();boolean valid=true;
                for(int side=0;side<6;side++) {
                    String icon=v.faceIcons().get(side);
                    String sprite=icon.startsWith("minecraft:block/")?icon:LegacyIconPresentationPass.resolve(staging,icon,true);
                    if(sprite==null){valid=false;break;}
                    try{sprite=LegacyIconPresentationPass.tintSprite(staging,sprite,v.tints().get(side));}catch(IOException missing){valid=false;break;}
                    sprites.add(sprite);
                }
                if(!valid)continue;
                int metadata=v.metadata(),copy=input.neighbourFaces().getOrDefault(metadata,-1);
                if(input.collisions().getOrDefault(metadata,"unsupported").equals("unsupported"))continue;
                boolean materialFallback=input.materialFallbacks().containsKey(metadata);
                if(materialFallback&&!rule.family().startsWith("mimic_"))continue;
                if(rule.family().startsWith("mimic_")&&copy<0&&!materialFallback)continue;
                LegacyGeometry.Box box=LegacyGeometry.Box.from(v.bounds()),inv=LegacyGeometry.Box.from(v.inventoryBounds());
                boolean stairs=rule.family().endsWith("stairs"),pane=rule.family().equals("pane");
                List<LegacyGeometry.Box> world=stairs?LegacyGeometry.stairs(metadata):pane?LegacyGeometry.panes(0):List.of(box);
                List<LegacyGeometry.Box> held=stairs?LegacyGeometry.stairs(HELD_STAIR_METADATA):pane?List.of(new LegacyGeometry.Box(0,0,7d/16,1,1,9d/16)):List.of(inv);
                String worldId=ns+":block/lfb_geometry/"+path+"/"+metadata,heldId=ns+":item/lfb_geometry/"+path+"/"+metadata;
                boolean paneEdges=input.paneEdges().getOrDefault(metadata,true);
                JsonObject worldModel=pane?faceModel(LegacyGeometry.paneFaces(0,paneEdges),sprites):model(world,sprites);
                JsonObject heldModel=pane&&!paneEdges?faceModel(LegacyGeometry.curtainFaces(12),sprites):model(held,sprites);
                write(modelPath(staging,worldId),worldModel);write(modelPath(staging,heldId),heldModel);
                JsonObject state=new JsonObject();state.addProperty("model",worldId);states.add("legacy_meta="+metadata,state);
                String item=ns+":lfb_geometry/"+path+"/"+metadata;
                JsonObject nativeItem=new JsonObject(),modelRef=new JsonObject();modelRef.addProperty("type","minecraft:model");modelRef.addProperty("model",heldId);nativeItem.add("model",modelRef);
                write(staging.resolve("assets/"+ns+"/items/lfb_geometry/"+path+"/"+metadata+".json"),nativeItem);itemVariants.addProperty(String.valueOf(metadata),item);
                JsonObject spec=new JsonObject();spec.add("bounds",JSON.toJsonTree(box.values()));spec.add("inventoryBounds",JSON.toJsonTree(inv.values()));spec.addProperty("copyFace",copy);spec.addProperty("edges",input.paneEdges().getOrDefault(metadata,true));spec.addProperty("collision",input.collisions().getOrDefault(metadata,"unsupported"));
                if(materialFallback){spec.addProperty("materialMode",LegacyGeometrySpec.INVENTORY_FALLBACK);spec.addProperty("materialFallbackReason",input.materialFallbacks().get(metadata));materialFallbackCount++;}
                variants.add(String.valueOf(metadata),spec);
                if(metadata==0){write(main,worldModel);write(staging.resolve("assets/"+ns+"/models/item/"+path+".json"),heldModel);write(itemDef,nativeItem);}
            }
            if(variants.isEmpty())continue;
            for(int m=0;m<16;m++)if(!states.has("legacy_meta="+m)) {
                JsonElement original=fallback!=null&&fallback.has("variants")?fallback.getAsJsonObject("variants").get("legacy_meta="+m):null;
                if(original!=null)states.add("legacy_meta="+m,original.deepCopy());
                else {String unknown=ns+":block/lfb_geometry/"+path+"/unsupported";write(modelPath(staging,unknown),oldModel);JsonObject value=new JsonObject();value.addProperty("model",unknown);states.add("legacy_meta="+m,value);}
            }
            JsonObject blockstates=new JsonObject();blockstates.add("variants",states);write(staging.resolve("assets/"+ns+"/blockstates/"+path+".json"),blockstates);
            JsonObject spec=new JsonObject();spec.addProperty("family",rule.family());spec.addProperty("opaque",Boolean.TRUE.equals(input.opaque()));spec.addProperty("sourceClass",input.sourceClass());spec.addProperty("proof",rule.proof());spec.addProperty("sourceRendererEquivalent",false);spec.add("variants",variants);blocks.add(id,spec);
            JsonObject merged=itemMap.has(id)?itemMap.getAsJsonObject(id):new JsonObject();itemVariants.entrySet().forEach(e->merged.add(e.getKey(),e.getValue()));itemMap.add(id,merged);count++;
        }
        root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());root.addProperty("nativeGeometryAdapters",count);root.addProperty("inventoryMaterialFallbackVariants",materialFallbackCount);root.addProperty("fullRendererEquivalenceProven",false);root.add("blocks",blocks);root.add("excluded",exclusions);
        // Reparse before publishing so the runtime cannot receive a malformed generated schema.
        LegacyGeometrySpec.parse(root);write(staging.resolve(LegacyGeometrySpec.PATH),root);
        icons.add("items",itemMap);write(iconPath,icons);
        context.diagnostics().info("LFB-CONVERT-GEOMETRY-0001",SupportLevel.ADAPTED,"Native geometric adapters="+count+"; explicit inventory-material fallback variants="+materialFallbackCount+"; source-specific collision, held models and bounded connected/mimic presentation; not full renderer equivalence.");
    }
    private static boolean ordinaryItemDefinition(Path p,String namespace,String path,boolean priorIcon)throws IOException {
        if(!Files.isRegularFile(p))return true;JsonObject root=read(p);
        if(!root.has("model")||!root.get("model").isJsonObject())return false;
        JsonObject node=root.getAsJsonObject("model");
        if(!node.has("type")||!node.get("type").getAsString().equals("minecraft:model")||!node.has("model"))return false;
        String ref=node.get("model").getAsString();
        return ref.equals(namespace+":block/"+path)||ref.equals(namespace+":item/"+path)
                ||priorIcon&&(ref.startsWith(namespace+":block/"+path+"_lfb_meta_")||ref.startsWith(namespace+":item/"+path+"_lfb_meta_"));
    }
    private static boolean placeholder(JsonObject o){return o.size()==1&&o.has("parent")&&Set.of("minecraft:item/barrier","minecraft:block/magenta_glazed_terracotta").contains(o.get("parent").getAsString());}
    public static JsonObject model(List<LegacyGeometry.Box> boxes,List<String> sprites) {
        return faceModel(LegacyGeometry.surfaces(boxes),sprites);
    }
    public static JsonObject faceModel(List<LegacyGeometry.Face> surface,List<String> sprites) {
        if(sprites.size()!=6)throw new IllegalArgumentException("Six source face sprites required");
        JsonObject model=new JsonObject(),textures=new JsonObject();model.addProperty("parent","minecraft:block/block");
        for(int side=0;side<6;side++)textures.addProperty(FACES.get(side),sprites.get(side));textures.addProperty("particle",sprites.getFirst());model.add("textures",textures);
        JsonArray elements=new JsonArray();
        // Surface quads are represented as zero-thickness elements. This avoids coplanar internal
        // stair faces while retaining vanilla GUI/first-/third-person block transforms.
        for(var quad:surface) {
            List<Double> pos=quad.positions();double[] lo={1,1,1},hi={0,0,0};
            for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++){double n=pos.get(v*3+axis);lo[axis]=Math.min(lo[axis],n);hi[axis]=Math.max(hi[axis],n);}
            JsonObject element=new JsonObject(),faces=new JsonObject(),face=new JsonObject();JsonArray from=new JsonArray(),to=new JsonArray();for(int axis=0;axis<3;axis++){from.add(lo[axis]*16);to.add(hi[axis]*16);}element.add("from",from);element.add("to",to);
            face.addProperty("texture","#"+FACES.get(quad.side()));faces.add(FACES.get(quad.side()),face);element.add("faces",faces);elements.add(element);
        }
        model.add("elements",elements);return model;
    }
    private static Path modelPath(Path staging,String id){String[] p=id.split(":",2);return staging.resolve("assets/"+p[0]+"/models/"+p[1]+".json");}
    private static JsonObject read(Path path)throws IOException{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void write(Path path,JsonObject o)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(o)+"\n",StandardCharsets.UTF_8);}
}
