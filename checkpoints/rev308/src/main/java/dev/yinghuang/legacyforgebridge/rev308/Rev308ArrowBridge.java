package dev.yinghuang.legacyforgebridge.rev308;

import dev.yinghuang.legacyforgebridge.compat.LegacyProjectilePresentationRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedModCatalog;
import net.minecraft.class_2960;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/** Source-SHA-qualified supplemental remote arrow render rules. No client gameplay or AI execution. */
public final class Rev308ArrowBridge {
    private Rev308ArrowBridge() {}
    public static LegacyProjectilePresentationRegistry.Rule sourceRule(String modId) {
        if (modId == null) return null;
        Optional<String> source = ConvertedModCatalog.loadedSourceSha256(modId);
        if (source.isEmpty() || !source.get().matches("[0-9a-f]{64}")) return null;
        String resource = "legacyforgebridge/rev308/arrow/" + source.get() + ".properties";
        try (InputStream input = Rev308ArrowBridge.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return null;
            Properties p = new Properties();
            p.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            if (!source.get().equals(p.getProperty("sourceSha256")) || !modId.equals(p.getProperty("fabricModId"))) return null;
            String legacyModId = p.getProperty("legacyModId");
            int legacyNumericId = Integer.parseInt(p.getProperty("legacyNumericId"));
            class_2960 modernId = class_2960.method_60656(p.getProperty("modernEntityId"));
            if (LegacyProjectilePresentationRegistry.rule(modernId) != null ||
                LegacyProjectilePresentationRegistry.remoteSpawnRule(legacyModId, legacyNumericId) != null) return null;
            return new LegacyProjectilePresentationRegistry.Rule(
                    modernId, legacyModId, legacyNumericId,
                    Integer.parseInt(p.getProperty("trackingRange")),
                    Integer.parseInt(p.getProperty("updateFrequency")),
                    Boolean.parseBoolean(p.getProperty("velocityUpdates")),
                    Float.parseFloat(p.getProperty("width")), Float.parseFloat(p.getProperty("height")),
                    LegacyProjectilePresentationRegistry.BaseFamily.ARROW,
                    LegacyProjectilePresentationRegistry.Adapter.ORIENTED_ITEM,
                    class_2960.method_60656(p.getProperty("itemId")), -1, -1, 0, 0, null);
        } catch (Exception | LinkageError rejected) {
            // Invalid or unavailable exact source proof is not an invitation to guess IDs.
            return null;
        }
    }
}
