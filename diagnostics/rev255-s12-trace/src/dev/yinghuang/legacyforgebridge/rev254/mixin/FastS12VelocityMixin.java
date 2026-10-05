package dev.yinghuang.legacyforgebridge.rev254.mixin;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.rev254.FastS12;
import dev.yinghuang.legacyforgebridge.rev255.S12Trace;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(value=class_634.class,remap=false)
public abstract class FastS12VelocityMixin {
 @WrapMethod(method="method_11132(Lnet/minecraft/class_2743;)V",remap=false,require=1)
 private void rev255$observeHandler(class_2743 packet,Operation<Void> original){
  S12Trace.Apply token=null;try{token=S12Trace.beginApply(this,packet);}catch(Throwable e){S12Trace.observerError();}
  boolean ok=false;
  try{original.call(packet);ok=true;FastS12.velocityHandled();}
  finally{try{S12Trace.finishApply(token,ok);}catch(Throwable e){S12Trace.observerError();}}
 }
}
