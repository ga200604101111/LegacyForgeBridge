package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConvertedModCatalogTest {
    @Test
    void prefersExactNetworkVersionAndFallsBackToDisplayVersion() {
        JsonObject root = new JsonObject();
        JsonArray mods = new JsonArray();

        JsonObject exact = new JsonObject();
        exact.addProperty("modid", "BambooMod");
        exact.addProperty("version", "Minecraft1.7.10 ver2.6.8.5");
        exact.addProperty("networkVersion", "Minecraft@MC_VERSION@ var@VERSION@");
        mods.add(exact);

        JsonObject fallback = new JsonObject();
        fallback.addProperty("modid", "ExampleMod");
        fallback.addProperty("version", "1.2.3");
        mods.add(fallback);
        root.add("legacyMods", mods);

        assertEquals(Map.of(
                "BambooMod", "Minecraft@MC_VERSION@ var@VERSION@",
                "ExampleMod", "1.2.3"
        ), ConvertedModCatalog.legacyModVersions(root));
    }
}
