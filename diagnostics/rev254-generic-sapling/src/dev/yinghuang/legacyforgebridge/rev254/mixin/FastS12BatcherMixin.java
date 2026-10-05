package dev.yinghuang.legacyforgebridge.rev254.mixin;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.rev254.FastS12Access;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=class_11980.class,remap=false)
public abstract class FastS12BatcherMixin implements FastS12Access {
 @Unique private final AtomicBoolean rev254$pendingVelocity=new AtomicBoolean();
 @Inject(method="method_74448(Lnet/minecraft/class_2547;Lnet/minecraft/class_2596;)V",at=@At("RETURN"),remap=false,require=1)
 private void rev254$afterEnqueue(class_2547 listener,class_2596<?> packet,CallbackInfo ci){if(packet instanceof class_2743 v && dev.yinghuang.legacyforgebridge.rev254.FastS12.shouldSignal(listener,v.method_11818()))rev254$pendingVelocity.set(true);}
 @Override public boolean rev254$takeVelocitySignal(){return rev254$pendingVelocity.getAndSet(false);}
}
