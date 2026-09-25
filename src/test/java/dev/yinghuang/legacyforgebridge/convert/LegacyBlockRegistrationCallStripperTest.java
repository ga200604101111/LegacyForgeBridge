package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockRegistrationCallStripperTest {
    private static final String EVENT =
            "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V";

    @Test
    void neutralizesVoidRegistrationAndPreservesBlockConstruction() {
        byte[] source = fixture(false, false);
        var target = new LegacyBlockRegistrationCallStripper.Target("boot", EVENT);

        var result = new LegacyBlockRegistrationCallStripper().strip(source, target);

        assertEquals(1, result.strippedSites());
        assertTrue(result.blockers().isEmpty(), result.blockers().toString());
        assertFalse(hasRegisterBlock(result.bytes()));
        assertTrue(hasMachineAllocation(result.bytes()));
    }

    @Test
    void neutralizesDiscardedBlockReturningRegistration() {
        byte[] source = fixture(true, false);
        var target = new LegacyBlockRegistrationCallStripper.Target("boot", EVENT);

        var result = new LegacyBlockRegistrationCallStripper().strip(source, target);

        assertEquals(1, result.strippedSites(), result.blockers().toString());
        assertTrue(result.blockers().isEmpty(), result.blockers().toString());
        assertFalse(hasRegisterBlock(result.bytes()));
        assertTrue(hasMachineAllocation(result.bytes()));
    }

    @Test
    void usedBlockReturnValueFailsClosed() {
        byte[] source = fixture(true, true);
        var target = new LegacyBlockRegistrationCallStripper.Target("boot", EVENT);

        var result = new LegacyBlockRegistrationCallStripper().strip(source, target);

        assertEquals(0, result.strippedSites());
        assertTrue(result.blockers().contains(
                "registerBlock-return-value-is-used-or-control-boundary"));
        assertArrayEquals(source, result.bytes());
    }

    @Test
    void recognizesLegacyItemBlockVarargsOverloadWithoutGuessingOtherShapes() {
        assertTrue(LegacyBlockRegistrationCallStripper.supportedDescriptor(
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)V"));
        assertTrue(LegacyBlockRegistrationCallStripper.supportedDescriptor(
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/block/Block;"));
        assertFalse(LegacyBlockRegistrationCallStripper.supportedDescriptor(
                "(Lnet/minecraft/block/Block;I)V"));
    }

    private static byte[] fixture(boolean returnsBlock, boolean useReturn) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/Bootstrap", null, "java/lang/Object", null);
        if (useReturn) {
            writer.visitField(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "REGISTERED",
                    "Lnet/minecraft/block/Block;",
                    null,
                    null).visitEnd();
        }

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "boot", EVENT, null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/machine/MachineBlock");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "foreign/machine/MachineBlock",
                "<init>",
                "()V",
                false);
        method.visitLdcInsn("processor");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                returnsBlock
                        ? "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;"
                        : "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",
                false);
        if (returnsBlock) {
            if (useReturn) {
                method.visitFieldInsn(
                        Opcodes.PUTSTATIC,
                        "foreign/machine/Bootstrap",
                        "REGISTERED",
                        "Lnet/minecraft/block/Block;");
            } else {
                method.visitInsn(Opcodes.POP);
            }
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static boolean hasRegisterBlock(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals(
                        "cpw/mods/fml/common/registry/GameRegistry")
                        && call.name.equals("registerBlock")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMachineAllocation(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW
                        && type.desc.equals("foreign/machine/MachineBlock")) {
                    return true;
                }
            }
        }
        return false;
    }
}
