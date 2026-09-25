package dev.yinghuang.legacyforgebridge.mixin.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LegacyLocalPlayerSoundMixinBytecodeTest {
    @Test void localPlayerSoundHookPinsExactModernSoundBoundaryAndUsesViaMapping() throws Exception {
        String resource="/dev/yinghuang/legacyforgebridge/mixin/client/LegacyLocalPlayerSoundMixin.class";
        try(InputStream stream=LegacyLocalPlayerSoundMixinBytecodeTest.class.getResourceAsStream(resource)){
            assertNotNull(stream,"Missing compiled LocalPlayer sound mixin");
            byte[] bytes=stream.readAllBytes();
            String pool=new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(pool.contains("net/minecraft/client/player/LocalPlayer"));
            assertTrue(pool.contains("playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"));
            assertTrue(pool.contains("ViaLegacySoundMappings"));
            AtomicBoolean seen=new AtomicBoolean();
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if("legacyforgebridge$sourcePlaySound".equals(name)){
                        seen.set(true);
                        assertEquals("(Lnet/minecraft/sounds/SoundEvent;FFLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",descriptor);
                    }
                    return null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(seen.get());
        }
    }
}
