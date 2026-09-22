package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyVanillaStackDataFix;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Promotes source-proven GridPot presentation into a client runtime registration plan. */
public final class LegacyGridPotPresentationRuntimePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/grid-pot-presentation-runtime.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-grid-pot-presentation-runtime"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path proofPath = context.stagingDir().resolve(LegacyGridPotPresentationProofPass.OUTPUT);
        Path corePath = context.stagingDir().resolve(LegacyGridPotBlockPass.OUTPUT);
        if (!Files.isRegularFile(proofPath) || !Files.isRegularFile(corePath)) return;

        JsonObject proofRoot = JsonParser.parseString(Files.readString(proofPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject coreRoot = JsonParser.parseString(Files.readString(corePath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(proofRoot, "schemaVersion", 0) != 1 || integer(coreRoot, "schemaVersion", 0) != 1) return;

        Map<String,JsonObject> proofBySource = new LinkedHashMap<>();
        JsonArray proofs = proofRoot.getAsJsonArray("rules");
        if (proofs != null) for (JsonElement element : proofs) {
            if (!element.isJsonObject()) continue;
            JsonObject proof = element.getAsJsonObject();
            if (!bool(proof, "storedContentPresentationProven")) continue;
            String source = string(proof, "sourceBlockClass");
            if (source == null) continue;
            JsonObject previous = proofBySource.putIfAbsent(source, proof);
            if (previous != null && !previous.equals(proof)) proofBySource.remove(source);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("adaptation", "SOURCE_SIZED_3D_CELL_ITEM_MODEL");
        root.addProperty("storedContentPresentationRuntimeWired", true);
        JsonArray rules = new JsonArray();
        JsonArray skipped = new JsonArray();
        JsonArray coreRules = coreRoot.getAsJsonArray("rules");
        if (coreRules != null) for (JsonElement element : coreRules) {
            if (!element.isJsonObject()) continue;
            JsonObject core = element.getAsJsonObject();
            String source = string(core, "sourceBlockClass");
            JsonObject proof = source == null ? null : proofBySource.get(source);
            if (!bool(core, "coreRuntimeComplete") || proof == null) continue;
            String id = string(core, "id");
            if (id == null || integer(core, "cells", 0) != 9 || integer(core, "gridWidth", 0) != 3) {
                addSkipped(skipped, id, source, "GridPot core rule does not satisfy the bounded 3x3 presentation runtime family.");
                continue;
            }
            JsonArray offsets = proof.getAsJsonArray("gridOffsets");
            String legacyCarrier=string(proof,"cellCarrierLegacyRegistryName");
            if (offsets == null || offsets.size() != 3 || legacyCarrier==null
                    || !finite(proof, "contentTranslateY") || !finite(proof, "crossedScale")) {
                addSkipped(skipped, id, source, "GridPot presentation proof has malformed transforms.");
                continue;
            }
            try{LegacyVanillaStackDataFix.upgrade(legacyCarrier,0);}
            catch(RuntimeException unsupported){
                addSkipped(skipped,id,source,"GridPot source pot-icon carrier could not be migrated through vanilla DFU: "+unsupported.getMessage());
                continue;
            }
            float cellWidth=1.0F/integer(core,"gridWidth",0);
            float cellHeight=core.get("cellHeight").getAsFloat();
            writeCellItemModel(context.stagingDir(),id,cellWidth,cellHeight);
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", source);
            value.addProperty("sourceRendererClass", string(proof, "sourceRendererClass"));
            // Reuse the converted GridPot BlockItem itself. Its generated item model below is a
            // source-sized 3D cell rather than a flat legacy item sprite or a full vanilla pot.
            value.addProperty("cellCarrierItemId",id);
            value.addProperty("cellBodyWidth",cellWidth);
            value.addProperty("cellBodyHeight",cellHeight);
            value.add("gridOffsets", offsets.deepCopy());
            value.addProperty("contentTranslateY", proof.get("contentTranslateY").getAsFloat());
            value.addProperty("sourceContentScale", proof.get("crossedScale").getAsFloat());
            value.addProperty("itemDisplayContext", "NONE");
            value.addProperty("boundingBoxCentered", true);
            value.addProperty("boundingBoxBottomAligned", true);
            value.addProperty("storedContentPresentationProven", true);
            value.addProperty("storedContentPresentationRuntimeWired", true);
            value.addProperty("exactLegacyGeometry", false);
            value.addProperty("sourceSizedCellGeometry", true);
            value.addProperty("inventoryUsesSameCellModel", true);
            rules.add(value);
        }
        root.add("rules", rules);
        root.add("skipped", skipped);
        root.addProperty("runtimeRules", rules.size());
        root.addProperty("skippedRules", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-GRIDPOT-PRESENT-RUNTIME-0001", SupportLevel.ADAPTED,
                "Admitted source-sized 3D GridPot cell presentation for " + rules.size()
                        + " block(s); world and inventory now share the same 3D cell model while stored-content render branches remain adapted.");
    }

    private static void writeCellItemModel(Path staging,String idValue,float width,float height)throws Exception{
        int split=idValue.indexOf(':');if(split<=0||split==idValue.length()-1)throw new IllegalArgumentException("Invalid GridPot id "+idValue);
        String ns=idValue.substring(0,split),path=idValue.substring(split+1);
        float half=width*8F;
        float x0=8F-half,x1=8F+half,z0=x0,z1=x1,y1=height*16F;
        JsonObject model=new JsonObject();model.addProperty("parent","minecraft:block/block");
        JsonObject textures=new JsonObject();textures.addProperty("pot","minecraft:block/flower_pot");
        textures.addProperty("dirt","minecraft:block/dirt");textures.addProperty("particle","minecraft:block/flower_pot");
        model.add("textures",textures);JsonArray elements=new JsonArray();

        JsonObject body=new JsonObject();body.add("from",vec(x0,0F,z0));body.add("to",vec(x1,y1,z1));
        JsonObject bodyFaces=new JsonObject();
        for(String face:java.util.List.of("down","north","south","west","east")){
            JsonObject f=new JsonObject();f.addProperty("texture","#pot");bodyFaces.add(face,f);
        }
        // The source renderer draws a separate dirt top rather than closing the cell with the pot icon.
        body.add("faces",bodyFaces);elements.add(body);

        float inset=Math.max(.5F,width*1.5F);
        JsonObject dirt=new JsonObject();dirt.add("from",vec(x0+inset,Math.max(0F,y1-.20F),z0+inset));
        dirt.add("to",vec(x1-inset,y1-.05F,z1-inset));JsonObject dirtFaces=new JsonObject();
        JsonObject up=new JsonObject();up.addProperty("texture","#dirt");dirtFaces.add("up",up);dirt.add("faces",dirtFaces);elements.add(dirt);
        model.add("elements",elements);

        Path modelPath=staging.resolve("assets/"+ns+"/models/item/"+path+".json");Files.createDirectories(modelPath.getParent());
        Files.writeString(modelPath,GSON.toJson(model)+"\n",StandardCharsets.UTF_8);

        JsonObject node=new JsonObject();node.addProperty("type","minecraft:model");node.addProperty("model",ns+":item/"+path);
        JsonObject definition=new JsonObject();definition.add("model",node);
        Path itemPath=staging.resolve("assets/"+ns+"/items/"+path+".json");Files.createDirectories(itemPath.getParent());
        Files.writeString(itemPath,GSON.toJson(definition)+"\n",StandardCharsets.UTF_8);
    }

    private static JsonArray vec(float x,float y,float z){
        JsonArray out=new JsonArray();out.add(x);out.add(y);out.add(z);return out;
    }

    private static void addSkipped(JsonArray skipped, String id, String source, String reason) {
        JsonObject value = new JsonObject();
        if (id != null) value.addProperty("id", id);
        if (source != null) value.addProperty("sourceBlockClass", source);
        value.addProperty("reason", reason);
        skipped.add(value);
    }
    private static boolean finite(JsonObject value, String key) {
        JsonElement element = value.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return false;
        float number = element.getAsFloat();
        return Float.isFinite(number);
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
