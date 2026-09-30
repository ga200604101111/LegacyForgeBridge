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
 * Completes the generic client-visible resource surface for discovered legacy content.
 *
 * <p>Existing semantic models always win. Missing models are matched only to a unique,
 * high-confidence source texture. Ambiguous content receives an explicit unresolved marker rather
 * than being silently presented as stone or paper. Every generated legacy block exposes all sixteen
 * raw metadata states, so server state is not discarded merely because its meaning is not yet known.</p>
 */
public final class LegacyClientContentBaselinePass implements ConversionPass {
    public static final String CONTENT = "legacyforgebridge/converted-content.json";
    public static final String OUTPUT = "legacyforgebridge/client-content-baseline.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() { return "legacy-client-content-baseline"; }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path contentPath = context.stagingDir().resolve(CONTENT);
        if (!Files.isRegularFile(contentPath)) return;

        JsonObject content = read(contentPath);
        String namespace = string(content, "namespace", context.metadata().fabricId());
        JsonArray blocks = array(content, "blocks");
        JsonArray items = array(content, "items");
        TextureIndex textures = TextureIndex.scan(context.stagingDir());
        Stats stats = new Stats(blocks.size(), items.size(), textures.size());

        if (ensureCreativeTab(content, namespace, blocks, items,
                context.metadata().primary().name(), stats)) {
            write(contentPath, content);
        }

        for (JsonElement value : blocks) {
            if (value.isJsonObject()) ensureBlock(
                    context.stagingDir(), namespace, value.getAsJsonObject(), textures, stats);
        }
        for (JsonElement value : items) {
            if (value.isJsonObject()) ensureItem(
                    context.stagingDir(), namespace, value.getAsJsonObject(), textures, stats);
        }

