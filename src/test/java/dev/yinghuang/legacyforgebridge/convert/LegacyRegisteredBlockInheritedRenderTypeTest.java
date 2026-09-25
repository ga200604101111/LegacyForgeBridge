package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRegisteredBlockInheritedRenderTypeTest {
    @Test
    void known1710PlatformBasesSupplyExactInheritedIdentity() {
        assertInherited("net/minecraft/block/Block", 0);
        assertInherited("net/minecraft/block/BlockContainer", 0);
        assertInherited("net/minecraft/block/BlockBush", 1);
        assertInherited("net/minecraft/block/BlockCactus", 13);
        assertInherited("net/minecraft/block/BlockDoublePlant", 40);
    }

    @Test
    void sourceOwnedSuperclassMayStillInheritKnownPlatformIdentity() {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        classes.put("foreign/render/Leaf", clazz("foreign/render/Leaf", "foreign/render/Base"));
        classes.put("foreign/render/Base", clazz("foreign/render/Base", "net/minecraft/block/Block"));
        var identity = LegacyRegisteredBlockRenderTypeAnalyzer.inheritedPlatformRenderIdentity(classes, "foreign/render/Leaf");
        assertNotNull(identity);
        assertTrue(identity.isConstant(0));
    }

    @Test
    void anySourceOverridePreventsPlatformFallback() {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        ClassNode source = clazz("foreign/render/Override", "net/minecraft/block/Block");
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "func_149645_b", "()I", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        source.methods.add(method);
        classes.put(source.name, source);
        assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.inheritedPlatformRenderIdentity(classes, source.name));
    }

    @Test
    void unknownExternalBaseStaysFailClosed() {
        Map<String,ClassNode> classes = Map.of("foreign/render/Unknown", clazz("foreign/render/Unknown", "third/platform/UnknownBlock"));
        assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.inheritedPlatformRenderIdentity(classes, "foreign/render/Unknown"));
        assertTrue(LegacyBlockRenderType1710.effectiveRenderType("third/platform/UnknownBlock").isEmpty());
    }

    private static void assertInherited(String base, int expected) {
        String source = "foreign/render/Source" + Math.abs(base.hashCode());
        Map<String,ClassNode> classes = Map.of(source, clazz(source, base));
        var identity = LegacyRegisteredBlockRenderTypeAnalyzer.inheritedPlatformRenderIdentity(classes, source);
        assertNotNull(identity, base);
        assertTrue(identity.isConstant(expected), base + " -> " + identity);
    }

    private static ClassNode clazz(String name, String superName) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_7;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = superName;
        return node;
    }
}
