package dev.yinghuang.legacyforgebridge.render;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacySeatBedRendererBytecodeTest {
    @Test
    void rendererKeepsProvenTwoTextureModelPartAndExpandedCullingWiring() throws Exception {
        String resource = "/" + ConvertedLegacySeatBedRenderer.class.getName().replace('.', '/') + ".class";
        try (InputStream input = ConvertedLegacySeatBedRenderer.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] cube = {false};
            boolean[] textureFactory = {false};
            boolean[] queue = {false};
            boolean[] offscreen = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("method_3563".equals(name) && "()Z".equals(descriptor)) offscreen[0] = true;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && type.endsWith("class_630$class_628")) cube[0] = true;
                        }
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                            if (owner.endsWith("/class_12249") && "method_75990".equals(methodName)) textureFactory[0] = true;
                            if (owner.endsWith("/class_11659") && "method_73492".equals(methodName)) queue[0] = true;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(cube[0], "renderer must materialize proven ModelPart cubes");
            assertTrue(textureFactory[0], "renderer must use the proven two-texture render type path");
            assertTrue(queue[0], "renderer must submit the model parts to the modern render queue");
            assertTrue(offscreen[0], "source expanded render bounds must survive as conservative off-screen rendering");
        }
    }
}
