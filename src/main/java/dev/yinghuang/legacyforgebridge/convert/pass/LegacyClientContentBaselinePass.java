package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Completes the generic client-visible resource surface for discovered legacy content.
 *
 * <p>Earlier semantic presentation passes retain priority. This pass only fills missing model,
 * blockstate, item-definition and creative-tab paths with deterministic visible fallbacks. It does
 * not recreate server-authoritative gameplay or infer behavior from a mod name.</p>
 */
public final class LegacyClientContentBaselinePass implements ConversionPass {
    public static final String CONTENT = "legacyforgebridge/converted-content.json";
    public static final String OUTPUT = "legacyforgebridge/client-content-baseline.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-client-content-baseline";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path contentPath = context.stagingDir().resolve(CONTENT);
        if (!Files.isRegularFile(contentPath)) return;

        JsonObject content = read(contentPath);
        String namespace = string(content, "namespace", context.metadata().fabricId());
        JsonArray blocks = array(content, "blocks");
        JsonArray items = array(content, "items");
        Stats stats = new Stats(blocks.size(), items.size());

        if (ensureCreativeTab(
                content,
                namespace,
                blocks,
                items,
                context.metadata().primary().name(),
                stats
        )) {
            write(contentPath, content);
        }

        for (JsonElement value : blocks) {
            if (value.isJsonObject()) ensureBlock(
                    context.stagingDir(),
                    namespace,
                    value.getAsJsonObject(),
                    stats
            );
        }
        for (JsonElement value : items) {
            if (value.isJsonObject()) ensureItem(
                    context.stagingDir(),
                    namespace,
                    value.getAsJsonObject(),
                    stats
            );
        }

