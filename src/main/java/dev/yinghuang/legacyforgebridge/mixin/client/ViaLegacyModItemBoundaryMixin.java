package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import dev.yinghuang.legacyforgebridge.compat.LegacyModItemIdentityBridge;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Forge item identities in a carrier through the ENTIRE Via pipeline (1.7.10 edge). */
// Restoring at 1.13 was too early: ViaLegacy subsequently replaces non-existent vanilla
// 1.8 IDs (including 165..169 and 179..192) with stone. Forge reuses those IDs for mod content.
// Verified against the nested ViaLegacy JAR in ViaFabricPlus 4.4.15, not a moving snapshot.
@Mixin(
        targets = "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.rewriter.ItemRewriter",
        remap = false
)
public abstract class ViaLegacyModItemBoundaryMixin {
    @Inject(
            method = "handleItemToClient(Lcom/viaversion/viaversion/api/connection/UserConnection;Lcom/viaversion/viaversion/api/minecraft/item/Item;)Lcom/viaversion/viaversion/api/minecraft/item/Item;",
            at = @At("HEAD"),
            remap = false
    )
    private void legacyforgebridge$preserveLegacyModItemBeforeViaFallback(
            UserConnection connection,
            Item item,
            CallbackInfoReturnable<Item> cir
    ) {
        if (ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            LegacyModItemIdentityBridge.prepareLegacyItemForVia(item);
        }
    }

    @Inject(
            method = "handleItemToServer(Lcom/viaversion/viaversion/api/connection/UserConnection;Lcom/viaversion/viaversion/api/minecraft/item/Item;)Lcom/viaversion/viaversion/api/minecraft/item/Item;",
            at = @At("RETURN"),
            remap = false
    )
    private void legacyforgebridge$restoreLegacyModItemAfterViaMapping(
            UserConnection connection,
            Item item,
            CallbackInfoReturnable<Item> cir
    ) {
        if (ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            LegacyModItemIdentityBridge.restoreLegacyItemFromVia(cir.getReturnValue());
        }
    }
}
