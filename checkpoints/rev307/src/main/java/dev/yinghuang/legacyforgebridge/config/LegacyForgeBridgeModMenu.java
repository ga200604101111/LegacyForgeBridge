package dev.yinghuang.legacyforgebridge.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.yinghuang.legacyforgebridge.config.legacy.LegacyForgeConfigRegistry;
import dev.yinghuang.legacyforgebridge.config.legacy.LegacyForgeConfigScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LFG's own Mod Menu gear retains its global settings. Converted legacy mods
 * receive their own gear via the documented provider API, without modifying
 * any original Forge JAR or registering fake Fabric containers.
 */
public final class LegacyForgeBridgeModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LegacyBlockingPoseConfigScreen::create;
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> provided = new LinkedHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (LegacyForgeConfigRegistry.hasConvertedManifest(mod)) {
                provided.put(mod.getMetadata().getId(), parent -> LegacyForgeConfigScreen.create(parent, mod));
            }
        }
        return provided;
    }
}
