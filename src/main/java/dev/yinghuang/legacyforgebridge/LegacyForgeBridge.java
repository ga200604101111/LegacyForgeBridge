package dev.yinghuang.legacyforgebridge;

import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.convert.LegacyConversionManager;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedContentRuntime;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacyProcessorMenuSupport;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacySeatEntityRuntime;
import dev.yinghuang.legacyforgebridge.network.FmlConnectionTrace;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class LegacyForgeBridge implements ModInitializer {
    public static final String MOD_ID = "legacyforgebridge";
    /** LFB-internal diagnostics belong in logs/legacyforgebridge.log, not Minecraft latest.log. */
    public static final LegacyFileLogger LOGGER = new LegacyFileLogger();
    /** Used only when the dedicated LFB log itself cannot be opened. */
    private static final Logger GAME_LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Path logPath = LOGGER.initializeForLaunch();
        if (logPath == null) {
            GAME_LOGGER.error(
                    "LegacyForgeBridge could not open logs/legacyforgebridge.log; dedicated LFB diagnostics are unavailable.",
                    LOGGER.initializationFailure()
            );
        }
        LOGGER.info("Initializing LegacyForgeBridge {}", BuildInfo.VERSION);

        // Register shared LFB-owned state/menu/entity types before converted content is constructed.
        LegacyStackComponents.bootstrap();
        LegacyProcessorMenuSupport.bootstrap();
        LegacySeatEntityRuntime.bootstrap();

        // Forge/FML tracing shares the same dedicated launch log instead of opening a second writer.
        FmlConnectionTrace.INSTANCE.initializeForLaunch();

        try {
            ConvertedContentRuntime.initialize();
        } catch (Exception exception) {
            LOGGER.error("Converted legacy content registration failed for this launch.", exception);
            if (!LOGGER.isAvailable()) {
                GAME_LOGGER.error("Converted legacy content registration failed; LFB dedicated log is unavailable.");
            }
        }

        try {
            LegacyConversionManager manager = new LegacyConversionManager();
            manager.initialize();
        } catch (Exception exception) {
            LOGGER.error("LegacyForgeBridge initialization failed; legacy conversion has been disabled for this launch.", exception);
            if (!LOGGER.isAvailable()) {
                GAME_LOGGER.error("LegacyForgeBridge initialization failed; dedicated LFB log is unavailable.");
            }
        }
    }
}
