package dev.longyu.legacyforgebridge.protocol;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.network.FmlConnectionTrace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Installs reversible 1.7.10 legacy plugin-channel aliases into ViaVersion's 1.12->1.13 mapping table. */
public final class LegacyPluginChannelMappings {
    private static final String VIA_CLASS = "com.viaversion.viaversion.api.Via";
    private static final String VIA_MANAGER_CLASS = "com.viaversion.viaversion.api.ViaManager";
    private static final String PROTOCOL_CLASS =
            "com.viaversion.viaversion.protocols.v1_12_2to1_13.Protocol1_12_2To1_13";

    private static volatile boolean installed;
    private static volatile boolean failureLogged;

    private LegacyPluginChannelMappings() {
    }

    /**
     * Installs the aliases only after ViaVersion reports that its manager and protocol registry are initialized.
     *
     * <p>This guard is intentionally checked before loading Protocol1_12_2To1_13. Loading that protocol class too
     * early triggers its static logger while ViaVersion has no platform yet, which permanently poisons the class
     * with ExceptionInInitializerError for the rest of the JVM.</p>
     *
     * @return true when the aliases are installed, false when ViaVersion is not ready yet or installation failed
     */
    public static synchronized boolean installIfReady() {
        if (installed) {
            return true;
        }

        try {
            Class<?> viaClass = Class.forName(VIA_CLASS);
            Method isLoadedMethod = viaClass.getMethod("isLoaded");
            if (!Boolean.TRUE.equals(isLoadedMethod.invoke(null))) {
                return false;
            }

            Object manager = viaClass.getMethod("getManager").invoke(null);
            Class<?> viaManagerClass = Class.forName(VIA_MANAGER_CLASS);
            Method isInitializedMethod = viaManagerClass.getMethod("isInitialized");
            if (!Boolean.TRUE.equals(isInitializedMethod.invoke(manager))) {
                return false;
            }

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

            installed = true;
            failureLogged = false;
            LegacyForgeBridge.LOGGER.info(
                    "Installed ViaVersion legacy Forge channel aliases: FML|HS, FML, FORGE"
            );
            FmlConnectionTrace.INSTANCE.event("ViaVersion legacy Forge channel aliases installed");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                LegacyForgeBridge.LOGGER.error(
                        "Failed to install ViaVersion legacy Forge channel aliases after ViaVersion reported ready; Forge 1.7.10 handshake payloads may be dropped",
                        exception
                );
                FmlConnectionTrace.INSTANCE.event("ViaVersion Forge channel mapping installation failed: " + exception);
            }
            return false;
        }
    }

    /** Backward-compatible entry point. Safe to call before ViaVersion is ready. */
    public static void install() {
        installIfReady();
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
