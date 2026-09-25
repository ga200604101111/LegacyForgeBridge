package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ProcessorRetirementJson {
    private ProcessorRetirementJson() { }

    static JsonObject identity(JsonObject source) {
        JsonObject result = new JsonObject();
        for (String property : new String[]{"id", "sourceBlockClass", "sourceTileClass",
                "sourceContainerClass", "sourceGuiClass"}) {
            JsonElement value = source.get(property);
            if (value != null) result.add(property, value);
        }
        return result;
    }

    static boolean validReadiness(ConversionContext context, JsonObject root) {
        return integer(root, "schemaVersion", -1) == 1
                && context.sourceHash().equals(string(root, "sourceSha256", ""))
                && bool(root, "retirementReadinessAnalysisWired", false)
                && !bool(root, "retirementAuthorizationWired", true)
                && !bool(root, "sourceClassDeletionWired", true);
    }

    static Map<String,Path> classPaths(Path staging, Set<String> cohort,
            Set<String> blockers) {
        Map<String,Path> result = new LinkedHashMap<>();
        for (String target : cohort) {
            Path path = safeClassPath(staging, target);
            if (path == null) blockers.add("unsafe-source-class-path:" + target);
            else if (!Files.isRegularFile(path)) blockers.add("source-class-not-present:" + target);
            else result.put(target, path);
        }
        return result;
    }

    static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static Path safeClassPath(Path staging, String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || name.contains("..") || name.indexOf('\0') >= 0) return null;
        Path root = staging.toAbsolutePath().normalize();
        Path path = root.resolve(name + ".class").normalize();
        return path.startsWith(root) ? path : null;
    }
}
