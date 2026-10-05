package dev.yinghuang.legacyforgebridge.rev254.mixin;
import net.minecraft.class_310;
import dev.yinghuang.legacyforgebridge.rev254.FastS12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=class_310.class,remap=false)
public abstract class FastS12ClientMixin {
 @Inject(method="method_1574()V",at=@At("HEAD"),remap=false,require=1)
 private void rev254$beforePhysics(CallbackInfo ci){FastS12.beforeTick((class_310)(Object)this);}
}
