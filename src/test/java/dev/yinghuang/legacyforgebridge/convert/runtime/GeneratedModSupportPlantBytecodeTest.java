package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyPlantPlacementRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyPlantRuntimeRegistry;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModSupportPlantBytecodeTest {
    @Test void generatedRegistrationLoadsPlantProofsAndAllocatesSpecializedCropAndSeedRuntimes() throws Exception {
        String resource = "/" + GeneratedModSupport.class.getName().replace('.', '/') + ".class";
        try (InputStream input = GeneratedModSupport.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] plantRegistryLoad = {false};
            boolean[] placementRegistryLoad = {false};
            boolean[] plantBlockAllocation = {false};
            boolean[] plantingItemAllocation = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if ("beginMod".equals(name) && opcode == Opcodes.INVOKESTATIC && "loadMod".equals(methodName)) {
                                if (owner.equals(LegacyPlantRuntimeRegistry.class.getName().replace('.', '/'))) plantRegistryLoad[0] = true;
                                if (owner.equals(LegacyPlantPlacementRegistry.class.getName().replace('.', '/'))) placementRegistryLoad[0] = true;
                            }
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode != Opcodes.NEW) return;
                            if ("registerBlock".equals(name) && type.endsWith("/ConvertedLegacyPlantBlock")) plantBlockAllocation[0] = true;
                            if ("registerItem".equals(name) && type.endsWith("/ConvertedLegacyPlantingItem")) plantingItemAllocation[0] = true;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(plantRegistryLoad[0]);
            assertTrue(placementRegistryLoad[0]);
            assertTrue(plantBlockAllocation[0]);
            assertTrue(plantingItemAllocation[0]);
        }
    }
}
