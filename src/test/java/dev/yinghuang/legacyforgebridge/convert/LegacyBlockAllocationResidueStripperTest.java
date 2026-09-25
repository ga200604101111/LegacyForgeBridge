package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockAllocationResidueStripperTest {
    private static final String EVENT =
            "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V";
    private static final String MACHINE = "foreign/machine/MachineBlock";
    private static final String BLOCK = "net/minecraft/block/Block";

    @Test
    void removesExactTwoArgumentResidueAndPreservesDiscardedNameEvaluation() {
        byte[] source = staged(false);
        var target = target();

        var result = new LegacyBlockAllocationResidueStripper()
                .strip(source, target);

        assertEquals(1, result.strippedSites(), result.blockers().toString());
        assertTrue(result.blockers().isEmpty(), result.blockers().toString());
        MethodNode method = method(result.bytes());
        assertFalse(hasAllocation(method));
        assertTrue(hasString(method, "processor"));
        assertEquals(1, countOpcode(method, Opcodes.POP));
    }

    @Test
    void removesOnlyBlockValueFromFourArgumentNeutralizedResidue() {
        byte[] source = staged(true);
        var target = target();

        var result = new LegacyBlockAllocationResidueStripper()
                .strip(source, target);

        assertEquals(1, result.strippedSites(), result.blockers().toString());
        assertTrue(result.blockers().isEmpty(), result.blockers().toString());
        MethodNode method = method(result.bytes());
        assertFalse(hasAllocation(method));
        assertTrue(hasString(method, "processor"));
        assertEquals(3, countOpcode(method, Opcodes.POP));
    }

    private static LegacyBlockAllocationResidueStripper.Target target() {
        return new LegacyBlockAllocationResidueStripper.Target(
                "boot",
                EVENT,
                MACHINE,
                "()V",
                List.of(
                        new LegacySingleInputProcessorBlockAllocationAnalyzer.Effect(
                                LegacySingleInputProcessorBlockAllocationAnalyzer.EffectKind.HARDNESS,
                                MACHINE,
                                "func_149711_c",
                                "(F)Lnet/minecraft/block/Block;",
                                3.0F,
                                null),
                        new LegacySingleInputProcessorBlockAllocationAnalyzer.Effect(
                                LegacySingleInputProcessorBlockAllocationAnalyzer.EffectKind.SOUND,
                                BLOCK,
                                "func_149672_a",
                                "(Lnet/minecraft/block/Block$SoundType;)Lnet/minecraft/block/Block;",
                                null,
                                "METAL")));
    }

    private static byte[] staged(boolean fourArguments) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                "foreign/machine/Bootstrap",
                null,
                "java/lang/Object",
                null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "boot", EVENT, null, null);
        method.visitCode();

        method.visitTypeInsn(Opcodes.NEW, MACHINE);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL, MACHINE, "<init>", "()V", false);
        method.visitLdcInsn(3.0F);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                MACHINE,
                "func_149711_c",
                "(F)Lnet/minecraft/block/Block;",
                false);
        method.visitFieldInsn(
                Opcodes.GETSTATIC,
                BLOCK,
                "field_149777_j",
                "Lnet/minecraft/block/Block$SoundType;");
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BLOCK,
                "func_149672_a",
                "(Lnet/minecraft/block/Block$SoundType;)Lnet/minecraft/block/Block;",
                false);

        if (fourArguments) {
            method.visitLdcInsn(Type.getType("Lforeign/machine/MachineItemBlock;"));
        }
        method.visitLdcInsn("processor");
        if (fourArguments) {
            method.visitInsn(Opcodes.ICONST_0);
            method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.POP);
        } else {
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.POP);
        }

        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static MethodNode method(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(
                node,
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node.methods.stream()
                .filter(value -> value.name.equals("boot")
                        && value.desc.equals(EVENT))
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasAllocation(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.NEW
                    && MACHINE.equals(type.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasString(MethodNode method, String text) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode ldc
                    && text.equals(ldc.cst)) {
                return true;
            }
        }
        return false;
    }

    private static int countOpcode(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) count++;
        }
        return count;
    }
}
