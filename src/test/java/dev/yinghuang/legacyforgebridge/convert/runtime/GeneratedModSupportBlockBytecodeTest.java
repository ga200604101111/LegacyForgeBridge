package dev.longyu.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModSupportBlockBytecodeTest {
    @Test void generatedBlockRegistrationUsesOpaqueLegacyMetadataCarrier() throws Exception {
        String resource = "/" + GeneratedModSupport.class.getName().replace('.', '/') + ".class";
        try (InputStream input = GeneratedModSupport.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] convertedBlockAllocation = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("registerBlock")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && type.endsWith("/ConvertedLegacyBlock")) {
                                convertedBlockAllocation[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(convertedBlockAllocation[0]);
        }
    }
}
