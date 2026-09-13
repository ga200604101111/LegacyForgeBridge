package dev.longyu.legacyforgebridge.compat;

import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import net.minecraft.locale.Language;

/**
 * Resolves preserved Minecraft/Forge 1.7.10 translation keys from namespaced LFB language data.
 *
 * <p>The bundled JSON files intentionally prefix every legacy key so Minecraft's normal language
 * merge cannot overwrite modern translations while LegacyForgeBridge is installed. During a
 * 1.7.10 ViaFabricPlus session, this resolver checks the legacy aliases first. Outside such a
 * session, vanilla language lookup is left completely unchanged.</p>
 */
public final class LegacyTranslationBridge {
    static final String MINECRAFT_PREFIX = "lfb.minecraft.";
    static final String FORGE_PREFIX = "lfb.forge.";

    private LegacyTranslationBridge() {
    }

    public static String getOrDefault(Language language, String key) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return language.getOrDefault(key);
        }

        String legacy = findLegacy(language, key);
        return legacy != null ? legacy : language.getOrDefault(key);
    }

    public static String getOrDefault(Language language, String key, String fallback) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return language.getOrDefault(key, fallback);
        }

        String legacy = findLegacy(language, key);
        return legacy != null ? legacy : language.getOrDefault(key, fallback);
    }

    static String minecraftAlias(String key) {
        return MINECRAFT_PREFIX + key;
    }

    static String forgeAlias(String key) {
        return FORGE_PREFIX + key;
    }

    private static String findLegacy(Language language, String key) {
        // Avoid repeatedly prefixing keys produced internally by the bridge.
        if (key.startsWith(MINECRAFT_PREFIX) || key.startsWith(FORGE_PREFIX)) {
            return null;
        }

        String minecraftKey = minecraftAlias(key);
        if (language.has(minecraftKey)) {
            return language.getOrDefault(minecraftKey);
        }

        String forgeKey = forgeAlias(key);
        if (language.has(forgeKey)) {
            return language.getOrDefault(forgeKey);
        }

        return null;
    }
}
