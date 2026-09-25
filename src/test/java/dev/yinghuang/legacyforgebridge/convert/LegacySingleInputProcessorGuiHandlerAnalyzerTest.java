package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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

class LegacySingleInputProcessorGuiHandlerAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void provesSameHandlerServerAndClientBranchesFromExactGuiIdDispatch()
            throws Exception {
        Path jar = jar(false);
        var analysis = new LegacySingleInputProcessorGuiHandlerAnalyzer()
                .analyze(jar, rule(), "foreign/gui/ProcessorGui");

        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        var proof = analysis.proofs().getFirst();
        assertTrue(proof.branchRetirementProofComplete(), proof.blockers().toString());
        assertTrue(proof.sameHandlerProven());
        assertEquals("foreign/gui/Handler", proof.handlerClass());
        assertEquals("foreign/gui/ProcessorMenu", proof.sourceContainerClass());
        assertEquals("foreign/gui/ProcessorGui", proof.sourceGuiClass());
        assertEquals("TABLE", proof.serverBranch().switchKind());
        assertEquals("TABLE", proof.clientBranch().switchKind());
        assertTrue(proof.serverBranch().uniqueCaseLabelProven());
        assertTrue(proof.clientBranch().uniqueCaseLabelProven());
        assertTrue(proof.serverBranch().tileHandoffProven());
        assertTrue(proof.clientBranch().tileHandoffProven());
        assertEquals(0, proof.serverBranch().otherCaseCount());
        assertEquals(0, proof.clientBranch().otherCaseCount());
    }

    @Test
    void sharedCaseLabelFailsClosed() throws Exception {
        Path jar = jar(true);
        var proof = new LegacySingleInputProcessorGuiHandlerAnalyzer()
                .analyze(jar, rule(), "foreign/gui/ProcessorGui")
                .proofs().getFirst();

        assertFalse(proof.branchRetirementProofComplete());
        assertFalse(proof.sameHandlerProven());
        assertTrue(proof.blockers().stream().anyMatch(value ->
                value.startsWith(
                        "processor-gui-handler-server-client-case-pair-not-proven")));
    }

    private Path jar(boolean sharedTarget) throws Exception {
        Path jar = tempDir.resolve(sharedTarget ? "shared.jar" : "complete.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/gui/Handler.class", handler(sharedTarget));
            put(out, "foreign/gui/ProcessorMenu.class",
                    simple("foreign/gui/ProcessorMenu",
                            "net/minecraft/inventory/Container", null));
            put(out, "foreign/gui/ProcessorGui.class",
                    simple("foreign/gui/ProcessorGui",
                            "java/lang/Object", null));
        }
        return jar;
    }

    private static LegacySingleInputProcessorAnalyzer.Rule rule() {
        return new LegacySingleInputProcessorAnalyzer.Rule(
                "processor",
                "foreign",
                "foreign/gui/ProcessorBlock",
                "foreign/gui/ProcessorTile",
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
                "foreign/gui/Recipes",
                "find",
                "(Lnet/minecraft/item/ItemStack;)Ljava/lang/Object;",
                true,
                true,
                false);
    }

    private static byte[] handler(boolean sharedTarget) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                "foreign/gui/Handler",
                null,
                "java/lang/Object",
                new String[]{"cpw/mods/fml/common/network/IGuiHandler"});
        handlerMethod(writer, "getServerGuiElement",
                "foreign/gui/ProcessorMenu", sharedTarget);
        handlerMethod(writer, "getClientGuiElement",
                "foreign/gui/ProcessorGui", sharedTarget);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void handlerMethod(
            ClassWriter writer,
            String methodName,
            String constructedClass,
            boolean sharedTarget) {
        String descriptor =
                "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;";
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, methodName, descriptor, null, null);
        method.visitCode();

        Label hit = new Label();
        Label miss = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 1);
        if (sharedTarget) {
            method.visitTableSwitchInsn(7, 8, miss, hit, hit);
        } else {
            method.visitTableSwitchInsn(7, 7, miss, hit);
        }

        method.visitLabel(hit);
        method.visitTypeInsn(Opcodes.NEW, constructedClass);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitFieldInsn(
                Opcodes.GETFIELD,
                "net/minecraft/entity/player/EntityPlayer",
                "field_71071_by",
                "Lnet/minecraft/entity/player/InventoryPlayer;");
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/World",
                "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;",
                false);
        method.visitTypeInsn(Opcodes.CHECKCAST, "foreign/gui/ProcessorTile");
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                constructedClass,
                "<init>",
                LegacyGuiTileHandoff.CONSTRUCTOR,
                false);
        method.visitInsn(Opcodes.ARETURN);

        method.visitLabel(miss);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static byte[] simple(
            String name, String parent, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                name, null, parent, interfaces);
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
