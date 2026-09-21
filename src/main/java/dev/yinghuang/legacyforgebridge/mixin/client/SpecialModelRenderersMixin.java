package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.render.ConvertedLegacyInertModelSpecialRenderer;
import dev.yinghuang.legacyforgebridge.render.ConvertedLegacyOscillatingModelSpecialRenderer;
import dev.yinghuang.legacyforgebridge.render.ConvertedLegacyProcessorSpecialRenderer;
import dev.yinghuang.legacyforgebridge.render.ConvertedLegacyRadialModelSpecialRenderer;
import dev.yinghuang.legacyforgebridge.render.ObjSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpecialModelRenderers.class)
public abstract class SpecialModelRenderersMixin {
    @Shadow @Final private static ExtraCodecs.LateBoundIdMapper ID_MAPPER;
    @Inject(method="bootstrap",at=@At("TAIL"))
    private static void lfb$registerConvertedSpecialRenderers(CallbackInfo ci){
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("legacyforgebridge","obj"),ObjSpecialRenderer.Unbaked.MAP_CODEC);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("legacyforgebridge","processor"),ConvertedLegacyProcessorSpecialRenderer.Unbaked.MAP_CODEC);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("legacyforgebridge","inert_model"),ConvertedLegacyInertModelSpecialRenderer.Unbaked.MAP_CODEC);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("legacyforgebridge","oscillating_model"),ConvertedLegacyOscillatingModelSpecialRenderer.Unbaked.MAP_CODEC);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("legacyforgebridge","radial_model"),ConvertedLegacyRadialModelSpecialRenderer.Unbaked.MAP_CODEC);
    }
}
