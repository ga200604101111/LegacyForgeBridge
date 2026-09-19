package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.Protocol1_12_2To1_13;
import dev.yinghuang.legacyforgebridge.compat.LegacyModBlockStateBridge;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enters generic Forge mod blocks into the carrier range at ViaVersion's flattening boundary. */
@Mixin(
        targets = "com.viaversion.viaversion.protocols.v1_12_2to1_13.rewriter.WorldPacketRewriter1_13",
        remap = false
)
public abstract class ViaLegacyModBlockBoundaryMixin {
    @Inject(
            method = "toNewId(I)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void legacyforgebridge$carryLegacyModBlockIntoFlattenedProtocols(
            int oldStateId,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
        Mappings mappings = Protocol1_12_2To1_13.MAPPINGS.getBlockMappings();
        if (mappings == null) return;
        int mapped = LegacyModBlockStateBridge.enterLegacyState(
                oldStateId,
                "1.13",
                mappings.mappedSize()
        );
        if (mapped != LegacyModBlockStateBridge.NO_MAPPING) cir.setReturnValue(mapped);
    }
}
