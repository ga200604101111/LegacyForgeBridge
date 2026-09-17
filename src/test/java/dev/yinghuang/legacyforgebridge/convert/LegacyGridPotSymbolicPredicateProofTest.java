package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyGridPotSymbolicPredicateProofTest {
    @Test
    void symbolicIdentityIsCollectedOnlyWhenItIsTheDirectComparedRenderOperand() {
        MethodNode method=new MethodNode(Opcodes.ASM9,0,"use","()V",null,null);LabelNode done=new LabelNode();
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149645_b","()I",false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"foreign/render/Ids","coordinateCross","I"));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,done));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"foreign/render/Ids","unrelated","I"));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(done);method.instructions.add(new InsnNode(Opcodes.RETURN));
        assertEquals(Set.of("foreign/render/Ids#coordinateCross"),LegacyGridPotProofs.symbolicContentInsertionRenderFields(method));
    }
}
