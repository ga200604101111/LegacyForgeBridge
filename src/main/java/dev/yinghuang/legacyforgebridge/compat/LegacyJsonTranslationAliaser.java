package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Map;

/**
 * Rewrites selected legacy translation keys to LFB aliases before ViaLegacy can replace them with
 * hard-coded English fallback text.
 *
 * <p>The input/output boundary is a JSON string, so this class deliberately does not depend on
 * ViaVersion's relocated Gson classes. Only the {@code translate} field is changed; formatting,
 * arguments, style, click/hover events and non-preserved content keys are left intact.</p>
 */
public final class LegacyJsonTranslationAliaser {
    private static final Gson GSON = new Gson();

    private LegacyJsonTranslationAliaser() {
    }

    /**
     * Builds a JSON translatable component using LFB's collision-free alias for the supplied old
     * key. This is used when ViaLegacy synthesizes a presentation packet that the original 1.7.10
     * client would have localized from a known key.
     */
    public static String translationComponentForPreservedKey(String key) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", LegacyTranslationBridge.aliasForPreservedKey(key));
        return GSON.toJson(component);
    }

    public static String aliasPreservedTranslations(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            JsonElement root = JsonParser.parseString(json);
            if (!rewrite(root)) {
                return json;
            }
            return GSON.toJson(root);
        } catch (RuntimeException ignored) {
            // ViaLegacy is the authoritative parser for the packet. If LFB cannot safely parse a
            // component, leave it untouched rather than breaking the connection.
            return json;
        }
    }

    private static boolean rewrite(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }

        if (element.isJsonArray()) {
            boolean changed = false;
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                changed |= rewrite(child);
            }
            return changed;
        }

        if (!element.isJsonObject()) {
            return false;
        }

        JsonObject object = element.getAsJsonObject();
        boolean changed = false;

        JsonElement translate = object.get("translate");
        if (translate != null && translate.isJsonPrimitive() && translate.getAsJsonPrimitive().isString()) {
            String key = translate.getAsString();
            String alias = LegacyTranslationBridge.aliasForPreservedKey(key);
            if (!alias.equals(key)) {
                object.addProperty("translate", alias);
                changed = true;
            }
        }

        // Snapshot entries so replacing the "translate" value above can never interfere with the
        // map iterator. Nested "with"/"extra"/hover components are traversed recursively.
        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
            if (!"translate".equals(entry.getKey())) {
                changed |= rewrite(entry.getValue());
            }
        }

        return changed;
    }
}
