package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.data.Mappings;
import dev.yinghuang.legacyforgebridge.compat.LegacyModBlockStateBridge;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries generic legacy mod block-state tokens through every later ViaVersion mapping boundary. */
@Mixin(
        targets = "com.viaversion.viaversion.api.data.MappingDataBase",
        remap = false
)
public abstract class ViaBlockStateMappingBoundaryMixin {
    @Shadow
    @Final
    protected String unmappedVersion;

    @Shadow
    @Final
    protected String mappedVersion;

    @Shadow
    protected Mappings blockStateMappings;

    @Inject(
            method = "getNewBlockStateId(I)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void legacyforgebridge$carryLegacyModBlockAcrossProtocolMapping(
            int stateId,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
        Mappings mappings = blockStateMappings;
        if (mappings == null) return;

        int mapped = LegacyModBlockStateBridge.carryAcrossMapping(
                stateId,
                unmappedVersion,
                mappings.size(),
                mappedVersion,
                mappings.mappedSize()
        );
        if (mapped != LegacyModBlockStateBridge.NO_MAPPING) cir.setReturnValue(mapped);
    }
}
