package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationEffectsRegistry;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConvertedLegacyBlockActivationEffectsBytecodeTest {
    @Test void interactionAndBootstrapAreWiredToEffectRuntimeWithoutPacketOrInventoryMutation() throws Exception {
        String registry = Type.getInternalName(LegacyBlockActivationEffectsRegistry.class);
        assertTrue(calls(method(read(ConvertedLegacyBlock.class), "useWithoutItem")).stream()
                .anyMatch(c -> c.owner.equals(registry) && c.name.equals("activate")));
        assertTrue(calls(method(read(GeneratedModSupport.class), "finishMod")).stream()
                .anyMatch(c -> c.owner.equals(registry) && c.name.equals("loadMod")));
        MethodNode activate = method(read(LegacyBlockActivationEffectsRegistry.class), "activate");
        var calls = calls(activate);
        assertEquals(1, calls.stream().filter(c -> c.owner.equals("net/minecraft/world/level/Level") && c.name.equals("setBlock")
                && c.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")).count());
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("getMainHandItem")));
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("isClientSide")));
        assertFalse(calls.stream().anyMatch(c -> c.owner.contains("network") || c.name.equals("shrink") || c.name.equals("consume")));
        assertTrue(java.util.stream.StreamSupport.stream(activate.instructions.spliterator(), false)
                .anyMatch(i -> i instanceof IntInsnNode n && n.operand == 18), "legacy flag 2 must suppress invented modern shape propagation");
    }
    private static List<MethodInsnNode> calls(MethodNode method) {
        var calls = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode node : method.instructions) if (node instanceof MethodInsnNode call) calls.add(call);
        return calls;
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode read(Class<?> type) throws Exception {
        try (InputStream input = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) {
            assertNotNull(input); ClassNode node = new ClassNode(Opcodes.ASM9); new ClassReader(input).accept(node, 0); return node;
        }
    }
}
