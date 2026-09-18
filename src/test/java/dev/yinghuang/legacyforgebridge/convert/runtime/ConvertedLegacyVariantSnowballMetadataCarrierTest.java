package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.compat.LegacyVariantSnowballRuntimeRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyVariantSnowballMetadataCarrierTest {
    @Test
    void launchConstructorCarriesWholeItemStackAndMetadataReadsSyncedStackComponent() throws Exception {
        assertNotNull(ConvertedLegacyVariantSnowballProjectile.class.getConstructor(
                Level.class,
                LivingEntity.class,
                ItemStack.class,
                LegacyVariantSnowballRuntimeRegistry.Rule.class));
        assertNotNull(ConvertedLegacyVariantSnowballProjectile.class.getDeclaredMethod("legacyMetadata"));
        assertNotNull(ConvertedLegacyVariantSnowballProjectile.class.getDeclaredMethod("variant"));

        String resource = "/" + ConvertedLegacyVariantSnowballProjectile.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream input =
                     ConvertedLegacyVariantSnowballProjectile.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] stackCtor = {false};
            boolean[] getItem = {false};
            boolean[] metadataGet = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.equals(ThrowableItemProjectile.class.getName().replace('.', '/'))
                                    && methodName.equals("<init>")
                                    && methodDescriptor.contains("Lnet/minecraft/world/item/ItemStack;")) {
                                stackCtor[0] = true;
                            }
                            if (owner.equals(ThrowableItemProjectile.class.getName().replace('.', '/'))
                                    && methodName.equals("getItem")) {
                                getItem[0] = true;
                            }
                            if (owner.equals(LegacyStackComponents.class.getName().replace('.', '/'))
                                    && methodName.equals("get")) {
                                metadataGet[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(stackCtor[0]);
            assertTrue(getItem[0]);
            assertTrue(metadataGet[0]);
        }
    }
}
