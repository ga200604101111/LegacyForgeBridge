package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlantPresentationPass;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Client bootstrap for source-proven plant cross models and their required cutout terrain layer. */
public final class ConvertedPlantPresentationRuntime {
    private ConvertedPlantPresentationRuntime() { }

    public static void initializeMod(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyPlantPresentationPass.OUTPUT);
        if (path.isEmpty()) return;
        int mapped = 0;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!bool(value, "presentationComplete") || !bool(value, "cutoutRuntimeComplete") || !value.has("modernId")) continue;
                Identifier id;
                try { id = Identifier.parse(value.get("modernId").getAsString()); }
                catch (RuntimeException invalid) { continue; }
                if (!id.getNamespace().equals(modId) || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
                BlockRenderLayerMap.putBlock(BuiltInRegistries.BLOCK.getValue(id), ChunkSectionLayer.CUTOUT);
                mapped++;
            }
            if (mapped > 0) LegacyForgeBridge.LOGGER.info("Installed converted plant cutout presentation: mod={}, blocks={}", modId, mapped);
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to initialize converted plant presentation for {}", modId, exception);
        }
    }

    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
