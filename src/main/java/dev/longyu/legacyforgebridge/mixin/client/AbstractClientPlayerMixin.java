package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.compat.LegacyLocalPlayerSkinBridge;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the authenticated-profile fallback only to the local player in legacy sessions. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void legacyforgebridge$replaceIncompleteLegacyLocalSkin(
            CallbackInfoReturnable<PlayerSkin> cir
    ) {
        PlayerSkin replacement = LegacyLocalPlayerSkinBridge.INSTANCE
                .replacementFor((AbstractClientPlayer) (Object) this);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
