package dev.longyu.legacyforgebridge.compat;

import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import net.minecraft.locale.Language;

import java.util.Set;

/**
 * Resolves preserved Minecraft/Forge 1.7.10 message keys from namespaced LFB language data.
 *
 * <p>The bundled JSON files use prefixed alias keys, so loading the mod never overwrites modern
 * Minecraft translations. During a 1.7.10 ViaFabricPlus session only message-oriented legacy keys
 * are resolved through the catalogue. Item, block, entity, container and other normal content
 * names continue through ViaVersion's normal key mappings and the 1.21.11 language table.</p>
 */
public final class LegacyTranslationBridge {
    static final String MINECRAFT_PREFIX = "lfb.minecraft.";
    static final String FORGE_PREFIX = "lfb.forge.";

    private static final String[] VANILLA_MESSAGE_PREFIXES = {
            "commands.",
            "chat.type.",
            "chat.stream.",
            "death.",
            "multiplayer.player.",
            "multiplayer.disconnect.",
            "disconnect.",
            "achievement.",
            "stat.",
            "stats.tooltip.",
            "gameMode."
    };

    private static final Set<String> VANILLA_MESSAGE_KEYS = Set.of(
            "tile.bed.occupied",
            "tile.bed.noSleep",
            "tile.bed.notSafe",
            "tile.bed.notValid"
    );

    private LegacyTranslationBridge() {
    }

    /**
     * Whether ViaVersion should preserve this 1.7.10 translation key instead of translating it to
     * a newer key or an English inline fallback.
     */
    public static boolean shouldPreserveKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }

        // Forge/FML keys do not collide with modern vanilla content keys and may be used by
        // server/mod messages, so retain their legacy identity.
        if (key.startsWith("forge.") || key.startsWith("fml.")) {
            return true;
        }

        if (VANILLA_MESSAGE_KEYS.contains(key)) {
            return true;
        }

        for (String prefix : VANILLA_MESSAGE_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String getOrDefault(Language language, String key) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target() || !shouldPreserveKey(key)) {
            return language.getOrDefault(key);
        }

        String legacy = findLegacy(language, key);
        return legacy != null ? legacy : language.getOrDefault(key);
    }

    public static String getOrDefault(Language language, String key, String fallback) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target() || !shouldPreserveKey(key)) {
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
