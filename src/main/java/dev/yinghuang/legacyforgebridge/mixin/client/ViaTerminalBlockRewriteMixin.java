package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.api.protocol.Protocol;
import dev.yinghuang.legacyforgebridge.compat.LegacyTerminalBlockRewritePolicy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Retain the native-edge block handlers even when Via's vanilla block-state map is identity. */
@Mixin(targets = "com.viaversion.viaversion.rewriter.BlockRewriter", remap = false)
public abstract class ViaTerminalBlockRewriteMixin {
    @Shadow @Final protected Protocol<?, ?, ?, ?> protocol;

    @Redirect(method = {"registerBlockUpdate", "registerChunkBlocksUpdate", "registerSectionBlocksUpdate",
            "registerSectionBlocksUpdate1_20", "registerLevelEvent", "handleChunk1_18"},
            at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/data/Mappings;isIntIdIdentity(Lcom/viaversion/viaversion/api/data/Mappings;)Z"),
            remap = false, require = 7)
    private boolean legacyforgebridge$retainTerminalBlockStateRewrite(Mappings mappings) {
        var data = protocol.getMappingData();
        return LegacyTerminalBlockRewritePolicy.maySkip(protocol.getClass().getName(),
                data != null && mappings != null && mappings == data.getBlockStateMappings(),
                Mappings.isIntIdIdentity(mappings));
    }
}
