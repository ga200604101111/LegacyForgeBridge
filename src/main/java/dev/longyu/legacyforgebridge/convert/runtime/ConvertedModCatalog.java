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

/** Legacy FML identities supplied by converted Fabric candidates currently loaded by Fabric. */
public final class ConvertedModCatalog {
    private ConvertedModCatalog() {
    }

    public static Map<String, String> legacyModVersions() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                var path = container.findPath(ConvertedContentRuntime.MANIFEST_PATH);
                if (path.isEmpty()) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonElement legacyMods = root.get("legacyMods");
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
            }
        } catch (Throwable ignored) {
            // Unit tests and very early bootstrap environments may not have a complete loader
            // runtime. The FML handshake can always fall back to the clean FML identity.
        }
        return Map.copyOf(result);
    }
}
