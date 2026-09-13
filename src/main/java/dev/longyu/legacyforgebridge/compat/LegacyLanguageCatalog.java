package dev.longyu.legacyforgebridge.compat;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Private, session-scoped language source for 1.7.10 server messages.
 *
 * <p>These resources deliberately do not live under Minecraft's standard {@code lang/*.json}
 * path. Loading them into the global language map would make old item/block/UI translations
 * visible to unrelated modern components. LFB reads only the message catalogue itself.</p>
 */
final class LegacyLanguageCatalog {
    private static final Pattern LEGACY_NUMBER_FORMAT = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private LegacyLanguageCatalog() {
    }

    static String lookup(String key) {
        String language = currentLanguage();
        Map<String, String> selected = CACHE.computeIfAbsent(language, LegacyLanguageCatalog::loadLanguage);
        String value = selected.get(key);
        if (value != null || "en_us".equals(language)) {
            return value;
        }
        return CACHE.computeIfAbsent("en_us", LegacyLanguageCatalog::loadLanguage).get(key);
    }

    static void clearCache() {
        CACHE.clear();
    }

    private static String currentLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.options.languageCode == null) {
            return "en_us";
        }
        String code = minecraft.options.languageCode.toLowerCase(Locale.ROOT);
        return switch (code) {
            case "zh_tw" -> "zh_tw";
            case "en_us" -> "en_us";
            default -> code;
        };
    }

    private static Map<String, String> loadLanguage(String language) {
        Map<String, String> output = new HashMap<>();
        loadParts(output, "lfb-minecraft", language, 4);
        loadParts(output, "lfb-forge", language, "zh_tw".equals(language) ? 2 : 1);
        return Map.copyOf(output);
    }

    private static void loadParts(Map<String, String> output, String namespace, String language, int maxParts) {
        for (int index = 0; index < maxParts; index++) {
            String path = "/assets/" + namespace + "/legacy-lang/" + language + "/part" + index + ".lang";
            try (InputStream stream = LegacyLanguageCatalog.class.getResourceAsStream(path)) {
                if (stream == null) {
                    if (index == 0) {
                        LegacyForgeBridge.LOGGER.debug("No bundled legacy language catalogue at {}", path);
                    }
                    break;
                }
                parse(stream, output);
            } catch (IOException exception) {
                LegacyForgeBridge.LOGGER.warn("Failed to load legacy language catalogue {}", path, exception);
            }
        }
    }

    private static void parse(InputStream stream, Map<String, String> output) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator);
                String value = line.substring(separator + 1);
                value = LEGACY_NUMBER_FORMAT.matcher(value).replaceAll("%$1s");
                output.put(key, value);
            }
        }
    }
}
