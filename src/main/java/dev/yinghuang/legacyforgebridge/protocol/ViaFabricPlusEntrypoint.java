package dev.longyu.legacyforgebridge.protocol;

import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusLoadEntrypoint;

/** Called by ViaFabricPlus before its loading cycle starts. */
public final class ViaFabricPlusEntrypoint implements ViaFabricPlusLoadEntrypoint {
    @Override
    public void onPlatformLoad(ViaFabricPlusBase platform) {
        ViaFabricPlusBackend.INSTANCE.initialize(platform);
    }
}
