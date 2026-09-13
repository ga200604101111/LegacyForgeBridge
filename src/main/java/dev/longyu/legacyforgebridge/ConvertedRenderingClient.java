package dev.longyu.legacyforgebridge;

import dev.longyu.legacyforgebridge.render.ConvertedEquipmentRenderRuntime;
import net.fabricmc.api.ClientModInitializer;

/** Initializes client-only presentation bridges for converted legacy content. */
public final class ConvertedRenderingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConvertedEquipmentRenderRuntime.initialize();
    }
}