        write(context.stagingDir().resolve(OUTPUT), evidence(context, namespace, stats));
        context.diagnostics().info(
                "LFB-CONVERT-CLIENT-CONTENT-0001",
                SupportLevel.ADAPTED,
                "Completed generic client resource baseline: blocks=" + stats.blockCount
                        + ", items=" + stats.itemCount
                        + ", creativeTabsGenerated=" + stats.creativeTabsGenerated
                        + ", resourceFilesCreated=" + stats.createdFiles
                        + ", preservedSpecializedFiles=" + stats.preservedFiles
                        + ", visibleFallbackModels=" + stats.fallbackModels + "."
        );
        if (stats.fallbackModels > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-CLIENT-CONTENT-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Some discovered content had no model emitted by a semantic presentation pass. "
                            + "Visible vanilla fallback models were emitted instead of missing-model cubes; "
                            + "fallbacks=" + stats.fallbackModels + "."
            );
        }
    }

    private static boolean ensureCreativeTab(
            JsonObject content,
            String namespace,
            JsonArray blocks,
            JsonArray items,
            String title,
            Stats stats
    ) {
        JsonArray existing = array(content, "creativeTabs");
        if (!existing.isEmpty()) return false;

        Set<String> entries = new LinkedHashSet<>();
        collectIds(blocks, entries);
        collectIds(items, entries);
        if (entries.isEmpty()) return false;

        JsonObject tab = new JsonObject();
        tab.addProperty("id", namespace + ":converted_content");
        tab.addProperty("titleKey", "");
        tab.addProperty("title", title == null || title.isBlank() ? namespace : title);
        tab.addProperty("icon", entries.iterator().next());
        JsonArray tabItems = new JsonArray();
        entries.forEach(tabItems::add);
        tab.add("items", tabItems);
        existing.add(tab);
        content.add("creativeTabs", existing);

        stats.creativeTabsGenerated = 1;
        stats.creativeEntries = entries.size();
        return true;
    }

    private static void collectIds(JsonArray values, Set<String> output) {
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            String id = string(value.getAsJsonObject(), "id", null);
            if (id != null && !id.isBlank()) output.add(id);
        }
    }

    private static void ensureBlock(
            Path staging,
            String fallbackNamespace,
            JsonObject block,
            Stats stats
    ) throws IOException {
        ContentId id = ContentId.parse(string(block, "id", null), fallbackNamespace);
        if (id == null) return;

        ensure(
                staging.resolve("assets/" + id.namespace + "/models/block/" + id.path + ".json"),
                blockFallbackModel(),
                true,
                stats
        );
        ensure(
                staging.resolve("assets/" + id.namespace + "/blockstates/" + id.path + ".json"),
                blockState(id),
                false,
                stats
        );
        ensure(
                staging.resolve("assets/" + id.namespace + "/models/item/" + id.path + ".json"),
                parentModel(id.namespace + ":block/" + id.path),
                false,
                stats
        );
        ensure(
                staging.resolve("assets/" + id.namespace + "/items/" + id.path + ".json"),
                itemDefinition(id),
                false,
                stats
        );
    }

    private static void ensureItem(
            Path staging,
            String fallbackNamespace,
            JsonObject item,
            Stats stats
    ) throws IOException {
        ContentId id = ContentId.parse(string(item, "id", null), fallbackNamespace);
        if (id == null) return;

        ensure(
                staging.resolve("assets/" + id.namespace + "/models/item/" + id.path + ".json"),
                parentModel("minecraft:item/paper"),
                true,
                stats
        );
        ensure(
                staging.resolve("assets/" + id.namespace + "/items/" + id.path + ".json"),
                itemDefinition(id),
                false,
                stats
        );
    }

    private static void ensure(
            Path path,
            JsonObject fallback,
            boolean modelFallback,
            Stats stats
    ) throws IOException {
        if (Files.isRegularFile(path)) {
            stats.preservedFiles++;
            return;
        }
        write(path, fallback);
        stats.createdFiles++;
        if (modelFallback) stats.fallbackModels++;
    }

    private static JsonObject evidence(
            ConversionContext context,
            String namespace,
            Stats stats
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("namespace", namespace);
        root.addProperty("clientCompatibilityTarget", "forge-1.7.10-server-authoritative");
        root.addProperty("blockCount", stats.blockCount);
        root.addProperty("independentItemCount", stats.itemCount);
        root.addProperty("expectedRegisteredItemCount", stats.blockCount + stats.itemCount);
        root.addProperty("creativeTabsGenerated", stats.creativeTabsGenerated);
        root.addProperty("creativeEntries", stats.creativeEntries);
        root.addProperty("resourceFilesCreated", stats.createdFiles);
        root.addProperty("preservedSpecializedFiles", stats.preservedFiles);
        root.addProperty("visibleFallbackModels", stats.fallbackModels);
        root.addProperty("completeResourcePathCoverage", true);
        root.addProperty("serverGameplayReimplementationRequired", false);
        root.addProperty("blockStateIdentityBridge", "fml-modiddata-via-carrier");
        root.addProperty("metadataVariantMode", "generated-default-state");

        JsonArray limitations = new JsonArray();
        limitations.add("fallback-models-do-not-prove-metadata-variant-equivalence");
        limitations.add("legacy-metadata-variants-collapse-to-generated-default-block-state");
        limitations.add("server-authoritative-custom-gui-and-entity-presentation-remain-separate-gates");
        root.add("limitations", limitations);
        return root;
    }

    private static JsonObject blockFallbackModel() {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", "minecraft:block/stone");
        root.add("textures", textures);
        return root;
    }

    private static JsonObject blockState(ContentId id) {
        JsonObject model = new JsonObject();
        model.addProperty("model", id.namespace + ":block/" + id.path);
        JsonObject variants = new JsonObject();
        variants.add("", model);
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    private static JsonObject parentModel(String parent) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", parent);
        return root;
    }

    private static JsonObject itemDefinition(ContentId id) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", id.namespace + ":item/" + id.path);
        JsonObject root = new JsonObject();
        root.add("model", model);
        return root;
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    private record ContentId(String namespace, String path) {
        static ContentId parse(String raw, String fallbackNamespace) {
            if (raw == null || raw.isBlank()) return null;
            int separator = raw.indexOf(':');
            String namespace = separator < 0 ? fallbackNamespace : raw.substring(0, separator);
            String path = separator < 0 ? raw : raw.substring(separator + 1);
            if (namespace == null || namespace.isBlank() || path.isBlank()) return null;
            return new ContentId(namespace, path);
        }
    }

    private static final class Stats {
        final int blockCount;
        final int itemCount;
        int creativeTabsGenerated;
        int creativeEntries;
        int createdFiles;
        int preservedFiles;
        int fallbackModels;

        Stats(int blockCount, int itemCount) {
            this.blockCount = blockCount;
            this.itemCount = itemCount;
        }
    }
}
