package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class LegacyNameTagBehaviorMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",at=@At("TAIL"))
    private void legacyforgebridge$renderSpecialsPre(Entity entity,EntityRenderState state,float partialTicks,CallbackInfo ci){
        if(state.nameTag!=null&&entity instanceof LivingEntity living&&LegacyBehaviorRuntime.renderSpecialsPre(living)){
            state.nameTag=null;
            state.nameTagAttachment=null;
        }
    }
}
