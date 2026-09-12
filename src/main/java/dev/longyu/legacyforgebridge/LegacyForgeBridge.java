package dev.longyu.legacyforgebridge;

import dev.longyu.legacyforgebridge.convert.LegacyConversionManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LegacyForgeBridge implements ModInitializer {
    public static final String MOD_ID = "legacyforgebridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing LegacyForgeBridge {}", BuildInfo.VERSION);

        try {
            LegacyConversionManager manager = new LegacyConversionManager();
            manager.initialize();
        } catch (Exception exception) {
            LOGGER.error("LegacyForgeBridge initialization failed; legacy conversion has been disabled for this launch.", exception);
        }
    }
}
