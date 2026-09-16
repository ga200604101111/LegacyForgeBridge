package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockMaterialProvenanceAnalyzerTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC = "Lnet/minecraft/block/material/Material;";

    @TempDir Path tempDir;

    @Test
    void provesOnlyOneStableStaticMaterialFieldAcrossTheDirectBlockSourceConstructors() throws Exception {
        Path jar = tempDir.resolve("MaterialProvenance.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/materialproof/Plain.class",
                    directBlock("foreign/materialproof/Plain", "field_151575_d"));
            put(out, "foreign/materialproof/Base.class",
                    directBlock("foreign/materialproof/Base", "field_151575_d"));
            put(out, "foreign/materialproof/Child.class",
                    child("foreign/materialproof/Child", "foreign/materialproof/Base"));
            put(out, "foreign/materialproof/Delegating.class", delegatingBlock());
            put(out, "foreign/materialproof/Unknown.class", unknownMaterialBlock());
            put(out, "foreign/materialproof/Mixed.class", mixedMaterialBlock());
            put(out, "foreign/materialproof/Specialized.class", specializedBlock());
            put(out, "foreign/materialproof/Bootstrap.class", bootstrap());
        }

        LegacyBlockMaterialProvenanceAnalyzer.Analysis analysis =
                new LegacyBlockMaterialProvenanceAnalyzer().analyze(jar);
        Map<String, LegacyBlockMaterialProvenanceAnalyzer.Proof> proofs = analysis.proofs().stream()
                .collect(Collectors.toMap(LegacyBlockMaterialProvenanceAnalyzer.Proof::registryName, value -> value));

        assertEquals(6, proofs.size(), String.join("\n", analysis.diagnostics()));

        var plain = proofs.get("plain");
        assertTrue(plain.complete(), plain.reasons().toString());
        assertEquals("foreign/materialproof/Plain", plain.directBlockSourceClass());
        assertEquals(MATERIAL, plain.material().owner());
        assertEquals("field_151575_d", plain.material().fieldName());
        assertEquals(MATERIAL_DESC, plain.material().descriptor());

        var child = proofs.get("child");
        assertTrue(child.complete(), child.reasons().toString());
        assertEquals("foreign/materialproof/Base", child.directBlockSourceClass());
        assertEquals("field_151575_d", child.material().fieldName());

        var delegating = proofs.get("delegating");
        assertTrue(delegating.complete(), delegating.reasons().toString());
        assertEquals("field_151575_d", delegating.material().fieldName());

        var unknown = proofs.get("unknown");
        assertFalse(unknown.complete());
        assertNull(unknown.material());
        assertTrue(unknown.reasons().stream().anyMatch(reason -> reason.contains("not one direct static Material field")));

        var mixed = proofs.get("mixed");
        assertFalse(mixed.complete());
        assertNull(mixed.material());
        assertTrue(mixed.reasons().stream().anyMatch(reason -> reason.contains("multiple Material fields")));

        var specialized = proofs.get("specialized");
        assertFalse(specialized.complete());
        assertNull(specialized.material());
        assertTrue(specialized.reasons().stream().anyMatch(reason -> reason.contains("BlockOre")));
    }

    private static byte[] directBlock(String owner, String materialField) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, materialField, MATERIAL_DESC);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] child(String owner, String parent) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, parent, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); end(init); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] delegatingBlock() {
        String owner = "foreign/materialproof/Delegating";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);

        MethodVisitor empty = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        empty.visitCode(); empty.visitVarInsn(Opcodes.ALOAD, 0); empty.visitInsn(Opcodes.ICONST_1);
        empty.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "(I)V", false);
        empty.visitInsn(Opcodes.RETURN); end(empty);

        MethodVisitor actual = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
        actual.visitCode(); actual.visitVarInsn(Opcodes.ALOAD, 0);
        actual.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, "field_151575_d", MATERIAL_DESC);
        actual.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        actual.visitInsn(Opcodes.RETURN); end(actual);
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] unknownMaterialBlock() {
        String owner = "foreign/materialproof/Unknown";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN); end(init); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] mixedMaterialBlock() {
        String owner = "foreign/materialproof/Mixed";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        materialConstructor(writer, owner, "()V", "field_151575_d", false);
        materialConstructor(writer, owner, "(I)V", "field_151576_e", true);
        writer.visitEnd(); return writer.toByteArray();
    }

    private static void materialConstructor(ClassWriter writer, String owner, String descriptor,
                                            String field, boolean consumeInt) {
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null);
        init.visitCode();
        if (consumeInt) {
            init.visitVarInsn(Opcodes.ILOAD, 1);
            init.visitInsn(Opcodes.POP);
        }
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, field, MATERIAL_DESC);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN); end(init);
    }

    private static byte[] specializedBlock() {
        String owner = "foreign/materialproof/Specialized";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/BlockOre", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/BlockOre", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); end(init); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/materialproof/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd(); method.visitCode();
        register(method, "foreign/materialproof/Plain", "plain");
        register(method, "foreign/materialproof/Child", "child");
        register(method, "foreign/materialproof/Delegating", "delegating");
        register(method, "foreign/materialproof/Unknown", "unknown");
        register(method, "foreign/materialproof/Mixed", "mixed");
        register(method, "foreign/materialproof/Specialized", "specialized");
        method.visitInsn(Opcodes.RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, owner); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
