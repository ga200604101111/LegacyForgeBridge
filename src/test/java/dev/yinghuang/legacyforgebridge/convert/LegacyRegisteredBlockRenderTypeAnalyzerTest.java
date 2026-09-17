package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRegisteredBlockRenderTypeAnalyzerTest {
    @Test
    void directConstantRenderTypeIsProven() {
        MethodNode method = renderMethod();
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 13));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        var identity = LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(method);
        assertNotNull(identity);
        assertTrue(identity.isConstant(13));
        assertNull(identity.fieldOwner());
    }

    @Test
    void directStaticFieldRenderTypeIsPreservedSymbolically() {
        MethodNode method = renderMethod();
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "foreign/render/Ids", "cross", "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        var identity = LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(method);
        assertNotNull(identity);
        assertNull(identity.constant());
        assertEquals("foreign/render/Ids", identity.fieldOwner());
        assertEquals("cross", identity.fieldName());
    }

    @Test
    void dynamicOrMixedReturnsFailClosed() {
        MethodNode dynamic = renderMethod();
        dynamic.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        dynamic.instructions.add(new InsnNode(Opcodes.IRETURN));
        assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(dynamic));

        MethodNode mixed = renderMethod();
        mixed.instructions.add(new InsnNode(Opcodes.ICONST_1));
        mixed.instructions.add(new InsnNode(Opcodes.IRETURN));
        mixed.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 13));
        mixed.instructions.add(new InsnNode(Opcodes.IRETURN));
        assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(mixed));
    }

    private static MethodNode renderMethod() {
        return new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "func_149645_b", "()I", null, null);
    }
}
