package dev.yinghuang.legacyforgebridge.protocol;

import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusLoadEntrypoint;

/** Called by ViaFabricPlus before its loading cycle starts. */
public final class ViaFabricPlusEntrypoint implements ViaFabricPlusLoadEntrypoint {
    @Override
    public void onPlatformLoad(ViaFabricPlusBase platform) {
        ViaFabricPlusBackend.INSTANCE.initialize(platform);
        /*
         * LegacyForgeBridge is a 1.7.10 compatibility client. The backend already exposed the
         * selection hook, but nothing called it, leaving ViaFabricPlus on whichever protocol the
         * user had selected previously. Select the 1.7.6-1.7.10 protocol family immediately
         * through ViaFabricPlus' public API so every bridge boundary is active before connecting.
         */
        ViaFabricPlusBackend.INSTANCE.selectMinecraft1710ForNextConnection();
    }
}
