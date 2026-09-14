package dev.yinghuang.legacyforgebridge.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The runtime only accepts manifests embedded in installed, fully converted Fabric mods. */
public record LegacyRuntimeManifest(String fabricId, Set<String> logicalMods,
                                    Map<Identity, String> entities, Map<Identity, String> guis) {
    public static final String PATH = "legacyforgebridge/conversion-manifest.json";
    public static final int MAX_BYTES = 1_048_576;

    public LegacyRuntimeManifest {
        logicalMods = Set.copyOf(logicalMods);
        entities = Map.copyOf(entities);
        guis = Map.copyOf(guis);
        if (fabricId == null || fabricId.isBlank() || logicalMods.isEmpty()) {
            throw new IllegalArgumentException("Missing runtime manifest owner");
        }
        logicalMods.forEach(id -> new Identity(id, 0));
        validateMappings(logicalMods, entities);
        validateMappings(logicalMods, guis);
    }

    public record Identity(String modId, int legacyId) {
        public Identity {
            if (modId == null || !modId.matches("[A-Za-z0-9_.-]{1,128}")) {
                throw new IllegalArgumentException("Invalid legacy mod identity");
            }
        }
        public static Identity parse(String text) {
            int colon = text.lastIndexOf(':');
            if (colon < 1) throw new IllegalArgumentException("Expected legacy identity modid:integer");
            String number = text.substring(colon + 1);
            int id = Integer.parseInt(number);
            if (!Integer.toString(id).equals(number)) {
                throw new IllegalArgumentException("Non-canonical legacy numeric identity");
            }
            return new Identity(text.substring(0, colon), id);
        }
        @Override public String toString() { return modId + ':' + legacyId; }
    }

    public static LegacyRuntimeManifest parse(String json, String installedFabricId) {
        if (json == null || json.length() > MAX_BYTES) throw new IllegalArgumentException("Runtime manifest too large");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement schema = root.get("schemaVersion");
        if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()
                || !"1".equals(schema.getAsString())) {
            throw new IllegalArgumentException("Unsupported runtime manifest schema");
        }
        JsonElement installable = root.get("installable");
        if (installable == null || !installable.isJsonPrimitive() || !installable.getAsJsonPrimitive().isBoolean()
                || !installable.getAsBoolean() || !"converted".equals(text(root, "status"))) {
            throw new IllegalArgumentException("Runtime refuses PARTIAL/BLOCKED/non-installable conversion candidates");
        }
        JsonObject mod = object(root, "mod");
        String fabricId = text(mod, "fabricId");
        if (!fabricId.equals(installedFabricId)) throw new IllegalArgumentException("Runtime manifest Fabric owner mismatch");
        var logicalArray = mod.getAsJsonArray("logicalMods");
        if (logicalArray == null || logicalArray.isEmpty() || logicalArray.size() > 128) {
            throw new IllegalArgumentException("Invalid logical mod list");
        }
        Set<String> logical = new LinkedHashSet<>();
        for (JsonElement entry : logicalArray) {
            if (!logical.add(text(entry.getAsJsonObject(), "modid"))) {
                throw new IllegalArgumentException("Duplicate logical mod identity");
            }
        }
        JsonObject registries = object(root, "registries");
        return new LegacyRuntimeManifest(fabricId, logical, mappings(registries, "entities"), mappings(registries, "guis"));
    }

    private static Map<Identity, String> mappings(JsonObject registries, String category) {
        JsonObject entries = object(registries, category);
        if (entries.size() > 65_536) throw new IllegalArgumentException("Too many runtime identities");
        Map<Identity, String> result = new LinkedHashMap<>();
        for (var entry : entries.entrySet()) {
            Identity identity = Identity.parse(entry.getKey());
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Runtime target must be a string");
            }
            if (result.putIfAbsent(identity, value.getAsString()) != null) {
                throw new IllegalArgumentException("Duplicate runtime identity");
            }
        }
        return result;
    }
    private static void validateMappings(Set<String> logical, Map<Identity, String> mappings) {
        mappings.forEach((key, value) -> {
            if (!logical.contains(key.modId())) throw new IllegalArgumentException("Runtime identity belongs to another logical mod");
            if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                throw new IllegalArgumentException("Invalid namespaced runtime adapter identity");
            }
        });
    }
    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Missing manifest object: " + name);
        return value.getAsJsonObject();
    }
    private static String text(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing manifest string: " + name);
        }
        return value.getAsString();
    }
}
