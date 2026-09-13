package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.compat.LegacyTranslationBridge;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Resolves preserved 1.7.10 keys from the LFB language catalogue only during legacy sessions. */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin {
    @Redirect(
            method = "decompose",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"
            )
    )
    private String legacyforgebridge$resolveLegacyKey(Language language, String key) {
        return LegacyTranslationBridge.getOrDefault(language, key);
    }

    @Redirect(
            method = "decompose",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
            )
    )
    private String legacyforgebridge$resolveLegacyKeyWithFallback(
            Language language,
            String key,
            String fallback
    ) {
        return LegacyTranslationBridge.getOrDefault(language, key, fallback);
    }
}
