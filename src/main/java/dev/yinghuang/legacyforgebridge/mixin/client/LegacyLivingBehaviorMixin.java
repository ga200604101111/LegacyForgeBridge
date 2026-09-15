package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LegacyLivingBehaviorMixin {
    @Inject(method="jumpFromGround",at=@At("TAIL"))
    private void legacyforgebridge$sourceJump(CallbackInfo ci){LegacyBehaviorRuntime.jump((LivingEntity)(Object)this);}
    @Inject(method="causeFallDamage",at=@At("HEAD"),cancellable=true)
    private void legacyforgebridge$sourceFall(double distance,float multiplier,DamageSource source,CallbackInfoReturnable<Boolean> cir){
        if(LegacyBehaviorRuntime.fall((LivingEntity)(Object)this,distance,multiplier,source))cir.setReturnValue(false);
    }
}
