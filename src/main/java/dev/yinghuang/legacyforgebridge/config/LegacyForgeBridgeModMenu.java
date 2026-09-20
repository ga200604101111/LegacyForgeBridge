package dev.yinghuang.legacyforgebridge.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional Mod Menu entry; the same Cloth screen is also available through the O keybind. */
public final class LegacyForgeBridgeModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LegacyBlockingPoseConfigScreen::create;
    }
}
