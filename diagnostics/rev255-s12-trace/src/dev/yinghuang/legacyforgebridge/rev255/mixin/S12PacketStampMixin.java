package dev.yinghuang.legacyforgebridge.rev255.mixin;
import net.minecraft.class_2743;
import org.spongepowered.asm.mixin.*;
import dev.yinghuang.legacyforgebridge.rev255.*;
@Mixin(value=class_2743.class,remap=false)
public abstract class S12PacketStampMixin implements S12PacketAccess {
 @Unique private volatile TraceStore.Stamp rev255$stamp;
 @Override public TraceStore.Stamp rev255$getStamp(){return rev255$stamp;}
 @Override public void rev255$setStamp(TraceStore.Stamp stamp){rev255$stamp=stamp;}
}
