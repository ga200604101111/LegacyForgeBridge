package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityPresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test void inventoriesSourceWideRendererRegistrationsAndProvesOnlyEmptyRenderCallbacks() throws Exception {
        Path jar = tempDir.resolve("presentation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/client/ClientRegistrar.class", registrar());
            put(out, "third/client/RenderEmpty.class", renderer("third/client/RenderEmpty", true));
            put(out, "third/client/RenderActive.class", renderer("third/client/RenderActive", false));
        }

        var analysis = new LegacyEntityPresentationAnalyzer().analyze(jar);
        assertEquals(2, analysis.registrations().size(), analysis.diagnostics().toString());
        var empty = analysis.registrations().stream()
                .filter(value -> value.entityClass().equals("third/entity/Orb"))
                .findFirst().orElseThrow();
        assertEquals("third/client/RenderEmpty", empty.rendererClass());
        assertTrue(empty.rendererClassPresent());
        assertTrue(empty.noOpRenderProven());
        assertEquals("third/client/RenderEmpty", empty.renderOwner());

        var active = analysis.registrations().stream()
                .filter(value -> value.entityClass().equals("third/entity/ActiveOrb"))
                .findFirst().orElseThrow();
        assertFalse(active.noOpRenderProven());
    }

    private static byte[] registrar() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/client/ClientRegistrar", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "()V", null, null);
        m.visitCode();
        register(m, "third/entity/Orb", "third/client/RenderEmpty");
        register(m, "third/entity/ActiveOrb", "third/client/RenderActive");
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void register(MethodVisitor m, String entity, String renderer) {
        m.visitLdcInsn(Type.getObjectType(entity));
        m.visitTypeInsn(Opcodes.NEW, renderer);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, renderer, "<init>", "()V", false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/client/registry/RenderingRegistry",
                "registerEntityRenderingHandler",
                "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V", false);
    }

    private static byte[] renderer(String name, boolean noOp) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/client/renderer/entity/Render", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/client/renderer/entity/Render", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor render = w.visitMethod(Opcodes.ACC_PUBLIC, "doRender",
                "(Lthird/entity/Orb;DDDFF)V", null, null);
        render.visitCode();
        if (!noOp) {
            render.visitVarInsn(Opcodes.ALOAD, 1);
            render.visitInsn(Opcodes.POP);
        }
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
