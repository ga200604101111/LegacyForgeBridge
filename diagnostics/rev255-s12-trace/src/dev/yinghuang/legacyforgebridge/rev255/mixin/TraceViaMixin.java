package dev.yinghuang.legacyforgebridge.rev255.mixin;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import org.spongepowered.asm.mixin.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.yinghuang.legacyforgebridge.rev255.S12Trace;
@Pseudo
@Mixin(targets="com.viaversion.viaversion.connection.UserConnectionImpl",remap=false)
public abstract class TraceViaMixin {
 @WrapMethod(method="transformClientbound(Lio/netty/buffer/ByteBuf;Ljava/util/function/Function;)V",remap=false,require=0)
 private void rev255$measureVia(ByteBuf buffer,Function<?,?> cancel,Operation<Void> original){
  S12Trace.ViaStamp token=null;try{token=S12Trace.beginVia(this,buffer);}catch(Throwable e){S12Trace.observerError();}
  boolean ok=false;try{original.call(buffer,cancel);ok=true;}
  finally{try{S12Trace.finishVia(token,ok);}catch(Throwable e){S12Trace.observerError();}}
 }
}
