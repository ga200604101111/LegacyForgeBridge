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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorBlockAllocationAnalyzerTest {
    private static final String EVENT =
            "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String BLOCK_CONTAINER =
            "net/minecraft/block/BlockContainer";
    private static final String MACHINE = "foreign/machine/MachineBlock";

    @TempDir Path tempDir;

    @Test
    void provesInlineNoArgAllocationAndConstantFluentEffects() throws Exception {
        Path jar = tempDir.resolve("inline-allocation.jar");
        try (JarOutputStream out =
                     new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, MACHINE + ".class", block());
            put(out, "foreign/machine/Bootstrap.class", bootstrap(false));
        }

        var proof = new LegacySingleInputProcessorBlockAllocationAnalyzer()
                .prove(jar, rule());

        assertTrue(proof.allocationProofComplete(), proof.blockers().toString());
        assertTrue(proof.inlineAllocationProven());
        assertTrue(proof.allocationControlFlowSimple());
        assertTrue(proof.allocationSetterEffectsSupported());
        assertEquals("()V", proof.sourceConstructor());
        assertEquals(2, proof.effects().size());

        var hardness = proof.effects().get(0);
        assertEquals(
                LegacySingleInputProcessorBlockAllocationAnalyzer.EffectKind.HARDNESS,
                hardness.kind());
        assertEquals(3.0F, hardness.floatValue());
        assertEquals(MACHINE, hardness.owner());

        var sound = proof.effects().get(1);
        assertEquals(
                LegacySingleInputProcessorBlockAllocationAnalyzer.EffectKind.SOUND,
                sound.kind());
        assertEquals("METAL", sound.textValue());
    }

    @Test
    void rejectsAllocationStoredInLocalBeforeRegistration() throws Exception {
        Path jar = tempDir.resolve("local-allocation.jar");
        try (JarOutputStream out =
                     new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, MACHINE + ".class", block());
            put(out, "foreign/machine/Bootstrap.class", bootstrap(true));
        }

        var proof = new LegacySingleInputProcessorBlockAllocationAnalyzer()
                .prove(jar, rule());

        assertFalse(proof.allocationProofComplete());
        assertFalse(proof.inlineAllocationProven());
        // Registry provenance rejects the local alias before the later expression tracer runs.
        // Keep rejection mandatory and assert the actual early constructor-proof boundary.
        assertTrue(proof.blockers().contains("source-allocation-inline-constructor-not-proven"),
                proof.blockers().toString());
    }

    private static LegacySingleInputProcessorAnalyzer.Rule rule() {
        return new LegacySingleInputProcessorAnalyzer.Rule(
                "processor",
                "foreign",
                MACHINE,
                "foreign/machine/MachineTile",
                "foreign.machine.MachineTile",
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
                "foreign/machine/Recipes",
                "lookup",
                "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
                true,
                true,
                false);
    }

    private static byte[] block() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                MACHINE,
                null,
                BLOCK_CONTAINER,
                null);
        MethodVisitor init = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                BLOCK_CONTAINER,
                "<init>",
                "(Lnet/minecraft/block/material/Material;)V",
                false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap(boolean throughLocal) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                "foreign/machine/Bootstrap",
                null,
                "java/lang/Object",
                null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "boot",
                EVENT,
                null,
                null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, MACHINE);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL, MACHINE, "<init>", "()V", false);
        method.visitLdcInsn(3.0F);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                MACHINE,
                "func_149711_c",
                "(F)Lnet/minecraft/block/Block;",
                false);
        method.visitFieldInsn(
                Opcodes.GETSTATIC,
                BLOCK,
                "field_149777_j",
                "Lnet/minecraft/block/Block$SoundType;");
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                BLOCK,
                "func_149672_a",
                "(Lnet/minecraft/block/Block$SoundType;)Lnet/minecraft/block/Block;",
                false);
        if (throughLocal) {
            method.visitVarInsn(Opcodes.ASTORE, 2);
            method.visitVarInsn(Opcodes.ALOAD, 2);
        }
        method.visitLdcInsn("processor");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(
            JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
