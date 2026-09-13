package dev.longyu.legacyforgebridge.mixin.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViaLegacyTextRewriterMixinBytecodeTest {
    @Test
    void earlyAliasMixinTargetsTheStringBoundaryBeforeViaLegacyHardcodesEnglish() throws Exception {
        String resource = "/dev/longyu/legacyforgebridge/mixin/client/ViaLegacyTextRewriterMixin.class";
        try (InputStream stream = ViaLegacyTextRewriterMixinBytecodeTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing compiled ViaLegacy mixin class");
            byte[] bytes = stream.readAllBytes();

            String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(
                    constantPool.contains(
                            "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.rewriter.TextRewriter"
                    ),
                    "Mixin must target ViaLegacy's 1.7.10 -> 1.8 TextRewriter"
            );
            assertTrue(
                    constantPool.contains(
                            "toClient(Lcom/viaversion/viaversion/api/connection/UserConnection;Ljava/lang/String;)Ljava/lang/String;"
                    ),
                    "Mixin must pin the exact ViaLegacy toClient String boundary"
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
                    if ("legacyforgebridge$aliasLegacyMessageKeys".equals(name)) {
                        handlerSeen.set(true);
                        assertEquals(
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                descriptor,
                                "Early alias handler must stay independent of ViaLegacy implementation classes"
                        );
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(handlerSeen.get(), "Early ViaLegacy alias handler must be compiled");
        }
    }
}
