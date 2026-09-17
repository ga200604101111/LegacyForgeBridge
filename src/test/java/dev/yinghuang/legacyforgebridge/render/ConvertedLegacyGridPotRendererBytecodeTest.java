package dev.yinghuang.legacyforgebridge.render;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyGridPotRendererBytecodeTest {
    @Test
    void rendererUsesModernItemModelExtractionBoundingBoxPlacementAndSubmitPipeline() throws Exception {
        String resource = "/" + ConvertedLegacyGridPotRenderer.class.getName().replace('.', '/') + ".class";
        try (InputStream input = ConvertedLegacyGridPotRenderer.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] update={false}, contextNone={false}, bounds={false}, submit={false}, translate={false}, scale={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!"extractRenderState".equals(name) && !"submit".equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                            if (opcode == Opcodes.GETSTATIC && "net/minecraft/world/item/ItemDisplayContext".equals(owner)
                                    && "NONE".equals(fieldName)) contextNone[0] = true;
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                            if ("net/minecraft/client/renderer/item/ItemModelResolver".equals(owner)
                                    && "updateForTopItem".equals(methodName)) update[0] = true;
                            if ("net/minecraft/client/renderer/item/ItemStackRenderState".equals(owner)
                                    && "getModelBoundingBox".equals(methodName)) bounds[0] = true;
                            if ("net/minecraft/client/renderer/item/ItemStackRenderState".equals(owner)
                                    && "submit".equals(methodName)) submit[0] = true;
                            if ("com/mojang/blaze3d/vertex/PoseStack".equals(owner) && "translate".equals(methodName)) translate[0] = true;
                            if ("com/mojang/blaze3d/vertex/PoseStack".equals(owner) && "scale".equals(methodName)) scale[0] = true;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(update[0], "GridPot renderer must resolve ItemStack models during extract");
            assertTrue(contextNone[0], "GridPot renderer must use the admitted NONE item display context");
            assertTrue(bounds[0], "GridPot renderer must position contents from modern model bounds");
            assertTrue(submit[0], "GridPot renderer must submit ItemStackRenderState into the 1.21 renderer pipeline");
            assertTrue(translate[0] && scale[0], "GridPot renderer must apply source-proven cell transforms");
        }
    }
}
