package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyVariantSnowballItemLaunchTest {
    @Test
    void itemLaunchPreservesCreativeGuardMetadataCarrierServerGateAndVanillaSnowballBallistics()
            throws Exception {
        String resource = "/" + ConvertedLegacyVariantSnowballItem.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream input =
                     ConvertedLegacyVariantSnowballItem.class.getResourceAsStream(resource)) {
            assertNotNull(input);

            boolean[] copyWithCount = {false};
            boolean[] creativeFlag = {false};
            boolean[] shrink = {false};
            boolean[] nextFloat = {false};
            boolean[] snowballSound = {false};
            boolean[] playSound = {false};
            boolean[] spawnFromRotation = {false};
            boolean[] projectileCtor = {false};
            boolean[] power = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (methodName.equals("copyWithCount")
                                    && methodDescriptor.equals("(I)Lnet/minecraft/world/item/ItemStack;")) {
                                copyWithCount[0] = true;
                            }
                            if (methodName.equals("shrink")
                                    && methodDescriptor.equals("(I)V")) shrink[0] = true;
                            if (owner.equals("java/util/Random")
                                    && methodName.equals("nextFloat")) nextFloat[0] = true;
                            if (methodName.equals("playSound")) playSound[0] = true;
                            if (owner.endsWith("/Projectile")
                                    && methodName.equals("spawnProjectileFromRotation")) {
                                spawnFromRotation[0] = true;
                            }
                            if (owner.endsWith("/ConvertedLegacyVariantSnowballProjectile")
                                    && methodName.equals("<init>")
                                    && methodDescriptor.contains("/ItemStack;")) {
                                projectileCtor[0] = true;
                            }
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name,
                                                   String descriptor) {
                            if (owner.endsWith("/Abilities")
                                    && name.equals("instabuild")
                                    && descriptor.equals("Z")) creativeFlag[0] = true;
                            if (owner.endsWith("/SoundEvents")
                                    && name.equals("SNOWBALL_THROW")) snowballSound[0] = true;
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof Float f && Float.compare(f, 1.5F) == 0) {
                                power[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(copyWithCount[0]);
            assertTrue(creativeFlag[0]);
            assertTrue(shrink[0]);
            assertTrue(nextFloat[0]);
            assertTrue(snowballSound[0]);
            assertTrue(playSound[0]);
            assertTrue(spawnFromRotation[0]);
            assertTrue(projectileCtor[0]);
            assertTrue(power[0]);
        }
    }
}
