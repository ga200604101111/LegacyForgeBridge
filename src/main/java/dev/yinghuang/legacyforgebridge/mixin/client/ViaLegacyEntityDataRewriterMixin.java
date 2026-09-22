package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_8;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import dev.yinghuang.legacyforgebridge.compat.LegacyRemoteEntityMetadataBridge;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Removes already-captured FML custom watcher indices before ViaLegacy applies its vanilla
 * EntityDataIndex table. Base Entity indices 0/1 continue through the normal translator.
 */
@Mixin(
        targets="net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.rewriter.EntityDataRewriter",
        remap=false
)
public abstract class ViaLegacyEntityDataRewriterMixin {
    @Inject(
            method="transform(Lcom/viaversion/viaversion/api/connection/UserConnection;Lcom/viaversion/viaversion/api/minecraft/entities/EntityTypes1_8$EntityType;Ljava/util/List;)V",
            at=@At("HEAD"),
            remap=false
    )
    private void legacyforgebridge$stripCapturedFmlWatchers(UserConnection user,EntityTypes1_8.EntityType type,
                                                            List<EntityData> entityData,CallbackInfo ci){
        if(!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target())return;
        LegacyRemoteEntityMetadataBridge.prepareForViaRewrite(entityData);
    }
}
