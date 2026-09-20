package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes a non-rendering block model that still owns a valid destroy/hit particle sprite. */
final class LegacySpecialBlockModelWriter {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> CANDIDATES = List.of("particle", "all", "side", "north", "up", "down", "layer0", "cross", "crop");
    private LegacySpecialBlockModelWriter() { }

    static String write(Path staging, String idValue, String fallbackParticle) throws IOException {
        int separator = idValue.indexOf(':');
        if (separator <= 0 || separator == idValue.length() - 1) throw new IllegalArgumentException("Invalid converted block id " + idValue);
        String namespace = idValue.substring(0, separator), path = idValue.substring(separator + 1);
        Path modelPath = staging.resolve("assets/" + namespace + "/models/block/" + path + ".json");
        String particle = particle(modelPath, fallbackParticle);
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", particle);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        write(modelPath, model);
        LegacyPresentationOwnership.revoke(staging, modelPath);

        JsonObject variants = new JsonObject();
        for (int meta = 0; meta < 16; meta++) {
            JsonObject state = new JsonObject();
            state.addProperty("model", namespace + ":block/" + path);
            variants.add("legacy_meta=" + meta, state);
        }
        JsonObject blockState = new JsonObject();
        blockState.add("variants", variants);
        write(staging.resolve("assets/" + namespace + "/blockstates/" + path + ".json"), blockState);
        return particle;
    }

    private static String particle(Path modelPath, String fallback) throws IOException {
        if (Files.isRegularFile(modelPath)) {
            try {
                JsonObject model = JsonParser.parseString(Files.readString(modelPath, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject textures = model.has("textures") && model.get("textures").isJsonObject()
                        ? model.getAsJsonObject("textures") : null;
                if (textures != null) {
                    for (String candidate : CANDIDATES) {
                        if (!textures.has(candidate) || !textures.get(candidate).isJsonPrimitive()) continue;
                        String value = resolve(textures, textures.get(candidate).getAsString());
                        if (valid(value)) return value;
                    }
                }
            } catch (RuntimeException ignored) { /* fall through to a source-family fallback */ }
        }
        if (!valid(fallback)) throw new IllegalArgumentException("Invalid particle fallback " + fallback);
        return fallback;
    }

    private static String resolve(JsonObject textures, String value) {
        String current = value;
        for (int depth = 0; depth < 16 && current != null && current.startsWith("#"); depth++) {
            String key = current.substring(1);
            current = textures.has(key) && textures.get(key).isJsonPrimitive() ? textures.get(key).getAsString() : null;
        }
        return current;
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank() && !value.startsWith("#")
                && value.indexOf(':') > 0 && !value.contains("..") && value.indexOf('\\') < 0;
    }

    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }
}
