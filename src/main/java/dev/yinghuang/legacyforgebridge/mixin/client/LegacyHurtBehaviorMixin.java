package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.sugar.Local;

/** Native pre-armor boundary shared by players and other living entities. Logical server only. */
@Mixin(LivingEntity.class)
public abstract class LegacyHurtBehaviorMixin {
    @ModifyVariable(method="getDamageAfterArmorAbsorb",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float legacyforgebridge$sourceHurt(float amount,@Local(argsOnly=true) DamageSource source){
        return LegacyBehaviorRuntime.hurt((LivingEntity)(Object)this,source,amount);
    }
}
