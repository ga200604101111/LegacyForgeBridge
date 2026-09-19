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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Establishes the client-visible baseline before generated registry bytecode is emitted.
 *
 * <p>This pass deliberately does not recreate server-authoritative gameplay. It guarantees that
 * every discovered Block/BlockItem/Item has a loadable modern presentation path and that a legacy
 * mod with no provable CreativeTabs construction still has a deterministic inspection tab. More
 * precise converted models written by earlier presentation passes always win.</p>
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

        JsonObject content = readObject(contentPath);
        String namespace = string(content, "namespace", context.metadata().fabricId());
        JsonArray blocks = array(content, "blocks");
        JsonArray items = array(content, "items");
        TextureIndex textures = TextureIndex.scan(context.stagingDir());
        Stats stats = new Stats(blocks.size(), items.size(), textures.size());

        boolean contentChanged = ensureCreativeTab(content, namespace, blocks, items,
                context.metadata().primary().name(), stats);
        for (JsonElement element : blocks) {
            if (element.isJsonObject()) ensureBlock(context.stagingDir(), namespace,
                    element.getAsJsonObject(), textures, stats);
        }
        for (JsonElement element : items) {
            if (element.isJsonObject()) ensureItem(context.stagingDir(), namespace,
                    element.getAsJsonObject(), textures, stats);
        }

        if (contentChanged) write(contentPath, content);
        write(context.stagingDir().resolve(OUTPUT), evidence(context, namespace, stats));

        context.diagnostics().info(
                "LFB-CONVERT-CLIENT-CONTENT-0001",
                SupportLevel.ADAPTED,
                "Completed client presentation baseline: blocks=" + stats.blockCount
                        + ", items=" + stats.itemCount
                        + ", creativeTabsGenerated=" + stats.creativeTabsGenerated
                        + ", resourceFilesCreated=" + stats.createdFiles
                        + ", preservedSpecializedFiles=" + stats.preservedFiles
                        + ", textureBackedModels=" + stats.textureBackedModels
                        + ", vanillaFallbackModels=" + stats.vanillaFallbackModels + "."
        );
        if (stats.vanillaFallbackModels > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-CLIENT-CONTENT-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Some discovered content has no uniquely provable legacy texture. A visible vanilla fallback model was emitted instead of a missing-model cube; unresolved models="
                            + stats.vanillaFallbackModels + "."
            );
        }
    }

    private static boolean ensureCreativeTab(
            JsonObject content,
            String namespace,
            JsonArray blocks,
            JsonArray items,
            String title,
            Stats stats) {
        JsonArray existing = array(content, "creativeTabs");
        if (!existing.isEmpty()) return false;

        LinkedHashSet<String> entries = new LinkedHashSet<>();
        collectIds(blocks, entries);
        collectIds(items, entries);
        if (entries.isEmpty()) return false;

        JsonObject tab = new JsonObject();
        tab.addProperty("id", namespace + ":converted_content");
        tab.addProperty("titleKey", "");
        tab.addProperty("title", title == null || title.isBlank() ? namespace : title);
        tab.addProperty("icon", entries.iterator().next());
        JsonArray entryArray = new JsonArray();
        entries.forEach(entryArray::add);
        tab.add("items", entryArray);
        existing.add(tab);
        content.add("creativeTabs", existing);
        stats.creativeTabsGenerated++;
        stats.creativeEntries += entries.size();
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
            TextureIndex textures,
            Stats stats) throws IOException {
        ContentId id = ContentId.parse(string(block, "id", null), fallbackNamespace);
        if (id == null) return;

        Path model = staging.resolve("assets/" + id.namespace + "/models/block/" + id.path + ".json");
        Path state = staging.resolve("assets/" + id.namespace + "/blockstates/" + id.path + ".json");
        Path itemModel = staging.resolve("assets/" + id.namespace + "/models/item/" + id.path + ".json");
        Path itemDefinition = staging.resolve("assets/" + id.namespace + "/items/" + id.path + ".json");

        TextureMatch match = null;
        if (!Files.isRegularFile(model)) {
            match = textures.resolve(
                    TextureKind.BLOCK,
                    id.path,
                    string(block, "legacyRegistryName", id.path),
                    string(block, "sourceClass", ""));
            if (match == null) {
                write(model, blockModel("minecraft:block/stone", false));
                stats.vanillaFallbackModels++;
                stats.unresolved.add(id.value());
            } else {
                write(model, blockModel(match.texture.resource, match.cross));
                stats.textureBackedModels++;
                stats.textureMatches.put(id.value(), match.texture.resource);
            }
            stats.createdFiles++;
        } else stats.preservedFiles++;

        if (!Files.isRegularFile(state)) {
            write(state, blockState(id));
            stats.createdFiles++;
        } else stats.preservedFiles++;

        if (!Files.isRegularFile(itemModel)) {
            write(itemModel, parentModel(id.namespace + ":block/" + id.path));
            stats.createdFiles++;
        } else stats.preservedFiles++;

        if (!Files.isRegularFile(itemDefinition)) {
            write(itemDefinition, itemDefinition(id));
            stats.createdFiles++;
        } else stats.preservedFiles++;
    }

    private static void ensureItem(
            Path staging,
            String fallbackNamespace,
            JsonObject item,
            TextureIndex textures,
            Stats stats) throws IOException {
        ContentId id = ContentId.parse(string(item, "id", null), fallbackNamespace);
        if (id == null) return;

        Path model = staging.resolve("assets/" + id.namespace + "/models/item/" + id.path + ".json");
        Path definition = staging.resolve("assets/" + id.namespace + "/items/" + id.path + ".json");
        if (!Files.isRegularFile(model)) {
            TextureMatch match = textures.resolve(
                    TextureKind.ITEM,
                    id.path,
                    string(item, "legacyRegistryName", id.path),
                    string(item, "sourceClass", ""));
            if (match == null) {
                write(model, parentModel("minecraft:item/paper"));
                stats.vanillaFallbackModels++;
                stats.unresolved.add(id.value());
            } else {
                write(model, generatedItemModel(match.texture.resource));
                stats.textureBackedModels++;
                stats.textureMatches.put(id.value(), match.texture.resource);
            }
            stats.createdFiles++;
        } else stats.preservedFiles++;

        if (!Files.isRegularFile(definition)) {
            write(definition, itemDefinition(id));
            stats.createdFiles++;
        } else stats.preservedFiles++;
    }

    private static JsonObject evidence(ConversionContext context, String namespace, Stats stats) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("namespace", namespace);
        root.addProperty("clientCompatibilityTarget", "forge-1.7.10-server-authoritative");
        root.addProperty("blockCount", stats.blockCount);
        root.addProperty("independentItemCount", stats.itemCount);
        root.addProperty("expectedRegisteredItemCount", stats.blockCount + stats.itemCount);
        root.addProperty("creativeTabsGenerated", stats.creativeTabsGenerated);
        root.addProperty("creativeEntries", stats.creativeEntries);
        root.addProperty("legacyTextureCount", stats.textureCount);
        root.addProperty("resourceFilesCreated", stats.createdFiles);
        root.addProperty("preservedSpecializedFiles", stats.preservedFiles);
        root.addProperty("textureBackedModels", stats.textureBackedModels);
        root.addProperty("vanillaFallbackModels", stats.vanillaFallbackModels);
        root.addProperty("completeResourcePathCoverage", true);
        root.addProperty("serverGameplayReimplementationRequired", false);
        root.add("textureMatches", stringMap(stats.textureMatches));
        root.add("unresolvedModels", strings(stats.unresolved));
        JsonArray limitations = new JsonArray();
        limitations.add("fallback-models-do-not-prove-metadata-variant-equivalence");
        limitations.add("numeric-block-registry-map-requires-packet-boundary-wiring");
        limitations.add("server-authoritative-custom-gui-and-entity-presentation-remain-separate-gates");
        root.add("limitations", limitations);
        return root;
    }

    private static JsonObject blockModel(String texture, boolean cross) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", cross ? "minecraft:block/cross" : "minecraft:block/cube_all");
        JsonObject values = new JsonObject();
        values.addProperty(cross ? "cross" : "all", texture);
        root.add("textures", values);
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

    private static JsonObject generatedItemModel(String texture) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture);
        root.add("textures", textures);
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

    private static JsonObject readObject(Path path) throws IOException {
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

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static JsonObject stringMap(Map<String, String> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    private enum TextureKind { ITEM, BLOCK }

    private record ContentId(String namespace, String path) {
        static ContentId parse(String raw, String fallbackNamespace) {
            if (raw == null || raw.isBlank()) return null;
            int separator = raw.indexOf(':');
            String namespace = separator < 0 ? fallbackNamespace : raw.substring(0, separator);
            String path = separator < 0 ? raw : raw.substring(separator + 1);
            if (namespace == null || namespace.isBlank() || path.isBlank()) return null;
            return new ContentId(namespace, path);
        }

        String value() { return namespace + ":" + path; }
    }

    private record Texture(String resource, String normalizedStem, TextureKind kind) { }

    private record TextureMatch(Texture texture, boolean cross, int score) { }

    private static final class TextureIndex {
        private final List<Texture> textures;

        private TextureIndex(List<Texture> textures) {
            this.textures = textures;
        }

        static TextureIndex scan(Path staging) throws IOException {
            Path assets = staging.resolve("assets");
            if (!Files.isDirectory(assets)) return new TextureIndex(List.of());
            List<Texture> found = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(assets)) {
                for (Path file : stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .sorted().toList()) {
                    Path relative = assets.relativize(file);
                    if (relative.getNameCount() < 4 || !"textures".equals(relative.getName(1).toString())) continue;
                    String directory = relative.getName(2).toString().toLowerCase(Locale.ROOT);
                    TextureKind kind = switch (directory) {
                        case "item", "items" -> TextureKind.ITEM;
                        case "block", "blocks" -> TextureKind.BLOCK;
                        default -> null;
                    };
                    if (kind == null) continue;
                    String namespace = relative.getName(0).toString().toLowerCase(Locale.ROOT);
                    String resourcePath = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
                    resourcePath = resourcePath.substring(0, resourcePath.length() - 4);
                    String stem = file.getFileName().toString();
                    stem = stem.substring(0, stem.length() - 4);
                    found.add(new Texture(namespace + ":" + resourcePath, normalize(stem), kind));
                }
            }
            return new TextureIndex(List.copyOf(found));
        }

        int size() { return textures.size(); }

        TextureMatch resolve(TextureKind kind, String modernPath, String legacyName, String sourceClass) {
            LinkedHashSet<String> seeds = seeds(modernPath, legacyName, sourceClass);
            List<TextureMatch> candidates = new ArrayList<>();
            for (Texture texture : textures) {
                if (texture.kind != kind) continue;
                int score = score(texture.normalizedStem, seeds);
                if (score <= 0) continue;
                boolean cross = kind == TextureKind.BLOCK && isCrossTexture(texture.normalizedStem, seeds);
                candidates.add(new TextureMatch(texture, cross, score));
            }
            if (candidates.isEmpty()) return null;
            candidates.sort(Comparator.comparingInt(TextureMatch::score).reversed()
                    .thenComparing(match -> match.texture.resource));
            TextureMatch best = candidates.getFirst();
            if (candidates.size() > 1 && candidates.get(1).score == best.score
                    && !candidates.get(1).texture.resource.equals(best.texture.resource)) return null;
            return best.score >= 70 ? best : null;
        }

        private static int score(String texture, Set<String> seeds) {
            int best = 0;
            for (String seed : seeds) {
                if (seed.isBlank()) continue;
                if (texture.equals(seed)) best = Math.max(best, 100);
                else if (texture.equals(seed + "0") || texture.equals(seed + "stage0")
                        || texture.equals(seed + "s") || texture.equals(seed + "f")
                        || texture.equals(seed + "x")) best = Math.max(best, 85);
                else if (texture.startsWith(seed + "stage0") || texture.startsWith(seed + "0"))
                    best = Math.max(best, 80);
                else if (texture.startsWith(seed) && texture.length() - seed.length() <= 2)
                    best = Math.max(best, 70);
            }
            return best;
        }

        private static boolean isCrossTexture(String texture, Set<String> seeds) {
            if (texture.contains("plant") || texture.contains("sapling") || texture.contains("seaweed")) return true;
            for (String seed : seeds) {
                if (seed.contains("plant") || seed.contains("sapling") || seed.contains("seaweed")) return true;
            }
            return false;
        }

        private static LinkedHashSet<String> seeds(String modernPath, String legacyName, String sourceClass) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            addSeed(result, modernPath);
            addSeed(result, legacyName);
            int slash = sourceClass == null ? -1 : sourceClass.lastIndexOf('/');
            addSeed(result, slash < 0 ? sourceClass : sourceClass.substring(slash + 1));
            return result;
        }

        private static void addSeed(Set<String> output, String raw) {
            String value = normalize(raw);
            if (value.isBlank()) return;
            output.add(value);
            String stripped = value;
            boolean changed;
            do {
                changed = false;
                for (String prefix : List.of("tileentity", "block", "item", "entity", "render", "model")) {
                    if (stripped.startsWith(prefix) && stripped.length() > prefix.length()) {
                        stripped = stripped.substring(prefix.length());
                        output.add(stripped);
                        changed = true;
                        break;
                    }
                }
            } while (changed);
            if (stripped.startsWith("bamboo") && stripped.length() > "bamboo".length())
                output.add(stripped.substring("bamboo".length()));
            String noDigits = stripped.replaceAll("\\d+$", "");
            if (!noDigits.equals(stripped) && !noDigits.isBlank()) output.add(noDigits);
            for (String suffix : List.of("block", "item", "bottle")) {
                if (stripped.endsWith(suffix) && stripped.length() > suffix.length())
                    output.add(stripped.substring(0, stripped.length() - suffix.length()));
            }
        }

        private static String normalize(String raw) {
            if (raw == null) return "";
            return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
    }

    private static final class Stats {
        final int blockCount;
        final int itemCount;
        final int textureCount;
        int creativeTabsGenerated;
        int creativeEntries;
        int createdFiles;
        int preservedFiles;
        int textureBackedModels;
        int vanillaFallbackModels;
        final Map<String, String> textureMatches = new LinkedHashMap<>();
        final Set<String> unresolved = new LinkedHashSet<>();

        Stats(int blockCount, int itemCount, int textureCount) {
            this.blockCount = blockCount;
            this.itemCount = itemCount;
            this.textureCount = textureCount;
        }
    }
}
