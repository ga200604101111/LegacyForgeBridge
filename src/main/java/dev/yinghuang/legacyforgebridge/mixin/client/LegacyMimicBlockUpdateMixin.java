package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.render.LegacyMimicInvalidationClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe presentation changes without cancelling vanilla behavior or replaying gameplay. */
@Mixin(ClientLevel.class)
public abstract class LegacyMimicBlockUpdateMixin {
    @Inject(method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("TAIL"))
    private void legacyforgebridge$mimicNotified(BlockPos pos, BlockState oldState, BlockState newState,
                                                int flags, CallbackInfo ci) {
        LegacyMimicInvalidationClient.blockChanged((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "setBlocksDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private void legacyforgebridge$mimicChanged(BlockPos pos, BlockState oldState, BlockState newState,
                                               CallbackInfo ci) {
        LegacyMimicInvalidationClient.blockChanged((ClientLevel) (Object) this, pos);
    }
}
