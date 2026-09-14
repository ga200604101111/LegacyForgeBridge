package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.compat.LegacyJsonTranslationAliaser;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Preserves selected 1.7.10 translation keys at the first ViaLegacy text boundary.
 *
 * <p>ViaLegacy's 1.7.10 -> 1.8 TextRewriter contains an English translation table for legacy
 * keys such as command usages. Once that table replaces a key, downstream ViaVersion stages can
 * no longer recover the original locale-independent key. LFB therefore aliases only selected
 * message/presentation keys before ViaLegacy parses the component. ViaLegacy still performs all
 * of its normal component and hover-item conversions.</p>
 */
@Mixin(
        targets = "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.rewriter.TextRewriter",
        remap = false
)
public abstract class ViaLegacyTextRewriterMixin {
    @ModifyVariable(
            method = "toClient(Lcom/viaversion/viaversion/api/connection/UserConnection;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private String legacyforgebridge$aliasLegacyMessageKeys(String text) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return text;
        }
        return LegacyJsonTranslationAliaser.aliasPreservedTranslations(text);
    }
}
