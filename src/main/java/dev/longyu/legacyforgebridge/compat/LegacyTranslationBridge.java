package dev.longyu.legacyforgebridge.compat;

import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import net.minecraft.locale.Language;

import java.util.Set;

/**
 * Resolves preserved Minecraft/Forge 1.7.10 server-message keys from LFB's private language
 * catalogue.
 *
 * <p>Only message-oriented keys are preserved. Item, block, entity, container and ordinary UI
 * translation keys continue through ViaVersion's normal mapping pipeline and Minecraft 1.21.11's
 * language table.</p>
 */
public final class LegacyTranslationBridge {
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

    private static final String[] FORGE_MESSAGE_PREFIXES = {
            "commands.forge.",
            "forge.update."
    };

    private static final Set<String> VANILLA_MESSAGE_KEYS = Set.of(
            "tile.bed.occupied",
            "tile.bed.noSleep",
            "tile.bed.notSafe",
            "tile.bed.notValid"
    );

    private static final Set<String> FORGE_MESSAGE_KEYS = Set.of(
            "forge.texture.preload.warning",
            "forge.client.shutdown.internal"
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

        if (VANILLA_MESSAGE_KEYS.contains(key) || FORGE_MESSAGE_KEYS.contains(key)) {
            return true;
        }

        for (String prefix : VANILLA_MESSAGE_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : FORGE_MESSAGE_PREFIXES) {
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

        String legacy = LegacyLanguageCatalog.lookup(key);
        return legacy != null ? legacy : language.getOrDefault(key);
    }

    public static String getOrDefault(Language language, String key, String fallback) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target() || !shouldPreserveKey(key)) {
            return language.getOrDefault(key, fallback);
        }

        String legacy = LegacyLanguageCatalog.lookup(key);
        return legacy != null ? legacy : language.getOrDefault(key, fallback);
    }
}
