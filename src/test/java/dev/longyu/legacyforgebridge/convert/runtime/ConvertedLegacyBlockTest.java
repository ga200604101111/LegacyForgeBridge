package dev.longyu.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyBlockTest {
    @Test void raw1710MetadataPropertyIsExactlyZeroThroughFifteen() {
        assertEquals("legacy_meta", ConvertedLegacyBlock.LEGACY_META.getName());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
                ConvertedLegacyBlock.LEGACY_META.getPossibleValues());
        for (int meta = 0; meta < 16; meta++) assertEquals(meta, ConvertedLegacyBlock.validateLegacyMeta(meta));
        assertThrows(IllegalArgumentException.class, () -> ConvertedLegacyBlock.validateLegacyMeta(-1));
        assertThrows(IllegalArgumentException.class, () -> ConvertedLegacyBlock.validateLegacyMeta(16));
    }

    @Test void blockBytecodeAddsLegacyPropertyAndRegistersDefaultStateWithoutConstructingFrozenRegistryEntries() throws Exception {
        String resource = "/" + ConvertedLegacyBlock.class.getName().replace('.', '/') + ".class";
        try (InputStream input = ConvertedLegacyBlock.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] field = {false};
            boolean[] added = {false};
            boolean[] defaultState = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (name.equals("LEGACY_META")) field[0] = true;
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                                    String methodDescriptor, boolean isInterface) {
                            if (name.equals("createBlockStateDefinition") && method.equals("add")
                                    && owner.equals("net/minecraft/world/level/block/state/StateDefinition$Builder")) {
                                added[0] = true;
                            }
                            if (name.equals("<init>") && method.equals("registerDefaultState")) {
                                defaultState[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(field[0]);
            assertTrue(added[0]);
            assertTrue(defaultState[0]);
        }
    }
}
