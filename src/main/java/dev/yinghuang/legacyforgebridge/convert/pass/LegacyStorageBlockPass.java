package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyStorageBlockAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacyStoragePresentationAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Materializes source-proven six-row storage gameplay plus independently proven cube presentation. */
public final class LegacyStorageBlockPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/storage-block-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-storage-blocks"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path contentPath = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(contentPath)) return;

        Map<String, String> idsBySourceClass = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(contentPath, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray blocks = content.getAsJsonArray("blocks");
            if (blocks != null) for (var element : blocks) {
                if (!element.isJsonObject()) continue;
                JsonObject block = element.getAsJsonObject();
                if (block.has("sourceClass") && block.has("id")) {
                    idsBySourceClass.put(block.get("sourceClass").getAsString(), block.get("id").getAsString());
                }
            }
        }

        LegacyStorageBlockAnalyzer.Analysis analysis = new LegacyStorageBlockAnalyzer().analyze(context.sourceJar());
        LegacyStoragePresentationAnalyzer.Analysis presentation = new LegacyStoragePresentationAnalyzer().analyze(
                context.sourceJar(), analysis.rules().stream().map(LegacyStorageBlockAnalyzer.Rule::sourceBlockClass).toList());

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int unmapped = 0;
        int presentationComplete = 0;
        int presentationPending = 0;

        for (LegacyStorageBlockAnalyzer.Rule rule : analysis.rules()) {
            String id = idsBySourceClass.get(rule.sourceBlockClass());
            if (id == null) {
                unmapped++;
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", rule.sourceBlockClass());
            value.addProperty("sourceTileClass", rule.sourceTileClass());
            value.addProperty("legacyTileId", rule.legacyTileId());
            value.addProperty("slots", rule.slots());
            value.addProperty("rows", rule.rows());
            value.addProperty("stackLimit", rule.stackLimit());
            value.addProperty("title", rule.title());
            value.addProperty("interactionDistanceSq", rule.interactionDistanceSq());
            value.addProperty("sneakingPass", rule.sneakingPass());
            value.addProperty("dropContents", rule.dropContents());
            value.addProperty("comparator", rule.comparator());

            LegacyStoragePresentationAnalyzer.Presentation proven = presentation.presentations().get(rule.sourceBlockClass());
            String frontTexture = proven == null ? null : resolveBlockTexture(context.stagingDir(), proven.frontTexture());
            String otherTexture = proven == null ? null : resolveBlockTexture(context.stagingDir(), proven.otherTexture());
            boolean complete = proven != null && frontTexture != null && otherTexture != null;
            value.addProperty("presentationComplete", complete);
            value.addProperty("presentationPending", !complete);
            if (complete) {
                value.addProperty("orientation", proven.orientation());
                value.addProperty("sourceFrontTexture", proven.frontTexture());
                value.addProperty("sourceOtherTexture", proven.otherTexture());
                value.addProperty("frontTexture", frontTexture);
                value.addProperty("otherTexture", otherTexture);
                writeCubePresentation(context.stagingDir(), id, frontTexture, otherTexture);
                presentationComplete++;
            } else {
                presentationPending++;
                context.diagnostics().warning("LFB-CONVERT-STORAGE-0005", SupportLevel.RUNTIME_BRIDGE,
                        "Storage gameplay was migrated but source presentation remains unproven or its texture assets are missing: "
                                + rule.sourceBlockClass() + ".");
            }
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (LegacyStorageBlockAnalyzer.Skipped entry : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", entry.registryName());
            value.addProperty("sourceBlockClass", entry.sourceBlockClass());
            value.addProperty("reason", entry.reason());
            skipped.add(value);
        }
        root.add("skipped", skipped);
        JsonArray presentationDiagnostics = new JsonArray();
        presentation.diagnostics().forEach(presentationDiagnostics::add);
        root.add("presentationDiagnostics", presentationDiagnostics);
        root.addProperty("unmappedRules", unmapped);
        root.addProperty("presentationCompleteRules", presentationComplete);
        root.addProperty("presentationPendingRules", presentationPending);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning("LFB-CONVERT-STORAGE-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }
        if (unmapped > 0) {
            context.diagnostics().warning("LFB-CONVERT-STORAGE-0003", SupportLevel.MANUAL_REQUIRED,
                    "Source-proven storage rules could not be mapped to generated block identities: " + unmapped + ".");
        }
        if (!analysis.skipped().isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-STORAGE-0004", SupportLevel.RUNTIME_BRIDGE,
                    "BlockContainer candidates outside the bounded six-row storage family remain fail-closed: "
                            + analysis.skipped().size() + ".");
        }
        if (!rules.isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-STORAGE-0001", SupportLevel.ADAPTED,
                    "Materialized source-proven six-row storage rules: " + rules.size()
                            + ", presentationComplete=" + presentationComplete
                            + ", presentationPending=" + presentationPending + ".");
        }
    }

    private static String resolveBlockTexture(Path staging, String legacyIcon) {
        int colon = legacyIcon == null ? -1 : legacyIcon.indexOf(':');
        if (colon <= 0 || colon == legacyIcon.length() - 1) return null;
        String namespace = legacyIcon.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
        String path = legacyIcon.substring(colon + 1).replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        List<TextureCandidate> candidates = new ArrayList<>();
        addTexture(staging, candidates, namespace, "blocks/" + path, namespace + ":blocks/" + path);
        addTexture(staging, candidates, namespace, "block/" + path, namespace + ":block/" + path);
        addTexture(staging, candidates, namespace, path, namespace + ":" + path);
        return candidates.size() == 1 ? candidates.getFirst().resource() : null;
    }

    private static void addTexture(Path staging, List<TextureCandidate> output, String namespace,
                                   String filePath, String resource) {
        Path file = staging.resolve("assets").resolve(namespace).resolve("textures").resolve(filePath + ".png");
        if (Files.isRegularFile(file)) output.add(new TextureCandidate(file, resource));
    }

    private record TextureCandidate(Path file, String resource) { }

    private static void writeCubePresentation(Path staging, String idValue,
                                              String frontTexture, String otherTexture) throws Exception {
        int colon = idValue.indexOf(':');
        if (colon <= 0 || colon == idValue.length() - 1) throw new IllegalArgumentException("Invalid block id " + idValue);
        String namespace = idValue.substring(0, colon);
        String path = idValue.substring(colon + 1);

        Path blockModel = staging.resolve("assets/" + namespace + "/models/block/" + path + ".json");
        Path allOtherModel = staging.resolve("assets/" + namespace + "/models/block/" + path + "_all_other.json");
        Path blockState = staging.resolve("assets/" + namespace + "/blockstates/" + path + ".json");
        Path itemModel = staging.resolve("assets/" + namespace + "/models/item/" + path + ".json");
        Path itemDefinition = staging.resolve("assets/" + namespace + "/items/" + path + ".json");
        Files.createDirectories(blockModel.getParent());
        Files.createDirectories(blockState.getParent());
        Files.createDirectories(itemModel.getParent());
        Files.createDirectories(itemDefinition.getParent());

        Files.writeString(blockModel, GSON.toJson(cubeModel(frontTexture, otherTexture, "north")) + "\n", StandardCharsets.UTF_8);
        JsonObject allOther = new JsonObject();
        allOther.addProperty("parent", "minecraft:block/cube_all");
        JsonObject allTextures = new JsonObject(); allTextures.addProperty("all", otherTexture); allOther.add("textures", allTextures);
        Files.writeString(allOtherModel, GSON.toJson(allOther) + "\n", StandardCharsets.UTF_8);

        JsonObject state = new JsonObject(); JsonObject variants = new JsonObject();
        variants.add("legacy_meta=0", variant(namespace + ":block/" + path, 0));
        variants.add("legacy_meta=1", variant(namespace + ":block/" + path, 90));
        variants.add("legacy_meta=2", variant(namespace + ":block/" + path, 180));
        variants.add("legacy_meta=3", variant(namespace + ":block/" + path, 270));
        for (int meta = 4; meta <= 15; meta++) {
            variants.add("legacy_meta=" + meta, variant(namespace + ":block/" + path + "_all_other", 0));
        }
        state.add("variants", variants);
        Files.writeString(blockState, GSON.toJson(state) + "\n", StandardCharsets.UTF_8);

        Files.writeString(itemModel, GSON.toJson(cubeModel(frontTexture, otherTexture, "south")) + "\n", StandardCharsets.UTF_8);
        JsonObject item = new JsonObject(); JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model"); model.addProperty("model", namespace + ":item/" + path); item.add("model", model);
        Files.writeString(itemDefinition, GSON.toJson(item) + "\n", StandardCharsets.UTF_8);
    }

    private static JsonObject cubeModel(String front, String other, String frontFace) {
        JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:block/cube");
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", other);
        for (String face : List.of("down", "up", "north", "south", "west", "east")) {
            textures.addProperty(face, face.equals(frontFace) ? front : other);
        }
        model.add("textures", textures);
        return model;
    }

    private static JsonObject variant(String modelId, int y) {
        JsonObject value = new JsonObject(); value.addProperty("model", modelId);
        if (y != 0) value.addProperty("y", y);
        return value;
    }
}
