package dev.yinghuang.legacyforgebridge.rev308;

import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedModCatalog;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_2960;
import net.minecraft.class_6880;
import net.minecraft.class_7924;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies only source-proven creative-inventory enchantments to newly emitted creative stacks.
 * Never mutates remote inventory, drops, combat state, or server-owned item stacks.
 */
public final class Rev308CreativeEnchantBridge {
    private Rev308CreativeEnchantBridge() {}
    private static final Map<String, Properties> CACHE = new ConcurrentHashMap<>();
    public static class_1799 decorate(class_1799 stack) {
        if (stack == null || stack.method_7960() || stack.method_7942()) return stack;
        try {
            String key = stack.method_7909().method_7876();
            if (key == null || !key.startsWith("lfb.converted.")) return stack;
            int suffix = key.indexOf('.', "lfb.converted.".length());
            if (suffix < 0) return stack;
            String modId = key.substring("lfb.converted.".length(), suffix);
            Optional<String> sha = ConvertedModCatalog.loadedSourceSha256(modId);
            if (sha.isEmpty() || !sha.get().matches("[0-9a-f]{64}")) return stack;
            Properties proof = CACHE.computeIfAbsent(sha.get(), Rev308CreativeEnchantBridge::load);
            if (!sha.get().equals(proof.getProperty("sourceSha256")) || !modId.equals(proof.getProperty("fabricModId"))) return stack;
            String enchantments = proof.getProperty(key);
            if (enchantments == null) return stack;
            class_310 client = class_310.method_1551();
            if (client == null || client.field_1687 == null) return stack;
            var registry = client.field_1687.method_30349().method_30530(class_7924.method_47517("enchantment"));
            for (String enchant : enchantments.split(",")) {
                int split = enchant.lastIndexOf(':');
                if (split <= 0) continue;
                String enchantId = enchant.substring(0,split);
                int level = Integer.parseInt(enchant.substring(split+1));
                if (level <= 0 || level > 255) continue;
                var entry = registry.method_10223(class_2960.method_60656(enchantId));
                if (entry.isPresent()) stack.method_7978((class_6880)entry.get(), level);
            }
        } catch (Exception | LinkageError missing) {
            // Fail closed: never add synthetic enchantment data when dynamic registry is unavailable.
        }
        return stack;
    }
    private static Properties load(String hash) {
        Properties p = new Properties();
        try (InputStream input = Rev308CreativeEnchantBridge.class.getClassLoader().getResourceAsStream(
                "legacyforgebridge/rev308/creative/" + hash + ".properties")) {
            if (input != null) p.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        return p;
    }
}
