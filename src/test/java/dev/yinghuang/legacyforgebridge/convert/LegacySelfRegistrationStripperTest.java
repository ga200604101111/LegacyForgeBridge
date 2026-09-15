package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacySelfRegistrationStripperTest {
    @Test void forgeSelfRegistrationIsRemovedWithoutDroppingOtherConstructorWork() {
        byte[] input = forgeStatefulListener();
        var result = new LegacySelfRegistrationStripper().strip(input, Set.of("()V"));
        assertEquals(1, result.strippedSites());
        String pool = new String(result.bytes(), StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("MinecraftForge"));
        assertFalse(pool.contains("EventBus"));

        ClassNode node = read(result.bytes());
        MethodNode ctor = node.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        assertTrue(opcodes(ctor).stream().anyMatch(i -> i instanceof FieldInsnNode f
                && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals("value")));
        assertTrue(opcodes(ctor).stream().anyMatch(i -> i instanceof MethodInsnNode m
                && m.getOpcode() == Opcodes.INVOKESPECIAL && m.owner.equals("java/lang/Object") && m.name.equals("<init>")));
    }

    @Test void unselectedConstructorIsUntouched() {
        byte[] input = forgeStatefulListener();
        var result = new LegacySelfRegistrationStripper().strip(input, Set.of("(I)V"));
        assertEquals(0, result.strippedSites());
        assertArrayEquals(input, result.bytes());
    }

    @Test void exactFmlSequenceIsAlsoRemoved() {
        byte[] input = fmlListener();
        var result = new LegacySelfRegistrationStripper().strip(input, Set.of("()V"));
        assertEquals(1, result.strippedSites());
        String pool = new String(result.bytes(), StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("FMLCommonHandler"));
        assertFalse(pool.contains("EventBus"));
    }

    private static byte[] forgeStatefulListener() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "unrelated/events/Stateful", null,
                "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PRIVATE, "value", "I", null, null).visitEnd();
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitIntInsn(Opcodes.BIPUSH, 7);
        c.visitFieldInsn(Opcodes.PUTFIELD, "unrelated/events/Stateful", "value", "I");
        c.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;");
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus", "register",
                "(Ljava/lang/Object;)V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(2, 1);
        c.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] fmlListener() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "unrelated/events/Fml", null,
                "java/lang/Object", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/FMLCommonHandler", "instance",
                "()Lcpw/mods/fml/common/FMLCommonHandler;", false);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/FMLCommonHandler", "bus",
                "()Lcpw/mods/fml/common/eventhandler/EventBus;", false);
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus", "register",
                "(Ljava/lang/Object;)V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(2, 1);
        c.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static java.util.List<AbstractInsnNode> opcodes(MethodNode method) {
        java.util.List<AbstractInsnNode> result = new java.util.ArrayList<>();
        for (AbstractInsnNode i : method.instructions) if (i.getOpcode() >= 0) result.add(i);
        return result;
    }
}
