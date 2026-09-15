package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import dev.yinghuang.legacyforgebridge.compat.LegacyJsonTranslationAliaser;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restores the original 1.7.10 game-mode-change presentation message without reverse-matching
 * ViaLegacy's final English text.
 *
 * <p>ViaLegacy's 1.7.10 -> 1.8 GAME_EVENT handler synthesizes a clientbound CHAT packet for
 * reason 3 and feeds a literal English sentence through the first {@code LEGACY_TO_JSON}
 * transformer. In the current ViaLegacy runtime that transformer is the first anonymous class of
 * {@code Protocolr1_7_6_10Tor1_8}. Its only clientbound CHAT use in this protocol path is that
 * synthetic game-mode message; the other uses are window/tab-list presentation text.</p>
 *
 * <p>LFB therefore identifies the semantic context by packet type and replaces the transformer's
 * output with the original 1.7.10 {@code gameMode.changed} translation alias. The handler never
 * inspects or matches the English sentence itself.</p>
 */
@Mixin(
        targets = "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.Protocolr1_7_6_10Tor1_8$1",
        remap = false
)
public abstract class ViaLegacyGameModeMessageMixin {
    private static final String LEGACY_GAME_MODE_CHANGED_KEY = "gameMode.changed";

    @Inject(
            method = "transform(Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void legacyforgebridge$restoreLegacyGameModeChangedMessage(
            PacketWrapper wrapper,
            String message,
            CallbackInfoReturnable<String> cir
    ) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return;
        }

        PacketType packetType = wrapper.getPacketType();
        if (packetType == null
                || packetType.direction() != Direction.CLIENTBOUND
                || !"CHAT".equals(packetType.getName())) {
            return;
        }

        cir.setReturnValue(
                LegacyJsonTranslationAliaser.translationComponentForPreservedKey(
                        LEGACY_GAME_MODE_CHANGED_KEY
                )
        );
    }
}
