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
        root.addProperty("adaptation", "SOURCE_PROVEN_FLAT_ITEM_PLUS_BLOCK_CARRIER");
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
            LegacyVanillaStackDataFix.ModernStack modernCarrier;
            try{modernCarrier=LegacyVanillaStackDataFix.upgrade(legacyCarrier,0);}
            catch(RuntimeException unsupported){
                addSkipped(skipped,id,source,"GridPot source pot-icon carrier could not be migrated through vanilla DFU: "+unsupported.getMessage());
                continue;
            }
            if(modernCarrier.hasComponents()){
                addSkipped(skipped,id,source,"GridPot cell carrier requires ItemStack components and cannot be replayed as a plain block model.");
                continue;
            }
            float cellWidth=1.0F/integer(core,"gridWidth",0);
            float cellHeight=core.get("cellHeight").getAsFloat();
            boolean flatInventory=bool(proof,"flatInventorySourceProven");
            String inventoryTexture=flatInventory?legacyItemTexture(context.stagingDir(),string(proof,"inventoryTextureName")):null;
            boolean flatInventoryWired=flatInventory&&inventoryTexture!=null;
            if(flatInventoryWired)writeFlatItemModel(context.stagingDir(),id,inventoryTexture);
            // Own the custom-renderer block model before the generic icon pass runs later.
            // A particle-only non-rendering model prevents that pass from reclassifying the
            // GridPot BlockItem as an ordinary metadata cube and overwriting its source-proven flat item model.
            LegacySpecialBlockModelWriter.write(context.stagingDir(),id,"minecraft:block/flower_pot");
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", source);
            value.addProperty("sourceRendererClass", string(proof, "sourceRendererClass"));
            // World cell geometry is independent from the BlockItem model. The source renderer
            // proves a vanilla flower-pot carrier while Forge proves this BlockItem is flat in inventory.
            value.addProperty("cellCarrierBlockId",modernCarrier.id());
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
            value.addProperty("sourceSizedCellGeometry", false);
            value.addProperty("flatInventorySourceProven",flatInventory);
            value.addProperty("flatInventoryModelWired",flatInventoryWired);
            if(inventoryTexture!=null)value.addProperty("inventoryTexture",inventoryTexture);
            value.addProperty("inventoryUsesSameCellModel", false);
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
                "Admitted adapted GridPot presentation for " + rules.size()
                        + " block(s); source-proven flat inventory is independent from the world block-carrier renderer and stored-content branches remain adapted.");
    }

    private static void writeFlatItemModel(Path staging,String idValue,String texture)throws Exception{
        int split=idValue.indexOf(':');if(split<=0||split==idValue.length()-1)throw new IllegalArgumentException("Invalid GridPot id "+idValue);
        String ns=idValue.substring(0,split),path=idValue.substring(split+1);
        JsonObject model=new JsonObject();model.addProperty("parent","minecraft:item/generated");
        JsonObject textures=new JsonObject();textures.addProperty("layer0",texture);model.add("textures",textures);
        Path modelPath=staging.resolve("assets/"+ns+"/models/item/"+path+".json");Files.createDirectories(modelPath.getParent());
        Files.writeString(modelPath,GSON.toJson(model)+"\n",StandardCharsets.UTF_8);
        LegacyPresentationOwnership.revoke(staging,modelPath);

        JsonObject node=new JsonObject();node.addProperty("type","minecraft:model");node.addProperty("model",ns+":item/"+path);
        JsonObject definition=new JsonObject();definition.add("model",node);
        Path itemPath=staging.resolve("assets/"+ns+"/items/"+path+".json");Files.createDirectories(itemPath.getParent());
        Files.writeString(itemPath,GSON.toJson(definition)+"\n",StandardCharsets.UTF_8);
    }

    private static String legacyItemTexture(Path staging,String raw){
        if(raw==null||raw.isBlank())return null;int split=raw.indexOf(':');
        if(split<=0||split==raw.length()-1)return null;String ns=raw.substring(0,split),path=raw.substring(split+1);
        path=path.replaceFirst("^(items?|textures/items?)/","");
        if(!ns.matches("[a-z0-9_.-]+")||!path.matches("[a-zA-Z0-9_./-]+")||path.contains(".."))return null;
        for(String dir:java.util.List.of("items","item")){
            Path file=staging.resolve("assets/"+ns+"/textures/"+dir+"/"+path+".png").normalize();
            if(file.startsWith(staging)&&Files.isRegularFile(file))return ns+":"+dir+"/"+path;
        }
        return null;
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
