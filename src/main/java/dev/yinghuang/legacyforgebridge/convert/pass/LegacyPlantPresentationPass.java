package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyPlantBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyPlantPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Materializes only source-and-asset-proven legacy plant presentation; gameplay runtime remains closed. */
public final class LegacyPlantPresentationPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-presentation-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record AssetProof(boolean complete, List<String> textures, String reason) {
        AssetProof { textures = List.copyOf(textures); }
    }

    @Override public String id() { return "legacy-plant-presentation-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyPlantPresentationAnalyzer().analyze(context.sourceJar());
        if (analysis.proofs().isEmpty()) return;
        Map<String,String> modernIds = modernBlockIds(context.stagingDir());

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int textureProof = 0, assetProof = 0, complete = 0;

        for (var proof : analysis.proofs()) {
            String modern = modernIds.get(key(proof.registryName(), proof.sourceClass()));
            AssetProof assets = proof.sourcePresentationProofComplete()
                    ? resolveAssets(context.stagingDir(), proof.family(), proof.textureName())
                    : new AssetProof(false, List.of(), "source-presentation-proof-incomplete");
            boolean presentationComplete = modern != null && proof.sourcePresentationProofComplete() && assets.complete();

            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", proof.registryName());
            if (proof.legacyNamespace() != null) value.addProperty("legacyNamespace", proof.legacyNamespace());
            value.addProperty("sourceClass", proof.sourceClass());
            value.addProperty("family", proof.family().name().toLowerCase(Locale.ROOT));
            if (proof.constructorDescriptor() != null) value.addProperty("sourceConstructor", proof.constructorDescriptor());
            value.addProperty("constructorPathStraightLine", proof.constructorPathStraightLine());
            if (proof.textureName() != null) value.addProperty("textureName", proof.textureName());
            value.addProperty("textureNameProofComplete", proof.textureNameProofComplete());
            value.addProperty("sourcePresentationProofComplete", proof.sourcePresentationProofComplete());
            value.add("sourcePresentationHooks", strings(proof.sourcePresentationHooks()));
            value.add("constructorPresentationMutations", strings(proof.constructorPresentationMutations()));
            if (modern != null) value.addProperty("modernId", modern);
            value.addProperty("modernIdentityComplete", modern != null);
            value.addProperty("assetProofComplete", assets.complete());
            value.add("textures", strings(assets.textures()));
            if (assets.reason() != null) value.addProperty("assetProofReason", assets.reason());
            value.addProperty("modelFamily", proof.family() == LegacyPlantBlockAnalyzer.Family.CROPS ? "cross_stage_0_7" : "cross_single");
            value.addProperty("renderLayer", "cutout");
            value.addProperty("cutoutRuntimeComplete", presentationComplete);
            value.addProperty("presentationComplete", presentationComplete);
            value.addProperty("runtimeComplete", false);
            rules.add(value);

            if (proof.textureNameProofComplete()) textureProof++;
            if (assets.complete()) assetProof++;
            if (presentationComplete) {
                complete++;
                materializeModels(context.stagingDir(), modern, proof.family(), assets.textures());
            }
        }

        root.add("rules", rules);
        root.addProperty("classifiedBlocks", rules.size());
        root.addProperty("textureNameProofCompleteBlocks", textureProof);
        root.addProperty("assetProofCompleteBlocks", assetProof);
        root.addProperty("presentationCompleteBlocks", complete);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info("LFB-CONVERT-PLANT-PRESENTATION-0001", SupportLevel.RUNTIME_BRIDGE,
                "Proved legacy plant presentation: blocks=" + rules.size()
                        + ", texture-name=" + textureProof + ", assets=" + assetProof
                        + ", presentation-complete=" + complete
                        + "; plant gameplay runtime remains gated.");
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PLANT-PRESENTATION-0002", SupportLevel.MANUAL_REQUIRED, message));
    }

    /**
     * Forge 1.7.10 BlockCrops.registerBlockIcons derives exactly eight icons from
     * getTextureName()+"_stage_"+stage. Reed/Bush inherit a single blockIcon texture.
     */
    private static AssetProof resolveAssets(Path staging, LegacyPlantBlockAnalyzer.Family family, String textureName) {
        if (textureName == null || textureName.isBlank()) return new AssetProof(false, List.of(), "missing-texture-name");
        int colon = textureName.indexOf(':');
        if (colon <= 0 || colon == textureName.length() - 1 || textureName.indexOf(':', colon + 1) >= 0) {
            return new AssetProof(false, List.of(), "texture-name-must-be-namespaced");
        }
        String namespace = textureName.substring(0, colon);
        String path = textureName.substring(colon + 1).replace('\\', '/');
        if (!namespace.equals(namespace.toLowerCase(Locale.ROOT)) || !path.equals(path.toLowerCase(Locale.ROOT))
                || !namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9/._-]+")
                || path.startsWith("/") || path.contains("..")) {
            return new AssetProof(false, List.of(), "texture-name-not-modern-identifier-safe");
        }

        List<String> resources = new ArrayList<>();
        if (family == LegacyPlantBlockAnalyzer.Family.CROPS) {
            for (int stage = 0; stage < 8; stage++) {
                String resourcePath = path + "_stage_" + stage;
                Path file = staging.resolve("assets/" + namespace + "/textures/blocks/" + resourcePath + ".png");
                if (!Files.isRegularFile(file)) return new AssetProof(false, List.copyOf(resources), "missing-stage-" + stage);
                resources.add(namespace + ":blocks/" + resourcePath);
            }
        } else {
            Path file = staging.resolve("assets/" + namespace + "/textures/blocks/" + path + ".png");
            if (!Files.isRegularFile(file)) return new AssetProof(false, List.of(), "missing-cross-texture");
            resources.add(namespace + ":blocks/" + path);
        }
        return new AssetProof(true, resources, null);
    }

    private static void materializeModels(Path staging, String modernId, LegacyPlantBlockAnalyzer.Family family,
                                          List<String> textures) throws Exception {
        int colon = modernId.indexOf(':');
        if (colon <= 0 || colon == modernId.length() - 1) return;
        String namespace = modernId.substring(0, colon);
        String path = modernId.substring(colon + 1);
        Path models = staging.resolve("assets/" + namespace + "/models/block");
        Path blockstates = staging.resolve("assets/" + namespace + "/blockstates");
        Path itemModels = staging.resolve("assets/" + namespace + "/models/item");
        Path items = staging.resolve("assets/" + namespace + "/items");
        Files.createDirectories(models); Files.createDirectories(blockstates); Files.createDirectories(itemModels); Files.createDirectories(items);

        JsonObject stateRoot = new JsonObject();
        JsonObject variants = new JsonObject();
        if (family == LegacyPlantBlockAnalyzer.Family.CROPS) {
            for (int stage = 0; stage < 8; stage++) {
                String modelPath = path + "_stage_" + stage;
                writeCrossModel(models.resolve(modelPath + ".json"), textures.get(stage));
                variants.add("legacy_meta=" + stage, model(namespace + ":block/" + modelPath));
            }
            // 1.7.10 BlockCrops#getIcon clamps metadata outside 0..7 to stage 7.
            for (int meta = 8; meta < 16; meta++) {
                variants.add("legacy_meta=" + meta, model(namespace + ":block/" + path + "_stage_7"));
            }
            writeItemModel(itemModels.resolve(path + ".json"), namespace + ":block/" + path + "_stage_0");
        } else {
            writeCrossModel(models.resolve(path + ".json"), textures.getFirst());
            JsonObject model = model(namespace + ":block/" + path);
            // Raw metadata is semantically irrelevant to inherited BlockBush/BlockReed presentation.
            for (int meta = 0; meta < 16; meta++) variants.add("legacy_meta=" + meta, model.deepCopy());
            writeItemModel(itemModels.resolve(path + ".json"), namespace + ":block/" + path);
        }
        stateRoot.add("variants", variants);
        writeJson(blockstates.resolve(path + ".json"), stateRoot);

        JsonObject item = new JsonObject();
        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("type", "minecraft:model");
        itemModel.addProperty("model", namespace + ":item/" + path);
        item.add("model", itemModel);
        writeJson(items.resolve(path + ".json"), item);
    }

    private static JsonObject model(String id) {
        JsonObject value = new JsonObject();
        value.addProperty("model", id);
        return value;
    }

    private static void writeCrossModel(Path path, String texture) throws Exception {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cross");
        JsonObject textures = new JsonObject();
        textures.addProperty("cross", texture);
        model.add("textures", textures);
        writeJson(path, model);
    }

    private static void writeItemModel(Path path, String parent) throws Exception {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        writeJson(path, model);
    }

    private static void writeJson(Path path, JsonObject value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static Map<String,String> modernBlockIds(Path staging) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String,String> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray blocks = content.getAsJsonArray("blocks");
            if (blocks != null) for (var element : blocks) if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                if (block.has("legacyRegistryName") && block.has("sourceClass") && block.has("id")) {
                    result.put(key(block.get("legacyRegistryName").getAsString(), block.get("sourceClass").getAsString()),
                            block.get("id").getAsString());
                }
            }
        }
        return result;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }
}
