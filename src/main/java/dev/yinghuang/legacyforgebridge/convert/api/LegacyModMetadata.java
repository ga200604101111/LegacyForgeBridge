package dev.yinghuang.legacyforgebridge.convert.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Metadata extracted without loading any legacy classes.
 */
public record LegacyModMetadata(
        String sourceFileName,
        String metadataSource,
        List<ModEntry> mods
) {
    public LegacyModMetadata {
        mods = List.copyOf(mods);
    }

    public static LegacyModMetadata read(Path jarPath) throws IOException {
        String sourceFileName = jarPath.getFileName().toString();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("mcmod.info");
            if (entry != null) {
                try (Reader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    List<ModEntry> parsed = parseModEntries(root);
                    if (!parsed.isEmpty()) {
                        return new LegacyModMetadata(sourceFileName, "mcmod.info", parsed);
                    }
                } catch (RuntimeException ignored) {
                    // Fall through to a safe filename-only identity. The conversion engine records
                    // a diagnostic when mcmod.info existed but could not provide usable metadata.
                }
            }
        }

        String stem = stripJarSuffix(sourceFileName);
        ModEntry fallback = new ModEntry(
                sanitizeFabricId(stem),
                stem,
                "0.0.0+legacy",
                "1.7.10",
                List.of()
        );
        return new LegacyModMetadata(sourceFileName, "filename-fallback", List.of(fallback));
    }

    private static List<ModEntry> parseModEntries(JsonElement root) {
        List<ModEntry> result = new ArrayList<>();
        if (root == null || root.isJsonNull()) {
            return result;
        }

        JsonArray entries = null;
        if (root.isJsonArray()) {
            entries = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement modList = object.get("modList");
            if (modList != null && modList.isJsonArray()) {
                entries = modList.getAsJsonArray();
            } else {
                entries = new JsonArray();
                entries.add(object);
            }
        }

        if (entries == null) {
            return result;
        }

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String modId = string(object, "modid", "");
            if (modId.isBlank()) {
                continue;
            }
            String name = string(object, "name", modId);
            String version = string(object, "version", "0.0.0+legacy");
            String mcVersion = string(object, "mcversion", "1.7.10");
            List<String> dependencies = dependencies(object.get("dependencies"));
            result.add(new ModEntry(modId, name, version, mcVersion, dependencies));
        }
        return result;
    }

    private static List<String> dependencies(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement dependency : element.getAsJsonArray()) {
                if (dependency.isJsonPrimitive() && dependency.getAsJsonPrimitive().isString()) {
                    result.add(dependency.getAsString());
                } else if (dependency.isJsonObject()) {
                    String modId = string(dependency.getAsJsonObject(), "modId", "");
                    if (!modId.isBlank()) {
                        result.add(modId);
                    }
                }
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            if (!raw.isBlank()) {
                result.add(raw);
            }
        }
        return List.copyOf(result);
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        String string = value.getAsString();
        return string.isBlank() ? fallback : string;
    }

    public ModEntry primary() {
        return mods.getFirst();
    }

    public boolean containsModId(String modId) {
        return mods.stream().anyMatch(mod -> mod.modId().equalsIgnoreCase(modId));
    }

    public boolean hasMultipleLogicalMods() {
        return mods.size() > 1;
    }

    public String fabricId() {
        return sanitizeFabricId(primary().modId());
    }

    public static String sanitizeFabricId(String raw) {
        String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
        while (!normalized.isEmpty() && !Character.isLetter(normalized.charAt(0))) {
            normalized = "legacy_" + normalized;
        }
        if (normalized.isBlank()) {
            normalized = "legacy_mod";
        }
        if (normalized.length() < 2) {
            normalized = normalized + "_mod";
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
    }

    private static String stripJarSuffix(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    public record ModEntry(
            String modId,
            String name,
            String version,
            String mcVersion,
            List<String> dependencies
    ) {
        public ModEntry {
            dependencies = List.copyOf(dependencies);
        }
    }
}
