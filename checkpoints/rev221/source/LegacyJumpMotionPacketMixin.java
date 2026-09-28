package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.behavior.LegacyClientJumpMotion;
import dev.yinghuang.legacyforgebridge.behavior.LegacyJumpEchoReconciler;
import net.minecraft.class_2743;
import net.minecraft.class_634;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=class_634.class, remap=false)
public abstract class LegacyJumpMotionPacketMixin {
    @Inject(method="method_11132",at=@At("HEAD"),cancellable=true,remap=false)
    private void legacyforgebridge$motionHead(class_2743 packet,CallbackInfo ci){
        if(LegacyJumpEchoReconciler.consumeIfPredictedEcho(packet)){ci.cancel();return;}
        LegacyClientJumpMotion.velocityHead(packet);
    }
    @Inject(method="method_11132",at=@At("TAIL"),remap=false)
    private void legacyforgebridge$motionTail(class_2743 packet,CallbackInfo ci){LegacyClientJumpMotion.velocityTail(packet);}
    @Inject(method={"method_11157","method_11117"},at=@At("HEAD"),remap=false)
    private void legacyforgebridge$positionBarrier(CallbackInfo ci){LegacyJumpEchoReconciler.clear("position-correction-or-respawn");LegacyClientJumpMotion.barrier("position-correction-or-respawn");}
    @Inject(method={"method_49034","method_48295","method_11148"},at=@At("HEAD"),remap=false)
    private void legacyforgebridge$damageBarrier(CallbackInfo ci){LegacyJumpEchoReconciler.clear("damage-or-entity-status");LegacyClientJumpMotion.barrier("damage-or-entity-status");}
    @Inject(method="method_11124",at=@At("HEAD"),remap=false)
    private void legacyforgebridge$explosionBarrier(CallbackInfo ci){LegacyJumpEchoReconciler.clear("explosion");LegacyClientJumpMotion.barrier("explosion");}
}
