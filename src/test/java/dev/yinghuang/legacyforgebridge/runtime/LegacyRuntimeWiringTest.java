package dev.yinghuang.legacyforgebridge.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRuntimeWiringTest {
    private static List<String> calls(String className, String selectedMethod) throws Exception {
        List<String> calls = new ArrayList<>();
        try (var stream = LegacyRuntimeWiringTest.class.getResourceAsStream("/dev/yinghuang/legacyforgebridge/" + className + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (selectedMethod != null && !name.equals(selectedMethod)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            calls.add(owner + '.' + name);
                        }
                    };
                }
            }, 0);
        }
        return calls;
    }
    @Test void bothForgeReceiversAndLifecycleResetsAreWired() throws Exception {
        var calls = calls("LegacyForgeBridgeClient", null);
        assertEquals(2L, calls.stream().filter(s -> s.endsWith("FmlRuntimeClient.handleForge")).count());
        assertEquals(3L, calls.stream().filter(s -> s.endsWith("FmlRuntimeClient.reset")).count());
        assertTrue(calls.stream().anyMatch(s -> s.endsWith("FmlRuntimeClient.onPlay")));
        assertTrue(calls.stream().anyMatch(s -> s.endsWith("FmlRuntimeClient.initializeAdapters")));
    }
    @Test void entityAdjustStillOnlyChangesPacketBaselineAndSpawnUsesFullCodec() throws Exception {
        var adjustment = calls("network/FmlRuntimeClient", "adjustEntity");
        assertTrue(adjustment.stream().anyMatch(s -> s.endsWith("Entity.syncPacketPositionCodec")));
        assertFalse(adjustment.stream().anyMatch(s -> s.endsWith("Entity.setPos") || s.contains("teleport")));
        var all = calls("network/FmlRuntimeClient", null);
        assertTrue(all.stream().anyMatch(s -> s.endsWith("LegacyEntitySpawnCodec.decode")));
        assertTrue(all.stream().anyMatch(s -> s.endsWith("LegacyRuntimeState.accepts")));
        assertFalse(all.stream().anyMatch(s -> s.endsWith("FmlRuntimeCodec.parseEntitySpawnHeader")));
    }
    @Test void invalidOrMismatchedWindowIdsCannotBeApplied() {
        for (int id : new int[]{Integer.MIN_VALUE, -1, 0, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeGuards.requireWindow(id));
        }
        LegacyRuntimeGuards.requireMatchingWindow(1, 1);
        LegacyRuntimeGuards.requireMatchingWindow(255, 255);
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeGuards.requireMatchingWindow(7, 3));
    }
}
