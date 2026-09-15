package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPureIntFunctionCompilerTest {
    @Test void maskAndTemporaryLocalsPreserveExactSourceSlots() {
        MethodNode mask = new MethodNode(Opcodes.ACC_PUBLIC, "getMetadata", "(I)I", null, null);
        mask.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        mask.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        mask.instructions.add(new InsnNode(Opcodes.IAND));
        mask.instructions.add(new InsnNode(Opcodes.IRETURN));

        var compiled = new LegacyPureIntFunctionCompiler().compile(mask, 1);
        assertTrue(compiled.supported(), compiled.error());
        assertEquals(7, compiled.program().evaluate(15));
        assertEquals(3, compiled.program().evaluate(11));

        MethodNode temp = new MethodNode(Opcodes.ACC_PUBLIC, "normalize", "(I)I", null, null);
        LabelNode low = new LabelNode(new Label());
        LabelNode done = new LabelNode(new Label());
        temp.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        temp.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        temp.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        temp.instructions.add(new InsnNode(Opcodes.ICONST_4));
        temp.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPLT, low));
        temp.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        temp.instructions.add(new InsnNode(Opcodes.ICONST_1));
        temp.instructions.add(new InsnNode(Opcodes.ISUB));
        temp.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        temp.instructions.add(new JumpInsnNode(Opcodes.GOTO, done));
        temp.instructions.add(low);
        temp.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        temp.instructions.add(new InsnNode(Opcodes.ICONST_1));
        temp.instructions.add(new InsnNode(Opcodes.IADD));
        temp.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        temp.instructions.add(done);
        temp.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        temp.instructions.add(new InsnNode(Opcodes.IRETURN));

        var withTemp = new LegacyPureIntFunctionCompiler().compile(temp, 1);
        assertTrue(withTemp.supported(), withTemp.error());
        assertEquals(3, withTemp.program().evaluate(4));
        assertEquals(3, withTemp.program().evaluate(2));
    }

    @Test void sourceStateAccessIsRejectedRatherThanInterpreted() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "getMetadata", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "foreign/item/StatefulItemBlock", "mask", "I"));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));

        var compiled = new LegacyPureIntFunctionCompiler().compile(method, 1);
        assertFalse(compiled.supported());
        assertTrue(compiled.error().contains("unsupported"), compiled.error());
    }
}
