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
import java.util.stream.Stream;

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
        int textureProof = 0, sourceModelProof = 0, assetProof = 0, complete = 0;

        for (var proof : analysis.proofs()) {
            String modern = modernIds.get(key(proof.registryName(), proof.sourceClass()));
            boolean sourceModelPresentationComplete = proof.constructorPathStraightLine()
                    && proof.textureNameProofComplete()
                    && proof.sourcePresentationHooks().isEmpty();
            AssetProof assets = proof.textureNameProofComplete()
                    ? resolveAssets(context.stagingDir(), proof.family(), proof.textureName(), proof.legacyNamespace())
                    : new AssetProof(false, List.of(), "source-texture-proof-incomplete");
            boolean presentationComplete = modern != null && sourceModelPresentationComplete && assets.complete();

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
            value.addProperty("sourceModelPresentationProofComplete", sourceModelPresentationComplete);
            value.add("sourcePresentationHooks", strings(proof.sourcePresentationHooks()));
            value.add("constructorPresentationMutations", strings(proof.constructorPresentationMutations()));
            value.addProperty("constructorPresentationMutationsAffectGameplayRuntime", !proof.constructorPresentationMutations().isEmpty());
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
            if (sourceModelPresentationComplete) sourceModelProof++;
            if (assets.complete()) assetProof++;
            if (presentationComplete) {
                complete++;
                materializeModels(context.stagingDir(), modern, proof.family(), assets.textures());
            }
        }

        root.add("rules", rules);
        root.addProperty("classifiedBlocks", rules.size());
        root.addProperty("textureNameProofCompleteBlocks", textureProof);
        root.addProperty("sourceModelPresentationProofCompleteBlocks", sourceModelProof);
        root.addProperty("assetProofCompleteBlocks", assetProof);
        root.addProperty("presentationCompleteBlocks", complete);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info("LFB-CONVERT-PLANT-PRESENTATION-0001", SupportLevel.RUNTIME_BRIDGE,
                "Proved legacy plant presentation: blocks=" + rules.size()
                        + ", texture-name=" + textureProof + ", source-model=" + sourceModelProof
                        + ", assets=" + assetProof + ", presentation-complete=" + complete
                        + "; plant gameplay runtime remains gated.");
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PLANT-PRESENTATION-0002", SupportLevel.MANUAL_REQUIRED, message));
    }

    /**
     * Forge 1.7.10 BlockCrops.registerBlockIcons derives exactly eight icons from
     * getTextureName()+"_stage_"+stage. Reed/Bush inherit a single blockIcon texture.
     */
    private static AssetProof resolveAssets(Path staging, LegacyPlantBlockAnalyzer.Family family,
                                             String textureName, String fallbackNamespace) throws Exception {
        TextureName normalized = normalizeTextureName(textureName, fallbackNamespace);
        if (normalized == null) return new AssetProof(false, List.of(), "missing-or-unsafe-texture-name");

        List<String> resources = new ArrayList<>();
        if (family == LegacyPlantBlockAnalyzer.Family.CROPS) {
            for (int stage = 0; stage < 8; stage++) {
                String resourcePath = normalized.path() + "_stage_" + stage;
                String texture = resolveBlockTexture(staging, normalized.namespace(), resourcePath);
                if (texture == null) return new AssetProof(false, List.copyOf(resources), "missing-stage-" + stage);
                resources.add(texture);
            }
        } else {
            String texture = resolveBlockTexture(staging, normalized.namespace(), normalized.path());
            if (texture == null) return new AssetProof(false, List.of(), "missing-cross-texture");
            resources.add(texture);
        }
        return new AssetProof(true, resources, null);
    }

    private record TextureName(String namespace, String path) { }

    private static TextureName normalizeTextureName(String textureName, String fallbackNamespace) {
        if (textureName == null || textureName.isBlank()) return null;
        String value = textureName.trim().replace('\\', '/');
        int colon = value.indexOf(':');
        if (colon != value.lastIndexOf(':')) return null;

        String namespace;
        String path;
        if (colon >= 0) {
            namespace = value.substring(0, colon);
            path = value.substring(colon + 1);
        } else if (fallbackNamespace != null && !fallbackNamespace.isBlank()) {
            namespace = fallbackNamespace;
            path = value;
        } else {
            return null;
        }

        namespace = namespace.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT);
        if (path.startsWith("textures/blocks/")) path = path.substring("textures/blocks/".length());
        if (path.startsWith("blocks/")) path = path.substring("blocks/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);

        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9/._-]+")
                || path.isBlank() || path.startsWith("/") || path.contains("..")) {
            return null;
        }
        return new TextureName(namespace, path);
    }

    private static String resolveBlockTexture(Path staging, String namespace, String resourcePath) throws Exception {
        Path blocksRoot = staging.resolve("assets/" + namespace + "/textures/blocks");
        Path lower = blocksRoot.resolve(resourcePath + ".png");
        if (Files.isRegularFile(lower)) return namespace + ":blocks/" + resourcePath;

        String normalized = resourcePath.toLowerCase(Locale.ROOT);
        Path lowerNormalized = blocksRoot.resolve(normalized + ".png");
        if (Files.isRegularFile(lowerNormalized)) return namespace + ":blocks/" + normalized;

        if (!Files.isDirectory(blocksRoot)) return null;
        String wanted = normalized + ".png";
        try (Stream<Path> files = Files.walk(blocksRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> blocksRoot.relativize(path).toString().replace('\\', '/')
                            .equalsIgnoreCase(wanted))
                    .findFirst()
                    .map(path -> namespace + ":blocks/" + blocksRoot.relativize(path).toString()
                            .replace('\\', '/')
                            .replaceAll("(?i)\\.png$", "")
                            .toLowerCase(Locale.ROOT))
                    .orElse(null);
        }
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
