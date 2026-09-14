package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.compat.LegacyFuelRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves Forge 1.7 IFuelHandler ItemStack predicates that modern FuelValues cannot express. */
@Mixin(FuelValues.class)
public abstract class LegacyFuelValuesMixin {
    @Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
    private void legacyforgebridge$legacyFuelHandler(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Integer legacy = LegacyFuelRegistry.burnDuration(stack);
        if (legacy != null) cir.setReturnValue(legacy);
    }
}
