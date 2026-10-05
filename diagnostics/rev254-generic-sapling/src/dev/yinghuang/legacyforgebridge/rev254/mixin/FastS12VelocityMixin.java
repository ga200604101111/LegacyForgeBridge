package dev.yinghuang.legacyforgebridge.rev254.mixin;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.rev254.FastS12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=class_634.class,remap=false)
public abstract class FastS12VelocityMixin {
 @Inject(method="method_11132(Lnet/minecraft/class_2743;)V",at=@At("RETURN"),remap=false,require=1)
 private void rev254$countHandled(class_2743 packet,CallbackInfo ci){FastS12.velocityHandled();}
}
