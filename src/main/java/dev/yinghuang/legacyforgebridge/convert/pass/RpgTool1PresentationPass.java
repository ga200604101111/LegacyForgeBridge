package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCreativeTabAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import dev.yinghuang.legacyforgebridge.convert.profile.RpgTool1Profile;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Supplies corpus-backed presentation semantics using the common converted-content schema.
 *
 * <p>Creative group membership is not hardcoded to RPGTool categories. The verified source JAR is
 * inspected through {@link LegacyCreativeTabAnalyzer}; every recovered legacy custom tab becomes a
 * distinct modern group and only the items that actually called {@code setCreativeTab(...)} for
 * that tab are assigned to it. The wearable model paths remain corpus-known data until generic
 * renderer-bytecode extraction can recover them too.</p>
 */
public final class RpgTool1PresentationPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NAMESPACE = "rpgtool1";

    @Override
    public String id() {
        return "rpgtool1-presentation-metadata";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        if (!RpgTool1Profile.CORPUS_SHA256.equalsIgnoreCase(context.sourceHash())) {
            return;
        }

        Path manifestPath = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(manifestPath)) {
            return;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonArray items = root.getAsJsonArray("items");
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<String, JsonObject> itemByPath = new LinkedHashMap<>();
        int equipmentDefinitions = 0;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String itemId = string(item, "id");
            if (itemId == null) {
                continue;
            }
            itemByPath.put(itemPath(itemId), item);

            String kind = string(item, "kind");
            if ("wing".equals(kind)) {
                item.add("equipmentRender", wingRenderer(itemId));
                equipmentDefinitions++;
            } else if ("circle".equals(kind)) {
                item.add("equipmentRender", circleRenderer(itemId));
                equipmentDefinitions++;
            }
        }

        int creativeTabCount = emitExtractedCreativeTabs(context, root, itemByPath);
        Files.writeString(manifestPath, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (creativeTabCount == 0) {
            context.diagnostics().warning(
                    "LFB-RPGTOOL-PRESENTATION-0002",
                    SupportLevel.MANUAL_REQUIRED,
                    "No source-defined custom CreativeTabs could be recovered from the legacy bytecode. Items were left in their existing fallback groups instead of being incorrectly forced into one synthetic mod tab."
            );
        }

        context.diagnostics().info(
                "LFB-RPGTOOL-PRESENTATION-0001",
                SupportLevel.ADAPTED,
                "Recovered " + creativeTabCount + " source-defined creative group(s) and emitted "
                        + equipmentDefinitions + " wearable OBJ definitions through the generic presentation schema."
        );
    }

    private static int emitExtractedCreativeTabs(
            ConversionContext context,
            JsonObject root,
            Map<String, JsonObject> itemByPath
    ) throws IOException {
        LegacyCreativeTabAnalyzer.Analysis analysis = new LegacyCreativeTabAnalyzer().analyze(context.sourceJar());
        JsonArray tabs = new JsonArray();
        Set<String> usedTabIds = new LinkedHashSet<>();

        for (LegacyCreativeTabAnalyzer.Tab sourceTab : analysis.tabs()) {
            JsonArray tabItems = new JsonArray();
            JsonObject iconItem = null;
            String tabId = uniqueTabId(sourceTab, usedTabIds);

            for (String legacyItemName : sourceTab.itemNames()) {
                JsonObject item = itemByPath.get(normalizeItemName(legacyItemName));
                if (item == null) {
                    continue;
                }
                String itemId = string(item, "id");
                item.addProperty("creativeTab", tabId);
                tabItems.add(itemId);
                if (sourceTab.iconItemName() != null
                        && normalizeItemName(sourceTab.iconItemName()).equals(itemPath(itemId))) {
                    iconItem = item;
                }
            }

            if (tabItems.isEmpty()) {
                continue;
            }

            JsonObject tab = new JsonObject();
            tab.addProperty("id", tabId);
            if (sourceTab.label() != null && !sourceTab.label().isBlank()) {
                // LegacyLanguagePass already converted itemGroup.<label> into a collision-free
                // source-locale alias. Keep the original tab title instead of showing raw IDs.
                tab.addProperty(
                        "titleKey",
                        LegacyLanguagePass.translationAlias(context, "itemGroup." + sourceTab.label())
                );
            } else {
                tab.addProperty("title", sourceTab.fieldName());
            }
            tab.addProperty(
                    "icon",
                    iconItem != null ? string(iconItem, "id") : tabItems.get(0).getAsString()
            );
            tab.add("items", tabItems);
            tabs.add(tab);
        }

        if (!tabs.isEmpty()) {
            root.add("creativeTabs", tabs);
        } else {
            root.remove("creativeTabs");
        }
        return tabs.size();
    }

    private static String uniqueTabId(LegacyCreativeTabAnalyzer.Tab sourceTab, Set<String> used) {
        String path = sanitizePath(sourceTab.label());
        if (path.isBlank()) {
            path = sanitizePath(sourceTab.fieldName());
        }
        if (path.isBlank()) {
            path = "legacy_tab";
        }
        String candidate = NAMESPACE + ":" + path;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = NAMESPACE + ":" + path + "_" + suffix++;
        }
        return candidate;
    }

    private static String sanitizePath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/._-]", "_")
                .replaceAll("_+", "_");
        while (normalized.startsWith("_") || normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_") || normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeItemName(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        if (result.startsWith("item.")) {
            result = result.substring("item.".length());
        }
        return itemPath(result);
    }

    private static JsonObject wingRenderer(String itemId) {
        String path = itemPath(itemId);
        JsonObject render = renderDefinition("body", 1.75F);
        JsonArray parts = new JsonArray();
        parts.add(part(
                NAMESPACE + ":textures/wings/left_" + path + ".obj",
                NAMESPACE + ":textures/wings/left_" + path + ".png",
                NAMESPACE + ":textures/wings/" + path + ".png",
                NAMESPACE + ":textures/item/" + path + ".png"
        ));
        parts.add(part(
                NAMESPACE + ":textures/wings/right_" + path + ".obj",
                NAMESPACE + ":textures/wings/right_" + path + ".png",
                NAMESPACE + ":textures/wings/" + path + ".png",
                NAMESPACE + ":textures/item/" + path + ".png"
        ));
        render.add("parts", parts);
        return render;
    }

    private static JsonObject circleRenderer(String itemId) {
        String path = itemPath(itemId);
        int separator = path.lastIndexOf('_');
        String family = separator > 0 ? path.substring(0, separator) : path;
        JsonObject render = renderDefinition("body", 1.25F);
        JsonArray parts = new JsonArray();
        parts.add(part(
                NAMESPACE + ":textures/circle/" + family + ".obj",
                NAMESPACE + ":textures/circle/" + family + ".png",
                NAMESPACE + ":textures/circle/" + path + ".png",
                NAMESPACE + ":textures/item/" + path + ".png"
        ));
        render.add("parts", parts);
        return render;
    }

    private static JsonObject renderDefinition(String anchor, float fit) {
        JsonObject render = new JsonObject();
        render.addProperty("anchor", anchor);
        render.addProperty("autoCenter", true);
        render.addProperty("fit", fit);
        return render;
    }

    private static JsonObject part(String model, String... textureCandidates) {
        JsonObject part = new JsonObject();
        part.addProperty("model", model);
        JsonArray textures = new JsonArray();
        for (String texture : textureCandidates) {
            textures.add(texture);
        }
        part.add("textures", textures);
        return part;
    }

    private static String itemPath(String itemId) {
        int separator = itemId.indexOf(':');
        return separator >= 0 ? itemId.substring(separator + 1) : itemId;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
