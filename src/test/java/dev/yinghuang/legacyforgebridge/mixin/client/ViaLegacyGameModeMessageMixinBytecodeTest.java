package dev.yinghuang.legacyforgebridge.mixin.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViaLegacyGameModeMessageMixinBytecodeTest {
    @Test
    void syntheticGameModeHookUsesPacketSemanticsInsteadOfEnglishReverseMatching() throws Exception {
        String resource = "/dev/yinghuang/legacyforgebridge/mixin/client/ViaLegacyGameModeMessageMixin.class";
        try (InputStream stream = ViaLegacyGameModeMessageMixinBytecodeTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing compiled ViaLegacy game-mode mixin class");
            byte[] bytes = stream.readAllBytes();

            String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(
                    constantPool.contains(
                            "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.Protocolr1_7_6_10Tor1_8$1"
                    ),
                    "Mixin must target ViaLegacy's first LEGACY_TO_JSON transformer"
            );
            assertTrue(
                    constantPool.contains(
                            "transform(Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;Ljava/lang/String;)Ljava/lang/String;"
                    ),
                    "Mixin must pin the exact ViaLegacy transformer String boundary"
            );
            assertTrue(constantPool.contains("gameMode.changed"));
            assertTrue(constantPool.contains("CHAT"));
            assertFalse(
                    constantPool.contains("Your game mode has been updated"),
                    "LFB must not reverse-match ViaLegacy's English synthetic message"
            );

            AtomicBoolean handlerSeen = new AtomicBoolean(false);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if ("legacyforgebridge$restoreLegacyGameModeChangedMessage".equals(name)) {
                        handlerSeen.set(true);
                        assertEquals(
                                "(Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
                                descriptor,
                                "Game-mode handler must depend only on Via's public PacketWrapper API"
                        );
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(handlerSeen.get(), "ViaLegacy game-mode handler must be compiled");
        }
    }
}
