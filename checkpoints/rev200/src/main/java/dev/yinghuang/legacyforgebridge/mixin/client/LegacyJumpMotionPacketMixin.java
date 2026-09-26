package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.behavior.LegacyClientJumpMotion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No cancellable injection and no change to serverbound packets. All hooks target the 1.21.11 client. */
@Mixin(ClientPacketListener.class)
public abstract class LegacyJumpMotionPacketMixin {
    @Inject(method="handleSetEntityMotion", at=@At("HEAD"))
    private void legacyforgebridge$motionHead(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        LegacyClientJumpMotion.velocityHead(packet);
    }
    @Inject(method="handleSetEntityMotion", at=@At("TAIL"))
    private void legacyforgebridge$motionTail(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        LegacyClientJumpMotion.velocityTail(packet);
    }
    @Inject(method={"handleMovePlayer", "handleRespawn"}, at=@At("HEAD"))
    private void legacyforgebridge$positionBarrier(CallbackInfo ci) {
        LegacyClientJumpMotion.barrier("position-correction-or-respawn");
    }
    @Inject(method={"handleDamageEvent", "handleHurtAnimation", "handleEntityEvent"}, at=@At("HEAD"))
    private void legacyforgebridge$damageBarrier(CallbackInfo ci) {
        LegacyClientJumpMotion.barrier("damage-or-entity-status");
    }
    @Inject(method="handleExplosion", at=@At("HEAD"))
    private void legacyforgebridge$explosionBarrier(CallbackInfo ci) {
        LegacyClientJumpMotion.barrier("explosion");
    }
}
