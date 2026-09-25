package dev.yinghuang.legacyforgebridge.convert.pass;

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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Emits native modern Minecraft item tags from neutral converted item archetypes.
 *
 * <p>This is deliberately mod-agnostic. A legacy conversion pass only has to prove that an item is
 * a sword/axe/pickaxe/shovel/hoe; this pass translates that semantic fact into the same vanilla
 * tags ordinary Fabric mods use. Cross-version clients such as ViaFabricPlus can then recognize a
 * converted custom sword through {@code minecraft:swords} instead of requiring an LFB-specific
 * blocking implementation.</p>
 */
public final class NativeItemTagsPass implements ConversionPass {
    private static final String CONTENT_PATH = "legacyforgebridge/converted-content.json";
    private static final Map<String, String> KIND_TO_TAG = Map.of(
            "sword", "swords",
            "axe", "axes",
            "pickaxe", "pickaxes",
            "shovel", "shovels",
            "hoe", "hoes"
    );

    @Override
    public String id() {
        return "native-modern-item-tags";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path contentPath = context.stagingDir().resolve(CONTENT_PATH);
        if (!Files.isRegularFile(contentPath)) {
            return;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(contentPath, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonArray items = root.getAsJsonArray("items");
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<String, LinkedHashSet<String>> valuesByTag = new LinkedHashMap<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String id = primitiveString(item, "id");
            String kind = primitiveString(item, "kind");
            if (id == null || kind == null) {
                continue;
            }
            String tag = KIND_TO_TAG.get(kind.toLowerCase(Locale.ROOT));
            if (tag == null) {
                continue;
            }
            valuesByTag.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()).add(id);
        }

        int emitted = 0;
        for (Map.Entry<String, LinkedHashSet<String>> entry : valuesByTag.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            writeMergedTag(context.stagingDir(), entry.getKey(), entry.getValue());
            emitted += entry.getValue().size();
        }

        if (emitted > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-NATIVE-TAGS-0001",
                    SupportLevel.ADAPTED,
                    "Emitted native Minecraft tool tags for " + emitted
                            + " converted items across " + valuesByTag.size() + " tag groups."
            );
        }
    }

    private static void writeMergedTag(Path stagingDir, String tagName, Set<String> additions) throws IOException {
        Path target = stagingDir.resolve("data/minecraft/tags/item/" + tagName + ".json");
        JsonObject output = new JsonObject();
        JsonArray values = new JsonArray();
        LinkedHashSet<String> knownStrings = new LinkedHashSet<>();

        if (Files.isRegularFile(target)) {
            try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    JsonObject existing = parsed.getAsJsonObject();
                    if (existing.has("replace") && existing.get("replace").isJsonPrimitive()) {
                        output.addProperty("replace", existing.get("replace").getAsBoolean());
                    }
                    JsonArray existingValues = existing.getAsJsonArray("values");
                    if (existingValues != null) {
                        for (JsonElement value : existingValues) {
                            values.add(value.deepCopy());
                            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                                knownStrings.add(value.getAsString());
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // A malformed pre-existing modern tag should not make the whole legacy conversion
                // nondeterministic. Replace it with the proven generated values below.
                values = new JsonArray();
                knownStrings.clear();
                output = new JsonObject();
            }
        }

        if (!output.has("replace")) {
            output.addProperty("replace", false);
        }
        for (String addition : additions) {
            if (knownStrings.add(addition)) {
                values.add(addition);
            }
        }
        output.add("values", values);

        Files.createDirectories(target.getParent());
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(output, writer);
            writer.write('\n');
        }
    }

    private static String primitiveString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }
}
