package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.compat.LegacyRegistryIdentity;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Legacy FML identities supplied by converted Fabric candidates currently loaded by Fabric. */
public final class ConvertedModCatalog {
    private static final String CONVERSION_MANIFEST_PATH = "legacyforgebridge/conversion-manifest.json";
    private static final Set<String> STALE_CONVERTED_MODS = ConcurrentHashMap.newKeySet();

    private ConvertedModCatalog() {
    }

    public static Map<String, String> legacyModVersions() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                String fabricId = container.getMetadata().getId();
                if (STALE_CONVERTED_MODS.contains(fabricId)) {
                    continue;
                }
                Optional<JsonObject> content = readConvertedContent(container);
                if (content.isEmpty()) {
                    continue;
                }
                JsonElement legacyMods = content.get().get("legacyMods");
                if (legacyMods == null || !legacyMods.isJsonArray()) {
                    continue;
                }
                for (JsonElement element : legacyMods.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject mod = element.getAsJsonObject();
                    JsonElement modId = mod.get("modid");
                    JsonElement version = mod.get("version");
                    if (modId != null && version != null) {
                        result.put(modId.getAsString(), version.getAsString());
                    }
                }
            }
        } catch (Throwable ignored) {
            // Unit tests and very early bootstrap environments may not have a complete loader
            // runtime. The FML handshake can always fall back to the clean FML identity.
        }
        return Map.copyOf(result);
    }

    public static Map<String, Identifier> legacyBlockRegistryAliases() {
        return legacyRegistryAliases("blocks");
    }

    public static Map<String, Identifier> legacyItemRegistryAliases() {
        return legacyRegistryAliases("items");
    }

    /**
     * Reads exact legacy->modern registry aliases recorded by conversion. Ambiguous aliases from
     * multiple loaded candidates are removed instead of being resolved by load order.
     */
    private static Map<String, Identifier> legacyRegistryAliases(String category) {
        Map<String, Identifier> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                String fabricId = container.getMetadata().getId();
                if (STALE_CONVERTED_MODS.contains(fabricId)) continue;
                Optional<JsonObject> manifest = readJson(container, CONVERSION_MANIFEST_PATH);
                if (manifest.isEmpty()) continue;

                JsonElement registriesElement = manifest.get().get("registries");
                if (registriesElement == null || !registriesElement.isJsonObject()) continue;
                JsonElement categoryElement = registriesElement.getAsJsonObject().get(category);
                if (categoryElement == null || !categoryElement.isJsonObject()) continue;

                for (Map.Entry<String, JsonElement> entry : categoryElement.getAsJsonObject().entrySet()) {
                    String canonical = LegacyRegistryIdentity.canonical(entry.getKey());
                    if (canonical == null || ambiguous.contains(canonical)
                            || !entry.getValue().isJsonPrimitive()) continue;
                    Identifier modern;
                    try {
                        modern = Identifier.parse(entry.getValue().getAsString());
                    } catch (RuntimeException invalidIdentifier) {
                        continue;
                    }
                    Identifier previous = result.putIfAbsent(canonical, modern);
                    if (previous != null && !previous.equals(modern)) {
                        result.remove(canonical);
                        ambiguous.add(canonical);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Registry aliases are an optimization over canonical identity normalization. Missing
            // loader state therefore degrades to the normalizer rather than breaking handshake.
        }
        return Map.copyOf(result);
    }

    public static boolean isAnyModLoaded(String fabricId) {
        try {
            return FabricLoader.getInstance().getModContainer(fabricId).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isConvertedCandidateLoaded(String fabricId) {
        try {
            return FabricLoader.getInstance().getModContainer(fabricId)
                    .flatMap(ConvertedModCatalog::readConvertedContent)
                    .isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Optional<String> loadedSourceSha256(String fabricId) {
        try {
            return FabricLoader.getInstance().getModContainer(fabricId)
                    .flatMap(ConvertedModCatalog::readConvertedContent)
                    .map(content -> content.get("sourceSha256"))
                    .filter(element -> element != null && element.isJsonPrimitive())
                    .map(JsonElement::getAsString);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    /**
     * A converted mod whose old source or converter output changed must not advertise its stale FML
     * identity during this launch. The newly staged candidate becomes authoritative after restart.
     */
    public static void markStale(String fabricId) {
        if (fabricId != null && !fabricId.isBlank()) {
            STALE_CONVERTED_MODS.add(fabricId);
        }
    }

    public static boolean isMarkedStale(String fabricId) {
        return STALE_CONVERTED_MODS.contains(fabricId);
    }

    private static Optional<JsonObject> readConvertedContent(ModContainer container) {
        return readJson(container, ConvertedContentRuntime.MANIFEST_PATH);
    }

    private static Optional<JsonObject> readJson(ModContainer container, String pathValue) {
        var path = container.findPath(pathValue);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