        write(context.stagingDir().resolve(OUTPUT), evidence(context, namespace, stats));
        context.diagnostics().info(
                "LFB-CONVERT-CLIENT-CONTENT-0001",
                SupportLevel.ADAPTED,
                "Completed generic client presentation baseline: blocks=" + stats.blockCount
                        + ", items=" + stats.itemCount
                        + ", creativeTabsGenerated=" + stats.creativeTabsGenerated
                        + ", textureBackedModels=" + stats.textureBackedModels
                        + ", unresolvedModels=" + stats.unresolved.size()
                        + ", metadataBlockstatesCreatedOrExpanded=" + stats.metadataBlockstates
                        + ", resourceFilesCreated=" + stats.createdFiles
                        + ", preservedSpecializedFiles=" + stats.preservedFiles + ".");
        if (!stats.unresolved.isEmpty()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-CLIENT-CONTENT-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Some content had no unique source-proven texture/model and uses the explicit unresolved marker; unresolved="
                            + stats.unresolved.size() + ". No stone/paper identity was guessed.");
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
            JsonObject definition,
            TextureIndex textures,
            Stats stats
    ) throws IOException {
        ContentId id = ContentId.parse(string(definition, "id", null), fallbackNamespace);
        if (id == null) return;

        Path model = staging.resolve("assets/" + id.namespace + "/models/block/" + id.path + ".json");
        Path blockstate = staging.resolve("assets/" + id.namespace + "/blockstates/" + id.path + ".json");
        Path itemModel = staging.resolve("assets/" + id.namespace + "/models/item/" + id.path + ".json");
        Path itemDefinition = staging.resolve("assets/" + id.namespace + "/items/" + id.path + ".json");

        if (!Files.isRegularFile(model)) {
            TextureMatch match = textures.resolve(
                    TextureKind.BLOCK,
                    id.path,
                    string(definition, "legacyRegistryName", id.path),
                    string(definition, "sourceClass", ""));
            if (match == null) {
                write(model, parentModel("minecraft:block/magenta_glazed_terracotta"));
                stats.unresolved.add(id.value());
            } else {
                write(model, blockTextureModel(match.texture.resource, match.cross));
                stats.textureBackedModels++;
                stats.textureMatches.put(id.value(), match.texture.resource);
            }
            LegacyPresentationOwnership.record(staging, model);
            stats.createdFiles++;
        } else {
            stats.preservedFiles++;
        }

        if (!Files.isRegularFile(blockstate)) {
            write(blockstate, metadataBlockState(id));
            stats.createdFiles++;
            stats.metadataBlockstates++;
        } else if (expandSingleDefaultVariant(blockstate)) {
            stats.metadataBlockstates++;
        } else {
            stats.preservedFiles++;
        }

        ensure(itemModel, parentModel(id.namespace + ":block/" + id.path), stats);
        ensure(itemDefinition, itemDefinition(id), stats);
    }

    private static void ensureItem(
            Path staging,
            String fallbackNamespace,
            JsonObject definition,
            TextureIndex textures,
            Stats stats
    ) throws IOException {
        ContentId id = ContentId.parse(string(definition, "id", null), fallbackNamespace);
        if (id == null) return;

        Path model = staging.resolve("assets/" + id.namespace + "/models/item/" + id.path + ".json");
        Path itemDefinition = staging.resolve("assets/" + id.namespace + "/items/" + id.path + ".json");
        if (!Files.isRegularFile(model)) {
            TextureMatch match = textures.resolve(
                    TextureKind.ITEM,
                    id.path,
                    string(definition, "legacyRegistryName", id.path),
                    string(definition, "sourceClass", ""));
            if (match == null) {
                write(model, parentModel("minecraft:item/barrier"));
                stats.unresolved.add(id.value());
            } else {
                write(model, generatedItemModel(match.texture.resource));
                stats.textureBackedModels++;
                stats.textureMatches.put(id.value(), match.texture.resource);
            }
            LegacyPresentationOwnership.record(staging, model);
            stats.createdFiles++;
        } else {
            stats.preservedFiles++;
        }
        ensure(itemDefinition, itemDefinition(id), stats);
    }

    /** Expands an old empty/default variant to every raw metadata state without altering its model. */
    private static boolean expandSingleDefaultVariant(Path blockstate) throws IOException {
        JsonObject root;
        try {
            root = read(blockstate);
        } catch (RuntimeException invalidJson) {
            return false;
        }
        JsonElement variantsElement = root.get("variants");
        if (variantsElement == null || !variantsElement.isJsonObject()) return false;
        JsonObject variants = variantsElement.getAsJsonObject();
        JsonElement defaultVariant = variants.get("");
        if (defaultVariant == null) return false;
        for (String key : variants.keySet()) {
            if (key.startsWith("legacy_meta=")) return false;
        }
        if (variants.size() != 1) return false;

        JsonObject expanded = new JsonObject();
        for (int metadata = 0; metadata < 16; metadata++) {
            expanded.add("legacy_meta=" + metadata, defaultVariant.deepCopy());
        }
        root.add("variants", expanded);
        write(blockstate, root);
        return true;
    }

    private static void ensure(Path path, JsonObject fallback, Stats stats) throws IOException {
        if (Files.isRegularFile(path)) {
            stats.preservedFiles++;
            return;
        }
        write(path, fallback);
        stats.createdFiles++;
    }

    private static JsonObject evidence(ConversionContext context, String namespace, Stats stats) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 3);
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
        root.addProperty("unresolvedModelCount", stats.unresolved.size());
        root.addProperty("metadataBlockstatesCreatedOrExpanded", stats.metadataBlockstates);
        root.addProperty("completeResourcePathCoverage", true);
        root.addProperty("presentationIdentityComplete", stats.unresolved.isEmpty());
        root.addProperty("serverGameplayReimplementationRequired", false);
        root.addProperty("blockStateIdentityBridge", "fml-modiddata-via-carrier");
        root.addProperty("metadataVariantMode", "preserved-opaque-0-through-15");
        root.add("textureMatches", stringMap(stats.textureMatches));
        root.add("unresolvedModels", strings(stats.unresolved));

        JsonArray limitations = new JsonArray();
        limitations.add("a-shared-model-for-sixteen-metadata-values-does-not-prove-visual-equivalence");
        limitations.add("ambiguous-texture-identities-use-an-explicit-unresolved-marker");
        limitations.add("server-authoritative-custom-gui-and-entity-presentation-remain-separate-gates");
        root.add("limitations", limitations);
        return root;
    }

    private static JsonObject blockTextureModel(String texture, boolean cross) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", cross ? "minecraft:block/cross" : "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty(cross ? "cross" : "all", texture);
        root.add("textures", textures);
        return root;
    }

    private static JsonObject metadataBlockState(ContentId id) {
        JsonObject variants = new JsonObject();
        for (int metadata = 0; metadata < 16; metadata++) {
            JsonObject model = new JsonObject();
            model.addProperty("model", id.namespace + ":block/" + id.path);
            variants.add("legacy_meta=" + metadata, model);
        }
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

    private enum TextureKind { ITEM, BLOCK }
    private record Texture(String resource, String normalizedStem, TextureKind kind) { }
    private record TextureMatch(Texture texture, boolean cross, int score) { }

    private static final class TextureIndex {
        private final List<Texture> textures;

        private TextureIndex(List<Texture> textures) { this.textures = textures; }

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
            if (best.score < 80) return null;
            if (candidates.size() > 1 && candidates.get(1).score == best.score
                    && !candidates.get(1).texture.resource.equals(best.texture.resource)) return null;
            return best;
        }

        private static int score(String texture, Set<String> seeds) {
            int best = 0;
            for (String seed : seeds) {
                if (seed.isBlank()) continue;
                if (texture.equals(seed)) best = Math.max(best, 100);
                else if (texture.length() >= 8 && seed.length() > texture.length()
                        && seed.length() - texture.length() <= 3 && seed.endsWith(texture))
                    // Some legacy mods prefix every registry identity with a short mod acronym
                    // while their source texture names omit it. Accept only one short leading
                    // difference and still require the global best match to be unique.
                    best = Math.max(best, 92);
                else if (texture.equals(seed + "0") || texture.equals(seed + "stage0"))
                    best = Math.max(best, 90);
                else if (texture.startsWith(seed + "stage0") || texture.startsWith(seed + "0"))
                    best = Math.max(best, 85);
                else if (texture.startsWith(seed) && texture.length() - seed.length() <= 2)
                    best = Math.max(best, 80);
            }
            return best;
        }

        private static boolean isCrossTexture(String texture, Set<String> seeds) {
            if (isPlantWord(texture)) return true;
            for (String seed : seeds) if (isPlantWord(seed)) return true;
            return false;
        }

        private static boolean isPlantWord(String value) {
            return value.contains("plant") || value.contains("sapling") || value.contains("crop")
                    || value.contains("flower") || value.contains("grass") || value.contains("seaweed")
                    || value.contains("reed");
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
        int metadataBlockstates;
        final Map<String, String> textureMatches = new LinkedHashMap<>();
        final Set<String> unresolved = new LinkedHashSet<>();

        Stats(int blockCount, int itemCount, int textureCount) {
            this.blockCount = blockCount;
            this.itemCount = itemCount;
            this.textureCount = textureCount;
        }
    }
}
