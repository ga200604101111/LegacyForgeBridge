package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModSupportVariantSnowballBytecodeTest {
    @Test
    void beginModLoadsVariantSnowballRulesBeforeGeneratedItemRegistrationPhase() throws Exception {
        String resource = "/" + GeneratedModSupport.class.getName().replace('.', '/') + ".class";
        try (InputStream input = GeneratedModSupport.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] load = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("beginMod") || !descriptor.equals("(Ljava/lang/String;)V")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.endsWith("/LegacyVariantSnowballRuntimeRegistry")
                                    && methodName.equals("loadMod")
                                    && methodDescriptor.equals("(Ljava/lang/String;)V")) {
                                load[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(load[0]);
        }
    }
}
