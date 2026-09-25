package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorBlockConstructionAnalyzerTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC = "L" + MATERIAL + ";";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String BLOCK_CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String SOUND_DESC =
            "Lnet/minecraft/block/Block$SoundType;";

    @TempDir Path tempDir;

    @Test
    void provesRockBlockContainerHardnessResistanceSoundAndLight() throws Exception {
        Path jar = tempDir.resolve("processor-block-construction.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/processor/Machine.class",
                    machine(
                            "foreign/processor/Machine",
                            "field_151576_e",
                            false,
                            false));
        }

        var proof = new LegacySingleInputProcessorBlockConstructionAnalyzer()
                .prove(jar, rule("foreign/processor/Machine"));

        assertTrue(proof.replacementProofComplete(), proof.blockers().toString());
        assertTrue(proof.constructorPresent());
        assertTrue(proof.constructorChainComplete());
        assertTrue(proof.constructorControlFlowSimple());
        assertTrue(proof.materialSemanticsProven());
        assertTrue(proof.propertyEffectsSupported());
        assertTrue(proof.noAdditionalEffects());
        assertEquals(MATERIAL, proof.materialOwner());
        assertEquals("field_151576_e", proof.materialField());

        assertNotNull(proof.properties());
        assertEquals(2.0F, proof.properties().destroyTime());
        assertEquals(6.0F, proof.properties().explosionResistance());
        assertEquals("STONE", proof.properties().soundType());
        assertEquals("STONE", proof.properties().mapColor());
        assertEquals(7, proof.properties().lightLevel());
    }

    @Test
    void rejectsUnsupportedMaterial() throws Exception {
        Path jar = tempDir.resolve("processor-block-wood.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/processor/Machine.class",
                    machine(
                            "foreign/processor/Machine",
                            "field_151575_d",
                            false,
                            false));
        }

        var proof = new LegacySingleInputProcessorBlockConstructionAnalyzer()
                .prove(jar, rule("foreign/processor/Machine"));

        assertFalse(proof.replacementProofComplete());
        assertFalse(proof.materialSemanticsProven());
        assertTrue(proof.blockers().stream().anyMatch(value ->
                value.startsWith("unsupported-source-block-material:")));
    }

    @Test
    void rejectsUnmappedConstructorSetter() throws Exception {
        Path jar = tempDir.resolve("processor-block-opacity.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/processor/Machine.class",
                    machine(
                            "foreign/processor/Machine",
                            "field_151576_e",
                            true,
                            false));
        }

        var proof = new LegacySingleInputProcessorBlockConstructionAnalyzer()
                .prove(jar, rule("foreign/processor/Machine"));

        assertFalse(proof.replacementProofComplete());
        assertFalse(proof.propertyEffectsSupported());
        assertTrue(proof.blockers().stream().anyMatch(value ->
                value.contains("setLightOpacity")
                        || value.contains("func_149713_g")));
    }

    @Test
    void rejectsConstructorControlFlow() throws Exception {
        Path jar = tempDir.resolve("processor-block-branch.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/processor/Machine.class",
                    machine(
                            "foreign/processor/Machine",
                            "field_151576_e",
                            false,
                            true));
        }

        var proof = new LegacySingleInputProcessorBlockConstructionAnalyzer()
                .prove(jar, rule("foreign/processor/Machine"));

        assertFalse(proof.replacementProofComplete());
        assertFalse(proof.constructorControlFlowSimple());
        assertTrue(proof.blockers().contains(
                "source-block-constructor-control-flow-not-simple"));
    }

    private static LegacySingleInputProcessorAnalyzer.Rule rule(String sourceBlockClass) {
        return new LegacySingleInputProcessorAnalyzer.Rule(
                "processor",
                "foreign",
                sourceBlockClass,
                "foreign/processor/Tile",
                "foreign.processor.Tile",
                3,
                64,
                0,
                List.of(1, 2),
                List.of(0),
                List.of(1, 2),
                List.of(0),
                200,
                64.0D,
                7,
                "foreign/processor/Recipes",
                "lookup",
                "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
                true,
                true,
                false);
    }

    private static byte[] machine(
            String owner,
            String materialField,
            boolean unsupportedOpacity,
            boolean branch) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                owner,
                null,
                BLOCK_CONTAINER,
                null);

        MethodVisitor init = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();

        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(
                Opcodes.GETSTATIC, MATERIAL, materialField, MATERIAL_DESC);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                BLOCK_CONTAINER,
                "<init>",
                "(" + MATERIAL_DESC + ")V",
                false);

        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(2.0F);
        init.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BLOCK,
                "func_149711_c",
                "(F)Lnet/minecraft/block/Block;",
                false);
        init.visitInsn(Opcodes.POP);

        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(10.0F);
        init.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BLOCK,
                "func_149752_b",
                "(F)Lnet/minecraft/block/Block;",
                false);
        init.visitInsn(Opcodes.POP);

        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(
                Opcodes.GETSTATIC,
                BLOCK,
                "field_149769_e",
                SOUND_DESC);
        init.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BLOCK,
                "func_149672_a",
                "(" + SOUND_DESC + ")Lnet/minecraft/block/Block;",
                false);
        init.visitInsn(Opcodes.POP);

        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(0.5F);
        init.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BLOCK,
                "func_149715_a",
                "(F)Lnet/minecraft/block/Block;",
                false);
        init.visitInsn(Opcodes.POP);

        if (unsupportedOpacity) {
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitIntInsn(Opcodes.SIPUSH, 32);
            init.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    BLOCK,
                    "func_149713_g",
                    "(I)Lnet/minecraft/block/Block;",
                    false);
            init.visitInsn(Opcodes.POP);
        }

        if (branch) {
            org.objectweb.asm.Label done = new org.objectweb.asm.Label();
            init.visitInsn(Opcodes.ICONST_0);
            init.visitJumpInsn(Opcodes.IFEQ, done);
            init.visitInsn(Opcodes.NOP);
            init.visitLabel(done);
        }

        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(
            JarOutputStream out,
            String name,
            byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
