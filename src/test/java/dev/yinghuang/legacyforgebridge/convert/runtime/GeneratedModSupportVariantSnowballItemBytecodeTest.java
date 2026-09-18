package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModSupportVariantSnowballItemBytecodeTest {
    @Test
    void generatedItemRegistrationSelectsVariantSnowballByInstalledRuntimeRule() throws Exception {
        String resource = "/" + GeneratedModSupport.class.getName().replace('.', '/') + ".class";
        try (InputStream input = GeneratedModSupport.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] lookup = {false};
            boolean[] specialized = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("registerItem")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.endsWith("/LegacyVariantSnowballRuntimeRegistry")
                                    && methodName.equals("rule")) lookup[0] = true;
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW
                                    && type.endsWith("/ConvertedLegacyVariantSnowballItem")) {
                                specialized[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(lookup[0]);
            assertTrue(specialized[0]);
        }
    }
}
