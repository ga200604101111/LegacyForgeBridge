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

class LegacySingleInputProcessorTileConstructionAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void provesFixedInventoryArrayAndJvmDefaultFieldInitialization() throws Exception {
        Path source = jar(false);
        var proof = new LegacySingleInputProcessorTileConstructionAnalyzer()
                .prove(source, rule());

        assertTrue(proof.constructorPresent());
        assertTrue(proof.constructorChainComplete(), proof.blockers().toString());
        assertTrue(proof.constructorControlFlowSimple(), proof.blockers().toString());
        assertTrue(proof.inventoryArrayInitializationProven(),
                proof.blockers().toString());
        assertTrue(proof.defaultFieldWritesOnly(), proof.blockers().toString());
        assertTrue(proof.noAdditionalMethodCalls(), proof.blockers().toString());
        assertTrue(proof.replacementProofComplete(), proof.blockers().toString());
        assertEquals(2, proof.constructorChain().size());
        assertEquals(3, proof.fieldInitializations().size());
        assertTrue(proof.fieldInitializations().stream().anyMatch(value ->
                value.kind().equals("item-stack-array")
                        && Integer.valueOf(3).equals(value.arrayLength())));
    }

    @Test
    void nonDefaultSourceFieldInitializationFailsClosed() throws Exception {
        Path source = jar(true);
        var proof = new LegacySingleInputProcessorTileConstructionAnalyzer()
                .prove(source, rule());

        assertTrue(proof.inventoryArrayInitializationProven(),
                proof.blockers().toString());
        assertFalse(proof.defaultFieldWritesOnly());
        assertFalse(proof.replacementProofComplete());
        assertTrue(proof.blockers().stream().anyMatch(value ->
                value.startsWith("source-tile-nondefault-field-write:")),
                proof.blockers().toString());
    }

    private Path jar(boolean nonDefault) throws Exception {
        Path source = tempDir.resolve(nonDefault ? "nondefault.jar" : "complete.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "foreign/machine/BaseTile.class", baseTile());
            put(out, "foreign/machine/MachineTile.class", machineTile(nonDefault));
        }
        return source;
    }

    private static LegacySingleInputProcessorAnalyzer.Rule rule() {
        return new LegacySingleInputProcessorAnalyzer.Rule(
                "processor",
                "foreign",
                "foreign/machine/MachineBlock",
                "foreign/machine/MachineTile",
                "processor_tile",
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
                "find",
                "(Lnet/minecraft/item/ItemStack;)Ljava/lang/Object;",
                true,
                true,
                false);
    }

    private static byte[] baseTile() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/BaseTile", null,
                "net/minecraft/tileentity/TileEntity", null);
        writer.visitField(Opcodes.ACC_PROTECTED,
                "progress", "I", null, null).visitEnd();

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/tileentity/TileEntity",
                "<init>",
                "()V",
                false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitInsn(Opcodes.ICONST_0);
        constructor.visitFieldInsn(
                Opcodes.PUTFIELD,
                "foreign/machine/BaseTile",
                "progress",
                "I");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] machineTile(boolean nonDefault) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/MachineTile", null,
                "foreign/machine/BaseTile", null);
        writer.visitField(Opcodes.ACC_PRIVATE,
                "items", "[Lnet/minecraft/item/ItemStack;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE,
                "mode", "I", null, null).visitEnd();

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "foreign/machine/BaseTile",
                "<init>",
                "()V",
                false);

        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitInsn(Opcodes.ICONST_3);
        constructor.visitTypeInsn(
                Opcodes.ANEWARRAY,
                "net/minecraft/item/ItemStack");
        constructor.visitFieldInsn(
                Opcodes.PUTFIELD,
                "foreign/machine/MachineTile",
                "items",
                "[Lnet/minecraft/item/ItemStack;");

        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitInsn(nonDefault ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        constructor.visitFieldInsn(
                Opcodes.PUTFIELD,
                "foreign/machine/MachineTile",
                "mode",
                "I");

        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes)
            throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
