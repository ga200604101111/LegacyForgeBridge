package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import dev.yinghuang.legacyforgebridge.protocol.ViaLegacySoundMappings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LocalPlayer.class)
public abstract class LegacyLocalPlayerSoundMixin {
    @Unique private static final ThreadLocal<Boolean> legacyforgebridge$soundReentry = ThreadLocal.withInitial(() -> false);

    @Inject(method="playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",at=@At("HEAD"),cancellable=true)
    private void legacyforgebridge$sourcePlaySound(SoundEvent sound,float volume,float pitch,CallbackInfo ci) {
        if(Boolean.TRUE.equals(legacyforgebridge$soundReentry.get())
                || !ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()
                || LegacyBehaviorRegistry.events("sound").isEmpty()) return;
        Identifier modernId=BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if(modernId==null)return;
        var path=ViaLegacySoundMappings.toLegacy(modernId.toString()).orElse(null);
        if(path==null)return;
        var outcome=LegacyBehaviorRuntime.soundEvent((LocalPlayer)(Object)this,path.legacyName(),volume,pitch);
        if(outcome.canceled()){ci.cancel();return;}
        if(Objects.equals(outcome.name(),path.legacyName()))return;
        String mapped=path.toModern(outcome.name()).orElse(null);
        if(mapped==null)return;
        final Identifier replacementId;
        try{replacementId=Identifier.parse(mapped);}catch(IllegalArgumentException invalid){return;}
        if(!BuiltInRegistries.SOUND_EVENT.containsKey(replacementId))return;
        SoundEvent replacement=BuiltInRegistries.SOUND_EVENT.getValue(replacementId);
        if(replacement==null||replacement==sound)return;
        legacyforgebridge$soundReentry.set(true);
        try{((LocalPlayer)(Object)this).playSound(replacement,volume,pitch);}
        finally{legacyforgebridge$soundReentry.remove();}
        ci.cancel();
    }
}
