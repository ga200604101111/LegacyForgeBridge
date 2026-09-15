package dev.yinghuang.legacyforgebridge;

import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.convert.LegacyConversionManager;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedContentRuntime;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacyProcessorMenuSupport;
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

        // Register shared LFB-owned state and menu types before converted content is constructed.
        LegacyStackComponents.bootstrap();
        LegacyProcessorMenuSupport.bootstrap();

        FmlConnectionTrace.INSTANCE.initializeForLaunch();

        try {
            ConvertedContentRuntime.initialize();
        } catch (Exception exception) {
            LOGGER.error("Converted legacy content registration failed for this launch.", exception);
        }

        try {
            LegacyConversionManager manager = new LegacyConversionManager();
            manager.initialize();
        } catch (Exception exception) {
            LOGGER.error("LegacyForgeBridge initialization failed; legacy conversion has been disabled for this launch.", exception);
        }
    }
}
