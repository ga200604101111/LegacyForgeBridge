package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import dev.yinghuang.legacyforgebridge.compat.LegacyViaStackComponents;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let native creative/recipe stacks enter Via without losing their mod-owned metadata codec. */
@Mixin(targets = "com.viaversion.viaversion.api.type.types.item.StructuredDataType", remap = false)
public abstract class ViaNativeMetadataCodecMixin {
    @Inject(method = "key(I)Lcom/viaversion/viaversion/api/minecraft/data/StructuredDataKey;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void legacyforgebridge$readNativeMetadata(int id, CallbackInfoReturnable<StructuredDataKey<?>> cir) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
        StructuredDataKey<?> key = LegacyViaStackComponents.key(this, id);
        if (key != null) cir.setReturnValue(key);
    }
}
