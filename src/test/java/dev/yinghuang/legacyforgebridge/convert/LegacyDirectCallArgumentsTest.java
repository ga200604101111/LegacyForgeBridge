package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyDirectCallArgumentsTest {
    private static final String CLIENT_REGISTRY = "cpw/mods/fml/client/registry/ClientRegistry";
    private static final String REGISTER_TILE =
            "(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V";

    @Test
    void consecutiveRegistrationsKeepTheirOwnArguments() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = "foreign/provenance/Client";
        owner.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "bind", "()V", null, null);
        owner.methods.add(method);

        emitRegistration(method, "foreign/provenance/TileA", "A", "foreign/provenance/RendererA");
        emitRegistration(method, "foreign/provenance/TileB", "B", "foreign/provenance/RendererB");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 5;
        method.maxLocals = 1;

        List<MethodInsnNode> calls = new ArrayList<>();
        method.instructions.forEach(insn -> {
            if (insn instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESTATIC
                    && CLIENT_REGISTRY.equals(call.owner)
                    && "registerTileEntity".equals(call.name)) {
                calls.add(call);
            }
        });
        assertEquals(2, calls.size());

        var first = LegacyDirectCallArguments.classStringNew(owner, method, calls.get(0));
        var second = LegacyDirectCallArguments.classStringNew(owner, method, calls.get(1));
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("foreign/provenance/TileA", first.classInternalName());
        assertEquals("A", first.stringValue());
        assertEquals("foreign/provenance/RendererA", first.newTypeInternalName());
        assertEquals("foreign/provenance/TileB", second.classInternalName());
        assertEquals("B", second.stringValue());
        assertEquals("foreign/provenance/RendererB", second.newTypeInternalName());
    }

    private static void emitRegistration(MethodNode method, String tile, String id, String renderer) {
        method.instructions.add(new LdcInsnNode(Type.getObjectType(tile)));
        method.instructions.add(new LdcInsnNode(id));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, renderer));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, renderer, "<init>", "()V", false));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, CLIENT_REGISTRY, "registerTileEntity", REGISTER_TILE, false));
    }
}
