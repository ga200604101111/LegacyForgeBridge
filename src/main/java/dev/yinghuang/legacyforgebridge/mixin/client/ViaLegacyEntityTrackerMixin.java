package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.compat.LegacyRemoteEntityMetadataBridge;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Captures raw 1.7.10 watcher updates for proof-gated FML carriers before ViaLegacy rewrites them. */
@Mixin(
        targets="net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.storage.EntityTracker",
        remap=false
)
public abstract class ViaLegacyEntityTrackerMixin {
    @Inject(method="updateEntityData(ILjava/util/List;)V",at=@At("HEAD"),remap=false)
    private void legacyforgebridge$captureFmlMetadata(int entityId,List<?> entityData,CallbackInfo ci){
        if(!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target())return;
        LegacyRemoteEntityMetadataBridge.capture(entityId,entityData);
    }

    @Inject(method="removeEntity(I)V",at=@At("HEAD"),remap=false)
    private void legacyforgebridge$forgetFmlEntity(int entityId,CallbackInfo ci){
        LegacyRemoteEntityMetadataBridge.unregisterEntityId(entityId);
    }
}
