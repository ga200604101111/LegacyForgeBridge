package dev.longyu.legacyforgebridge.protocol;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Installs reversible 1.7.10 legacy plugin-channel aliases into ViaVersion's 1.12->1.13 mapping table. */
public final class LegacyPluginChannelMappings {
    private static final String PROTOCOL_CLASS =
            "com.viaversion.viaversion.protocols.v1_12_2to1_13.Protocol1_12_2To1_13";

    private static volatile boolean installed;

    private LegacyPluginChannelMappings() {
    }

    public static synchronized void install() {
        try {
            Class<?> protocolClass = Class.forName(PROTOCOL_CLASS);
            Field mappingsField = protocolClass.getField("MAPPINGS");
            Object mappings = mappingsField.get(null);
            Method getter = mappings.getClass().getMethod("getChannelMappings");
            Object raw = getter.invoke(mappings);

            if (!(raw instanceof Map<?, ?> rawMap)) {
                throw new IllegalStateException("ViaVersion channel mappings are not a Map: " + raw);
            }

            @SuppressWarnings("unchecked")
            Map<String, String> channelMappings = (Map<String, String>) rawMap;

            putUnique(channelMappings, "FML|HS", "legacyforgebridge:fml_hs");
            putUnique(channelMappings, "FML", "legacyforgebridge:fml");
            putUnique(channelMappings, "FORGE", "legacyforgebridge:forge");

            if (!installed) {
                LegacyForgeBridge.LOGGER.info(
                        "Installed ViaVersion legacy Forge channel aliases: FML|HS, FML, FORGE"
                );
            }
            installed = true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LegacyForgeBridge.LOGGER.error(
                    "Failed to install ViaVersion legacy Forge channel aliases; Forge 1.7.10 handshake payloads may be dropped",
                    exception
            );
        }
    }

    public static boolean installed() {
        return installed;
    }

    private static void putUnique(Map<String, String> mappings, String legacy, String modern) {
        String existingForLegacy = mappings.get(legacy);
        if (modern.equals(existingForLegacy)) {
            return;
        }

        String conflictingLegacy = null;
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (modern.equals(entry.getValue()) && !legacy.equals(entry.getKey())) {
                conflictingLegacy = entry.getKey();
                break;
            }
        }
        if (conflictingLegacy != null) {
            mappings.remove(conflictingLegacy);
        }

        mappings.put(legacy, modern);
    }
}
