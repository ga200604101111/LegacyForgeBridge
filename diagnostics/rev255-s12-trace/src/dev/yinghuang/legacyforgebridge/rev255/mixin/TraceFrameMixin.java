package dev.yinghuang.legacyforgebridge.rev255.mixin;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.yinghuang.legacyforgebridge.rev255.S12Trace;
@Mixin(value=class_310.class,remap=false)
public abstract class TraceFrameMixin {
 @Inject(method="method_1523(Z)V",at=@At("HEAD"),remap=false,require=1)
 private void rev255$frame(boolean tick,CallbackInfo ci){try{S12Trace.frame((class_310)(Object)this);}catch(Throwable e){S12Trace.observerError();}}
}
