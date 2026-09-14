package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.compat.LegacyFuelRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFuelValuesMixinBytecodeTest {
    @Test void exact12111BurnDurationTargetExistsAndMixinConsultsLegacyRegistry() throws Exception {
        assertNotNull(FuelValues.class.getDeclaredMethod("burnDuration", ItemStack.class));
        String resource = "/" + LegacyFuelValuesMixin.class.getName().replace('.', '/') + ".class";
        try (InputStream input = LegacyFuelValuesMixin.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] runtimeCall = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            if (owner.equals(LegacyFuelRegistry.class.getName().replace('.', '/'))
                                    && name.equals("burnDuration")
                                    && descriptor.equals("(Lnet/minecraft/world/item/ItemStack;)Ljava/lang/Integer;")) {
                                runtimeCall[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(runtimeCall[0]);
        }
    }
}
