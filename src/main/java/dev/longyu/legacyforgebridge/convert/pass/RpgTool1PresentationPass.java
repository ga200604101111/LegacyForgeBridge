package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import dev.longyu.legacyforgebridge.convert.profile.RpgTool1Profile;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Supplies corpus-known presentation semantics using the common converted-content schema.
 *
 * <p>The runtime consuming {@code creativeTabs} and {@code equipmentRender} is mod-agnostic. This
 * pass only fills information that the generic bytecode extractor cannot yet recover from the
 * verified RPGTool1 corpus.</p>
 */
public final class RpgTool1PresentationPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NAMESPACE = "rpgtool1";
    private static final String CREATIVE_TAB_ID = NAMESPACE + ":main";

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

        JsonArray tabItems = new JsonArray();
        String icon = null;
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

            item.addProperty("creativeTab", CREATIVE_TAB_ID);
            tabItems.add(itemId);
            if (icon == null && "sword".equals(string(item, "kind"))) {
                icon = itemId;
            }

            String kind = string(item, "kind");
            if ("wing".equals(kind)) {
                item.add("equipmentRender", wingRenderer(itemId));
                equipmentDefinitions++;
            } else if ("circle".equals(kind)) {
                item.add("equipmentRender", circleRenderer(itemId));
                equipmentDefinitions++;
            }
        }

        if (icon == null && !tabItems.isEmpty()) {
            icon = tabItems.get(0).getAsString();
        }

        JsonObject tab = new JsonObject();
        tab.addProperty("id", CREATIVE_TAB_ID);
        tab.addProperty("title", context.metadata().primary().name());
        tab.addProperty("icon", icon);
        tab.add("items", tabItems);
        JsonArray tabs = new JsonArray();
        tabs.add(tab);
        root.add("creativeTabs", tabs);

        Files.writeString(manifestPath, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info(
                "LFB-RPGTOOL-PRESENTATION-0001",
                SupportLevel.ADAPTED,
                "Emitted the source mod's dedicated creative group plus " + equipmentDefinitions
                        + " wearable OBJ definitions into the generic converted-content presentation schema."
        );
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
