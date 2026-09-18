package dev.yinghuang.legacyforgebridge.render;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedVariantSnowballPresentationRuntimeTest {
    @Test
    void clientRuntimeRegistersVanillaThrownItemRenderer() throws Exception {
        String resource = "/" + ConvertedVariantSnowballPresentationRuntime.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream input =
                     ConvertedVariantSnowballPresentationRuntime.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] registryCall = {false};
            boolean[] thrownRenderer = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.endsWith("/EntityRendererRegistry")
                                    && methodName.equals("register")) {
                                registryCall[0] = true;
                            }
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor,
                                                           Handle bootstrapMethodHandle,
                                                           Object... bootstrapMethodArguments) {
                            for (Object argument : bootstrapMethodArguments) {
                                if (argument instanceof Handle handle
                                        && handle.getOwner().endsWith("/ThrownItemRenderer")
                                        && handle.getName().equals("<init>")) {
                                    thrownRenderer[0] = true;
                                }
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(registryCall[0]);
            assertTrue(thrownRenderer[0]);
        }
    }
}
