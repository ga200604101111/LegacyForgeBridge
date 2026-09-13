package dev.longyu.legacyforgebridge.convert.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Legacy FML identities supplied by converted Fabric candidates currently loaded by Fabric. */
public final class ConvertedModCatalog {
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
        var path = container.findPath(ConvertedContentRuntime.MANIFEST_PATH);
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
