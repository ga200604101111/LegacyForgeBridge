package dev.longyu.legacyforgebridge.mixin.client;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** Access to the authenticated profile Minecraft resolves during client startup. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("profileFuture")
    CompletableFuture<ProfileResult> legacyforgebridge$getProfileFuture();
}
