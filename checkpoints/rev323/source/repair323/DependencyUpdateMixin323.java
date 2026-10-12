
package dev.yinghuang.legacyforgebridge.repair322.mixin;
import dev.yinghuang.legacyforgebridge.repair322.FeatureClient323;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;import org.spongepowered.asm.mixin.injection.Inject;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import net.minecraft.*;
@Mixin(value=class_638.class,remap=false)
public class DependencyUpdateMixin323 {
 @Inject(method="method_8413(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_2680;I)V",at=@At("TAIL"),require=1,remap=false)
 private void lfb$gridUpdate(class_2338 pos,class_2680 oldState,class_2680 newState,int flags,CallbackInfo ci){FeatureClient323.changed((class_638)(Object)this,pos,oldState,newState);}
 @Inject(method="method_16109(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_2680;)V",at=@At("TAIL"),require=1,remap=false)
 private void lfb$gridRerender(class_2338 pos,class_2680 oldState,class_2680 newState,CallbackInfo ci){FeatureClient323.changed((class_638)(Object)this,pos,oldState,newState);}
}
