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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockPlacementCompilerTest {
    @TempDir Path tempDir;

    @Test void pureSideHitAndMetadataProgramsCompileAndExecuteAcrossUnrelatedNamespace() throws Exception {
        Path jar = tempDir.resolve("PlacementRules.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/place/Panel.class", panel());
            put(out, "foreign/place/FacingPart.class", facingPart());
            put(out, "foreign/place/Bootstrap.class", bootstrap(false));
        }

        var analysis = new LegacyBlockPlacementCompiler().compile(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(2, analysis.programs().size());

        var panel = analysis.programs().stream()
                .filter(value -> value.registryName().equals("panel"))
                .findFirst().orElseThrow();
        assertEquals(3, panel.evaluate(1, 0.2f, 0.2f, 0.2f, 3));
        assertEquals(3, panel.evaluate(2, 0.2f, 0.4f, 0.2f, 3));
        assertEquals(11, panel.evaluate(2, 0.2f, 0.7f, 0.2f, 3));
        assertEquals(11, panel.evaluate(0, 0.2f, 0.2f, 0.2f, 3));

        var facing = analysis.programs().stream()
                .filter(value -> value.registryName().equals("facing_part"))
                .findFirst().orElseThrow();
        assertEquals(1, facing.evaluate(0, 0, 0, 0, 0));
        assertEquals(0, facing.evaluate(1, 0, 0, 0, 0));
        assertEquals(3, facing.evaluate(2, 0, 0, 0, 0));
        assertEquals(5, facing.evaluate(4, 0, 0, 0, 0));
    }

    @Test void worldOrInstanceDependentPlacementFailsClosed() throws Exception {
        Path jar = tempDir.resolve("UnsafePlacement.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/place/UnsafePart.class", unsafePart());
            put(out, "foreign/place/Bootstrap.class", bootstrap(true));
        }
        var analysis = new LegacyBlockPlacementCompiler().compile(jar);
        assertTrue(analysis.programs().isEmpty());
        assertTrue(analysis.diagnostics().stream().anyMatch(value -> value.contains("Unsupported pure placement callback")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] panel() {
        ClassWriter w = blockClass("foreign/place/Panel");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149660_a",
                "(Lnet/minecraft/world/World;IIIIFFFI)I", null, null);
        m.visitCode();
        Label addTopBit = new Label();
        Label keepMeta = new Label();
        Label done = new Label();
        m.visitVarInsn(Opcodes.ILOAD, 5);
        m.visitJumpInsn(Opcodes.IFEQ, addTopBit);
        m.visitVarInsn(Opcodes.ILOAD, 5);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitJumpInsn(Opcodes.IF_ICMPEQ, keepMeta);
        m.visitVarInsn(Opcodes.FLOAD, 7);
        m.visitInsn(Opcodes.F2D);
        m.visitLdcInsn(0.5d);
        m.visitInsn(Opcodes.DCMPG);
        m.visitJumpInsn(Opcodes.IFGT, addTopBit);
        m.visitLabel(keepMeta);
        m.visitVarInsn(Opcodes.ILOAD, 9);
        m.visitJumpInsn(Opcodes.GOTO, done);
        m.visitLabel(addTopBit);
        m.visitVarInsn(Opcodes.ILOAD, 9);
        m.visitIntInsn(Opcodes.BIPUSH, 8);
        m.visitInsn(Opcodes.IOR);
        m.visitLabel(done);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(4, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] facingPart() {
        ClassWriter w = blockClass("foreign/place/FacingPart");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockPlaced",
                "(Lnet/minecraft/world/World;IIIIFFFI)I", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/util/ForgeDirection", "OPPOSITES", "[I");
        m.visitVarInsn(Opcodes.ILOAD, 5);
        m.visitInsn(Opcodes.IALOAD);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] unsafePart() {
        ClassWriter w = blockClass("foreign/place/UnsafePart");
        w.visitField(Opcodes.ACC_PRIVATE, "flip", "Z", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149660_a",
                "(Lnet/minecraft/world/World;IIIIFFFI)I", null, null);
        m.visitCode();
        Label plain = new Label();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, "foreign/place/UnsafePart", "flip", "Z");
        m.visitJumpInsn(Opcodes.IFEQ, plain);
        m.visitVarInsn(Opcodes.ILOAD, 9);
        m.visitIntInsn(Opcodes.BIPUSH, 8);
        m.visitInsn(Opcodes.IOR);
        m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(plain);
        m.visitVarInsn(Opcodes.ILOAD, 9);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(2, 10);
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

    private static byte[] bootstrap(boolean unsafe) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/place/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor av = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        av.visitEnd();
        m.visitCode();
        if (unsafe) {
            register(m, "foreign/place/UnsafePart", "unsafe_part");
        } else {
            register(m, "foreign/place/Panel", "panel");
            register(m, "foreign/place/FacingPart", "facing_part");
        }
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void register(MethodVisitor m, String blockClass, String name) {
        m.visitTypeInsn(Opcodes.NEW, blockClass);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
