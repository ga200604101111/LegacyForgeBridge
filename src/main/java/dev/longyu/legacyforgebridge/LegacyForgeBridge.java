package dev.longyu.legacyforgebridge;

import dev.longyu.legacyforgebridge.compat.LegacyStackComponents;
import dev.longyu.legacyforgebridge.convert.LegacyConversionManager;
import dev.longyu.legacyforgebridge.convert.runtime.ConvertedContentRuntime;
import dev.longyu.legacyforgebridge.network.FmlConnectionTrace;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LegacyForgeBridge implements ModInitializer {
    public static final String MOD_ID = "legacyforgebridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing LegacyForgeBridge {}", BuildInfo.VERSION);

        // Register LFB-owned stack components before any converted item defaults are constructed.
        LegacyStackComponents.bootstrap();

        // One deterministic trace file per Minecraft launch. This is intentionally initialized
        // before any connection attempt so the previous launch's trace is cleared immediately.
        FmlConnectionTrace.INSTANCE.initializeForLaunch();

        // Converted candidates are ordinary Fabric resource containers whose legacy classes were
        // removed by the conversion engine. Register their modern runtime-backed content before
        // networking starts so Forge registry identities have real client objects to target.
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
