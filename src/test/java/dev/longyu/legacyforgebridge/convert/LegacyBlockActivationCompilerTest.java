package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockActivationCompilerTest {
    @TempDir Path tempDir;

    @Test void pureClickedSideDecisionCompilesAndExecutesAcrossUnrelatedNamespace() throws Exception {
        Path jar = tempDir.resolve("ActivationRules.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/SideGate.class", sideGate());
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/SideGate", "side_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.activationCallbacks());
        assertEquals(1, analysis.programs().size());
        var program = analysis.programs().getFirst();
        assertEquals("side_gate", program.registryName());
        assertEquals("foreign/use/SideGate", program.sourceOwner());
        assertTrue(program.evaluate(1));
        assertTrue(program.evaluate(3));
        assertFalse(program.evaluate(0));
        assertFalse(program.evaluate(2));
        assertFalse(program.evaluate(4));
    }

    @Test void exactLegacyIsRemoteReadCompilesToTypedClientSideInput() throws Exception {
        Path jar = tempDir.resolve("ClientSideActivation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/ClientGate.class", clientSideGate("field_72995_K"));
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/ClientGate", "client_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.programs().size());
        var program = analysis.programs().getFirst();
        assertTrue(program.instructions().stream()
                .anyMatch(value -> value.op() == LegacyBlockActivationCompiler.Op.LOAD_CLIENT_SIDE));
        assertFalse(program.evaluate(1, 0, false));
        assertTrue(program.evaluate(1, 0, true));
        assertThrows(IllegalStateException.class, () -> program.evaluate(1, 0));
    }

    @Test void exactLegacySneakingReadCompilesToTypedPlayerInput() throws Exception {
        Path jar = tempDir.resolve("SneakingActivation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/SneakGate.class", playerGate("func_70093_af"));
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/SneakGate", "sneak_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.programs().size());
        var program = analysis.programs().getFirst();
        assertTrue(program.instructions().stream()
                .anyMatch(value -> value.op() == LegacyBlockActivationCompiler.Op.LOAD_SNEAKING));
        assertFalse(program.evaluate(1, 0, false, false));
        assertTrue(program.evaluate(1, 0, false, true));
        assertThrows(IllegalStateException.class, () -> program.evaluate(1, 0, false));
    }

    @Test void arbitraryWorldDependencyFailsClosedInsteadOfGuessing() throws Exception {
        Path jar = tempDir.resolve("UnsafeActivation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/WorldGate.class", arbitraryWorldGate());
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/WorldGate", "world_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertEquals(1, analysis.activationCallbacks());
        assertTrue(analysis.programs().isEmpty());
        assertTrue(analysis.diagnostics().stream().anyMatch(value -> value.contains("Unsupported pure activation callback")),
                String.join("\n", analysis.diagnostics()));
    }

    @Test void arbitraryPlayerDependencyFailsClosedInsteadOfGuessing() throws Exception {
        Path jar = tempDir.resolve("UnsafePlayerActivation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/PlayerGate.class", arbitraryPlayerGate());
            put(out, "foreign/use/Bootstrap.class", bootstrap("foreign/use/PlayerGate", "player_gate"));
        }

        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertEquals(1, analysis.activationCallbacks());
        assertTrue(analysis.programs().isEmpty());
        assertTrue(analysis.diagnostics().stream().anyMatch(value -> value.contains("Unsupported pure activation callback")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] sideGate() {
        ClassWriter w = blockClass("foreign/use/SideGate");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        Label falseResult = new Label();
        m.visitVarInsn(Opcodes.ILOAD, 6);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IAND);
        m.visitJumpInsn(Opcodes.IFEQ, falseResult);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(falseResult);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] clientSideGate(String fieldName) {
        ClassWriter w = blockClass("foreign/use/ClientGate");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/World", fieldName, "Z");
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] playerGate(String methodName) {
        ClassWriter w = blockClass("foreign/use/SneakGate");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 5);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", methodName, "()Z", false);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] arbitraryWorldGate() {
        ClassWriter w = blockClass("foreign/use/WorldGate");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "isDaytime", "()Z", false);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] arbitraryPlayerGate() {
        ClassWriter w = blockClass("foreign/use/PlayerGate");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 5);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "isCreative", "()Z", false);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static ClassWriter blockClass(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        return w;
    }

    private static byte[] bootstrap(String blockClass, String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/use/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor av = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        av.visitEnd();
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, blockClass);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
