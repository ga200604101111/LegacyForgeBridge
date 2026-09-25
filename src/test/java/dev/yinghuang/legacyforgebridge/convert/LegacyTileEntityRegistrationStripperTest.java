package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTileEntityRegistrationStripperTest {
    private static final String EVENT =
            "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V";

    @Test
    void removesOneExactPureTileRegistrationSlice() {
        byte[] source = fixture(false);
        var target = new LegacyTileEntityRegistrationStripper.Target(
                "boot", EVENT, "foreign/machine/MachineTile", "processor_tile");

        var result = new LegacyTileEntityRegistrationStripper().strip(source, target);

        assertEquals(1, result.strippedSites());
        assertTrue(result.blockers().isEmpty(), result.blockers().toString());

        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(result.bytes()).accept(node, 0);
        assertFalse(node.methods.stream().anyMatch(method -> {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals("cpw/mods/fml/common/registry/GameRegistry")
                        && call.name.equals("registerTileEntity")) {
                    return true;
                }
            }
            return false;
        }));
        assertFalse(node.methods.stream().anyMatch(method -> {
            for (var instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode ldc
                        && ldc.cst instanceof Type type
                        && type.getSort() == Type.OBJECT
                        && type.getInternalName().equals("foreign/machine/MachineTile")) {
                    return true;
                }
            }
            return false;
        }));
    }

    @Test
    void computedTileIdIsLeftUntouched() {
        byte[] source = fixture(true);
        var target = new LegacyTileEntityRegistrationStripper.Target(
                "boot", EVENT, "foreign/machine/MachineTile", "processor_tile");

        var result = new LegacyTileEntityRegistrationStripper().strip(source, target);

        assertEquals(0, result.strippedSites());
        assertTrue(result.blockers().contains(
                "no-exact-pure-registerTileEntity-callsite"));
        assertArrayEquals(source, result.bytes());
    }

    private static byte[] fixture(boolean computedId) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/Bootstrap", null, "java/lang/Object", null);
        if (computedId) {
            writer.visitField(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "TILE_ID", "Ljava/lang/String;", null, null).visitEnd();
        }

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "boot", EVENT, null, null);
        method.visitCode();
        method.visitLdcInsn(Type.getObjectType("foreign/machine/MachineTile"));
        if (computedId) {
            method.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    "foreign/machine/Bootstrap",
                    "TILE_ID",
                    "Ljava/lang/String;");
        } else {
            method.visitLdcInsn("processor_tile");
        }
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
