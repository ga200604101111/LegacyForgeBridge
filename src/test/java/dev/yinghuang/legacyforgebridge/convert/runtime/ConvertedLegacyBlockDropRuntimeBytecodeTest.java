package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyBlockDropRuntimeRegistry;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyBlockDropRuntimeBytecodeTest {
    @Test
    void blockBytecodeWiresProofGatedNormalAndExplosionDrops() throws Exception {
        String resource = "/" + ConvertedLegacyBlock.class.getName().replace('.', '/') + ".class";
        String registryOwner = LegacyBlockDropRuntimeRegistry.class.getName().replace('.', '/');
        try (InputStream input = ConvertedLegacyBlock.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] getDropsRuleLookup = {false};
            boolean[] getDropsCreatesSelfStack = {false};
            boolean[] explosionRuleLookup = {false};
            boolean[] explosionChance = {false};
            boolean[] explosionCreatesSelfStack = {false};
            boolean[] explosionRemovesBlock = {false};
            boolean[] explosionUsesAir = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("getDrops") && !name.equals("onExplosionHit")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && type.equals("net/minecraft/world/item/ItemStack")) {
                                if (name.equals("getDrops")) getDropsCreatesSelfStack[0] = true;
                                if (name.equals("onExplosionHit")) explosionCreatesSelfStack[0] = true;
                            }
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                            if (name.equals("onExplosionHit")
                                    && opcode == Opcodes.GETSTATIC
                                    && owner.equals("net/minecraft/world/level/block/Blocks")
                                    && fieldName.equals("AIR")) {
                                explosionUsesAir[0] = true;
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.equals(registryOwner) && method.equals("hasRule")) {
                                if (name.equals("getDrops")) getDropsRuleLookup[0] = true;
                                if (name.equals("onExplosionHit")) explosionRuleLookup[0] = true;
                            }
                            if (name.equals("onExplosionHit")
                                    && owner.equals(registryOwner)
                                    && method.equals("shouldDropFromExplosion")) {
                                explosionChance[0] = true;
                            }
                            if (name.equals("onExplosionHit") && method.equals("setBlock")) {
                                explosionRemovesBlock[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(getDropsRuleLookup[0]);
            assertTrue(getDropsCreatesSelfStack[0]);
            assertTrue(explosionRuleLookup[0]);
            assertTrue(explosionChance[0]);
            assertTrue(explosionCreatesSelfStack[0]);
            assertTrue(explosionUsesAir[0]);
            assertTrue(explosionRemovesBlock[0]);
        }
    }
}
