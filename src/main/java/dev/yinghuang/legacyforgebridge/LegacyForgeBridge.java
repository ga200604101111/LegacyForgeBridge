package dev.yinghuang.legacyforgebridge;

import dev.yinghuang.legacyforgebridge.convert.LegacyConversionManager;
import dev.yinghuang.legacyforgebridge.network.FmlConnectionTrace;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LegacyForgeBridge implements ModInitializer {
    public static final String MOD_ID = "legacyforgebridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing LegacyForgeBridge {}", BuildInfo.VERSION);

        // One deterministic trace file per Minecraft launch. This is intentionally initialized
        // before any connection attempt so the previous launch's trace is cleared immediately.
        FmlConnectionTrace.INSTANCE.initializeForLaunch();

        try {
            LegacyConversionManager manager = new LegacyConversionManager();
            manager.initialize();
        } catch (Exception exception) {
            LOGGER.error("LegacyForgeBridge initialization failed; legacy conversion has been disabled for this launch.", exception);
        }
    }
}
