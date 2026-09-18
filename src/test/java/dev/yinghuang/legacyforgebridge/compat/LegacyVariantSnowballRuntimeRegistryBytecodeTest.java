package dev.yinghuang.legacyforgebridge.compat;

import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVariantSnowballProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVariantSnowballRuntimeRegistryBytecodeTest {
    @Test
    void registryMaterializesDormantProjectileEntityTypeWithProvenTrackingSettings() throws Exception {
        assertTrue(ThrowableItemProjectile.class.isAssignableFrom(
                ConvertedLegacyVariantSnowballProjectile.class));

        String resource = "/" + LegacyVariantSnowballRuntimeRegistry.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream input =
                     LegacyVariantSnowballRuntimeRegistry.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] builder = {false};
            boolean[] sized = {false};
            boolean[] tracking = {false};
            boolean[] interval = {false};
            boolean[] register = {false};
            boolean[] projectileCtor = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.endsWith("/EntityType$Builder") && methodName.equals("of")) builder[0] = true;
                            if (owner.endsWith("/EntityType$Builder") && methodName.equals("sized")) sized[0] = true;
                            if (owner.endsWith("/EntityType$Builder") && methodName.equals("clientTrackingRange")) tracking[0] = true;
                            if (owner.endsWith("/EntityType$Builder") && methodName.equals("updateInterval")) interval[0] = true;
                            if (owner.equals("net/minecraft/core/Registry") && methodName.equals("register")) register[0] = true;
                            if (owner.endsWith("/ConvertedLegacyVariantSnowballProjectile")
                                    && methodName.equals("<init>")) projectileCtor[0] = true;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(builder[0]);
            assertTrue(sized[0]);
            assertTrue(tracking[0]);
            assertTrue(interval[0]);
            assertTrue(register[0]);
            assertTrue(projectileCtor[0]);
        }
    }
}
