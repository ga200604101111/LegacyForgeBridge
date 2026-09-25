package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySourceMaterialHarvestAnalyzerTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MAP_COLOR = "net/minecraft/block/material/MapColor";
    private static final String BLOCK = "net/minecraft/block/Block";
    @TempDir Path tempDir;

    @Test
    void stableDirectMaterialSingletonKeepsInheritedNoToolDefault() throws Exception {
        Path jar = fixture("good", Kind.GOOD, false);
        var provenance = new LegacyBlockMaterialProvenanceAnalyzer().analyze(jar).proofs().getFirst();
        assertTrue(provenance.complete(), provenance.reasons().toString());
        assertEquals("foreign/sourcematerial/GoodMaterial", provenance.material().owner());
        assertEquals("instance", provenance.material().fieldName());
        assertEquals("Lforeign/sourcematerial/GoodMaterial;", provenance.material().descriptor());

        var analysis = new LegacySourceMaterialHarvestAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(1, analysis.proofs().size());
        var proof = analysis.proofs().getFirst();
        assertTrue(proof.complete(), proof.reasons().toString());
        assertEquals(Boolean.TRUE, proof.toolNotRequired());
        assertTrue(proof.reasons().isEmpty());
    }

    @Test
    void setRequiresToolOrGetterOverrideRemainsFailClosed() throws Exception {
        Path requires = fixture("requires", Kind.REQUIRES_TOOL, false);
        var requiresProof = new LegacySourceMaterialHarvestAnalyzer().analyze(requires).proofs().getFirst();
        assertFalse(requiresProof.complete());
        assertNull(requiresProof.toolNotRequired());
        assertTrue(requiresProof.reasons().stream().anyMatch(reason -> reason.contains("setRequiresTool")),
                requiresProof.reasons().toString());

        Path override = fixture("override", Kind.OVERRIDES_GETTER, false);
        var overrideProof = new LegacySourceMaterialHarvestAnalyzer().analyze(override).proofs().getFirst();
        assertFalse(overrideProof.complete());
        assertNull(overrideProof.toolNotRequired());
        assertTrue(overrideProof.reasons().stream().anyMatch(reason -> reason.contains("overrides isToolNotRequired")),
                overrideProof.reasons().toString());
    }

    @Test
    void mutableSourceSingletonIsRejectedBeforeHarvestSemantics() throws Exception {
        Path jar = fixture("mutable", Kind.GOOD, true);
        var provenance = new LegacyBlockMaterialProvenanceAnalyzer().analyze(jar).proofs().getFirst();
        assertFalse(provenance.complete());
        assertNull(provenance.material());
        assertTrue(provenance.reasons().stream()
                .anyMatch(reason -> reason.contains("not one stable direct static Material field")),
                provenance.reasons().toString());
        assertTrue(new LegacySourceMaterialHarvestAnalyzer().analyze(jar).proofs().isEmpty());
    }

    private Path fixture(String suffix, Kind kind, boolean extraWrite) throws Exception {
        String material = "foreign/sourcematerial/" + switch (kind) {
            case GOOD -> "GoodMaterial";
            case REQUIRES_TOOL -> "RequiresMaterial";
            case OVERRIDES_GETTER -> "OverrideMaterial";
        };
        String block = "foreign/sourcematerial/Block" + suffix;
        String bootstrap = "foreign/sourcematerial/Bootstrap" + suffix;
        Path jar = tempDir.resolve(suffix + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, material + ".class", material(material, kind, extraWrite));
            put(out, block + ".class", block(block, material));
            put(out, bootstrap + ".class", bootstrap(bootstrap, block));
        }
        return jar;
    }

    private static byte[] material(String owner, Kind kind, boolean extraWrite) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, MATERIAL, null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "instance", "L" + owner + ";", null, null).visitEnd();

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, MATERIAL, "<init>", "(L" + MAP_COLOR + ";)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        if (kind == Kind.REQUIRES_TOOL) {
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MATERIAL, "func_76221_f", "()L" + MATERIAL + ";", false);
        } else {
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MATERIAL, "func_76219_n", "()L" + MATERIAL + ";", false);
        }
        init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN);
        end(init);

        if (kind == Kind.OVERRIDES_GETTER) {
            MethodVisitor getter = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_76229_l", "()Z", null, null);
            getter.visitCode();
            getter.visitInsn(Opcodes.ICONST_1);
            getter.visitInsn(Opcodes.IRETURN);
            end(getter);
        }

        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        newSingleton(clinit, owner);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, owner, "instance", "L" + owner + ";");
        clinit.visitInsn(Opcodes.RETURN);
        end(clinit);

        if (extraWrite) {
            MethodVisitor mutate = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "mutate", "()V", null, null);
            mutate.visitCode();
            newSingleton(mutate, owner);
            mutate.visitFieldInsn(Opcodes.PUTSTATIC, owner, "instance", "L" + owner + ";");
            mutate.visitInsn(Opcodes.RETURN);
            end(mutate);
        }

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] block(String owner, String material) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, BLOCK, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, material, "instance", "L" + material + ";");
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, BLOCK, "<init>", "(L" + MATERIAL + ";)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap(String owner, String block) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, block);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, block, "<init>", "()V", false);
        method.visitLdcInsn("source_material");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void newSingleton(MethodVisitor method, String owner) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
    }

    private static void end(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private enum Kind { GOOD, REQUIRES_TOOL, OVERRIDES_GETTER }
}
