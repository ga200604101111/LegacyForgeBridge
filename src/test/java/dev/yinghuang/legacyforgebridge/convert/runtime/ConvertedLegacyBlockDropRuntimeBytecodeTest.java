package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyBlockDropRuntimeRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
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
    void blockBytecodeWiresProofGatedNormalExplosionAndMetadataMappedDrops() throws Exception {
        String resource = "/" + ConvertedLegacyBlock.class.getName().replace('.', '/') + ".class";
        String blockOwner = ConvertedLegacyBlock.class.getName().replace('.', '/');
        String registryOwner = LegacyBlockDropRuntimeRegistry.class.getName().replace('.', '/');
        String ruleOwner = LegacyBlockDropRuntimeRegistry.Rule.class.getName().replace('.', '/');
        String componentsOwner = LegacyStackComponents.class.getName().replace('.', '/');
        try (InputStream input = ConvertedLegacyBlock.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] getDropsRuleLookup = {false};
            boolean[] getDropsCallsStackHelper = {false};
            boolean[] explosionRuleLookup = {false};
            boolean[] explosionChance = {false};
            boolean[] explosionCallsStackHelper = {false};
            boolean[] explosionRemovesBlock = {false};
            boolean[] explosionUsesAir = {false};
            boolean[] helperCreatesSelfStack = {false};
            boolean[] helperReadsLegacyDamage = {false};
            boolean[] helperWritesLegacyComponent = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("getDrops") && !name.equals("onExplosionHit") && !name.equals("legacyDropStack")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (name.equals("legacyDropStack") && opcode == Opcodes.NEW
                                    && type.equals("net/minecraft/world/item/ItemStack")) {
                                helperCreatesSelfStack[0] = true;
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
                            if (owner.equals(registryOwner) && method.equals("rule")) {
                                if (name.equals("getDrops")) getDropsRuleLookup[0] = true;
                                if (name.equals("onExplosionHit")) explosionRuleLookup[0] = true;
                            }
                            if (owner.equals(blockOwner) && method.equals("legacyDropStack")) {
                                if (name.equals("getDrops")) getDropsCallsStackHelper[0] = true;
                                if (name.equals("onExplosionHit")) explosionCallsStackHelper[0] = true;
                            }
                            if (name.equals("onExplosionHit")
                                    && owner.equals(registryOwner)
                                    && method.equals("shouldDropFromExplosion")) {
                                explosionChance[0] = true;
                            }
                            if (name.equals("onExplosionHit") && method.equals("setBlock")) {
                                explosionRemovesBlock[0] = true;
                            }
                            if (name.equals("legacyDropStack") && owner.equals(ruleOwner) && method.equals("legacyDamage")) {
                                helperReadsLegacyDamage[0] = true;
                            }
                            if (name.equals("legacyDropStack") && owner.equals(componentsOwner) && method.equals("set")) {
                                helperWritesLegacyComponent[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(getDropsRuleLookup[0]);
            assertTrue(getDropsCallsStackHelper[0]);
            assertTrue(explosionRuleLookup[0]);
            assertTrue(explosionChance[0]);
            assertTrue(explosionCallsStackHelper[0]);
            assertTrue(explosionUsesAir[0]);
            assertTrue(explosionRemovesBlock[0]);
            assertTrue(helperCreatesSelfStack[0]);
            assertTrue(helperReadsLegacyDamage[0]);
            assertTrue(helperWritesLegacyComponent[0]);
        }
    }
}
