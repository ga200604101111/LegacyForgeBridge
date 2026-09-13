package dev.longyu.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.rewriter.Rewriter;
import dev.longyu.legacyforgebridge.compat.LegacyModItemIdentityBridge;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores converted items only at ViaVersion's final 1.21.11 structured-item boundary. */
@Mixin(targets = "com.viaversion.viaversion.rewriter.StructuredItemRewriter", remap = false)
public abstract class ViaModernModItemBoundaryMixin {
    private static final String FINAL_REWRITER =
            "com.viaversion.viaversion.protocols.v1_21_9to1_21_11.rewriter.BlockItemPacketRewriter1_21_11";

    @Inject(
            method = "handleItemToClient(Lcom/viaversion/viaversion/api/connection/UserConnection;Lcom/viaversion/viaversion/api/minecraft/item/Item;)Lcom/viaversion/viaversion/api/minecraft/item/Item;",
            at = @At("RETURN"),
            remap = false
    )
    private void legacyforgebridge$restoreConvertedItemAtModernEdge(
            UserConnection connection,
            Item item,
            CallbackInfoReturnable<Item> cir
    ) {
        if (activeFinalRewriter()) {
            LegacyModItemIdentityBridge.restoreModernItemFromVia(cir.getReturnValue());
        }
    }

    @Inject(
            method = "handleItemToServer(Lcom/viaversion/viaversion/api/connection/UserConnection;Lcom/viaversion/viaversion/api/minecraft/item/Item;)Lcom/viaversion/viaversion/api/minecraft/item/Item;",
            at = @At("HEAD"),
            remap = false
    )
    private void legacyforgebridge$encodeConvertedItemBeforeModernMapping(
            UserConnection connection,
            Item item,
            CallbackInfoReturnable<Item> cir
    ) {
        if (!activeFinalRewriter()) {
            return;
        }
        Protocol<?, ?, ?, ?> protocol = ((Rewriter<?>)(Object) this).protocol();
        LegacyModItemIdentityBridge.prepareModernItemForVia(item, protocol);
    }

    private boolean activeFinalRewriter() {
        return ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()
                && FINAL_REWRITER.equals(getClass().getName());
    }
}
