package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import dev.yinghuang.legacyforgebridge.compat.LegacyModItemIdentityBridge;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bridges Forge numeric mod item IDs around ViaVersion's lossy 1.12.2 -> 1.13 boundary. */
@Mixin(
        targets = "com.viaversion.viaversion.protocols.v1_12_2to1_13.rewriter.ItemPacketRewriter1_13",
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
