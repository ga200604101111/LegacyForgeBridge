package dev.longyu.legacyforgebridge.convert.runtime;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyBlockPlacementTest {
    @Test void generatedBlockPinsIdentifierAndDelegatesPlacementToLegacyRegistry() throws Exception {
        assertNotNull(ConvertedLegacyBlock.class.getConstructor(Identifier.class, BlockBehaviour.Properties.class));
        assertNotNull(ConvertedLegacyBlock.class.getDeclaredMethod("getStateForPlacement", BlockPlaceContext.class));

        String resource = "/" + ConvertedLegacyBlock.class.getName().replace('.', '/') + ".class";
        try (InputStream input = ConvertedLegacyBlock.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] placementCall = {false};
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!"getStateForPlacement".equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.endsWith("/LegacyBlockPlacementRegistry") && name.equals("placementMeta")) {
                                placementCall[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(placementCall[0]);
        }
    }
}
