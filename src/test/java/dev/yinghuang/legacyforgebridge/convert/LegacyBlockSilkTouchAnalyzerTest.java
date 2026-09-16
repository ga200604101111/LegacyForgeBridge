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

class LegacyBlockSilkTouchAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void provesForgeDefaultSilkBranchAndSeparatesEligibilityFromStackConstruction() throws Exception {
        Path jar = tempDir.resolve("SilkProof.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/silk/Plain.class", block("foreign/silk/Plain", Kind.PLAIN, null));
            put(out, "foreign/silk/TileProvider.class", block("foreign/silk/TileProvider", Kind.PLAIN,
                    new String[]{"net/minecraft/block/ITileEntityProvider"}));
            put(out, "foreign/silk/RenderOverride.class", block("foreign/silk/RenderOverride", Kind.RENDER, null));
            put(out, "foreign/silk/TileOverride.class", block("foreign/silk/TileOverride", Kind.HAS_TILE, null));
            put(out, "foreign/silk/SilkOverride.class", block("foreign/silk/SilkOverride", Kind.SILK, null));
            put(out, "foreign/silk/StackOverride.class", block("foreign/silk/StackOverride", Kind.STACKED, null));
            put(out, "foreign/silk/Bootstrap.class", bootstrap(false));
        }

        Map<String, LegacyBlockSilkTouchAnalyzer.Proof> proofs = new LegacyBlockSilkTouchAnalyzer().analyze(jar)
                .proofs().stream().collect(Collectors.toMap(LegacyBlockSilkTouchAnalyzer.Proof::registryName, value -> value));
        assertEquals(6, proofs.size());

        var plain = proofs.get("plain");
        assertTrue(plain.eligibilityProofComplete());
        assertEquals(Boolean.TRUE, plain.silkEligible());
        assertTrue(plain.stackedItemProofComplete());
        assertEquals(0, plain.stackedLegacyDamage());

        var tileProvider = proofs.get("tile_provider");
        assertTrue(tileProvider.eligibilityProofComplete());
        assertEquals(Boolean.FALSE, tileProvider.silkEligible());
        assertTrue(tileProvider.stackedItemProofComplete());

        assertEligibilityIncomplete(proofs.get("render_override"), "renderAsNormalBlock");
        assertEligibilityIncomplete(proofs.get("tile_override"), "hasTileEntity(metadata)");
        assertEligibilityIncomplete(proofs.get("silk_override"), "canSilkHarvest");

        var stacked = proofs.get("stack_override");
        assertTrue(stacked.eligibilityProofComplete());
        assertEquals(Boolean.TRUE, stacked.silkEligible());
        assertFalse(stacked.stackedItemProofComplete());
        assertNull(stacked.stackedLegacyDamage());
        assertTrue(stacked.stackedItemReasons().stream().anyMatch(reason -> reason.contains("createStackedBlock")));
    }

    @Test
    void unscopedGetItemFromBlockSubtypeMutationGatesDefaultStackMetadataProof() throws Exception {
        Path jar = tempDir.resolve("SilkSubtypeMutation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/silkmutation/Plain.class", block("foreign/silkmutation/Plain", Kind.PLAIN, null));
            put(out, "foreign/silkmutation/Bootstrap.class", bootstrap(true));
        }
        var proof = new LegacyBlockSilkTouchAnalyzer().analyze(jar).proofs().getFirst();
        assertTrue(proof.eligibilityProofComplete());
        assertEquals(Boolean.TRUE, proof.silkEligible());
        assertFalse(proof.stackedItemProofComplete());
        assertTrue(proof.stackedItemReasons().stream()
                .anyMatch(reason -> reason.contains("unscoped Item.setHasSubtypes mutation")));
    }

    private static void assertEligibilityIncomplete(LegacyBlockSilkTouchAnalyzer.Proof proof, String fragment) {
        assertFalse(proof.eligibilityProofComplete());
        assertNull(proof.silkEligible());
        assertTrue(proof.eligibilityReasons().stream().anyMatch(reason -> reason.contains(fragment)),
                () -> proof.eligibilityReasons().toString());
    }

    private static byte[] block(String owner, Kind kind, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", interfaces);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN); end(init);
        switch (kind) {
            case PLAIN -> { }
            case RENDER -> intReturn(writer, "func_149686_d", "()Z");
            case HAS_TILE -> intReturn(writer, "hasTileEntity", "(I)Z");
            case SILK -> intReturn(writer, "func_149700_E", "()Z");
            case STACKED -> objectReturn(writer, "func_149644_j", "(I)Lnet/minecraft/item/ItemStack;");
        }
        writer.visitEnd(); return writer.toByteArray();
    }

    private static void intReturn(ClassWriter writer, String name, String descriptor) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode(); method.visitInsn(Opcodes.ICONST_1); method.visitInsn(Opcodes.IRETURN); end(method);
    }
    private static void objectReturn(ClassWriter writer, String name, String descriptor) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PROTECTED, name, descriptor, null, null);
        method.visitCode(); method.visitInsn(Opcodes.ACONST_NULL); method.visitInsn(Opcodes.ARETURN); end(method);
    }

    private static byte[] bootstrap(boolean mutateBlockItemSubtype) {
        String owner = mutateBlockItemSubtype ? "foreign/silkmutation/Bootstrap" : "foreign/silk/Bootstrap";
        String blockOwner = mutateBlockItemSubtype ? "foreign/silkmutation/Plain" : null;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        if (mutateBlockItemSubtype) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "block",
                "Lnet/minecraft/block/Block;", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd(); method.visitCode();
        if (mutateBlockItemSubtype) {
            method.visitTypeInsn(Opcodes.NEW, blockOwner); method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, blockOwner, "<init>", "()V", false);
            method.visitInsn(Opcodes.DUP); method.visitLdcInsn("plain");
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                    "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
            method.visitFieldInsn(Opcodes.PUTSTATIC, owner, "block", "Lnet/minecraft/block/Block;");
            method.visitFieldInsn(Opcodes.GETSTATIC, owner, "block", "Lnet/minecraft/block/Block;");
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/Item", "func_150898_a",
                    "(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;", false);
            method.visitInsn(Opcodes.ICONST_1);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/Item", "func_77627_a",
                    "(Z)Lnet/minecraft/item/Item;", false);
            method.visitInsn(Opcodes.POP);
        } else {
            register(method, "foreign/silk/Plain", "plain");
            register(method, "foreign/silk/TileProvider", "tile_provider");
            register(method, "foreign/silk/RenderOverride", "render_override");
            register(method, "foreign/silk/TileOverride", "tile_override");
            register(method, "foreign/silk/SilkOverride", "silk_override");
            register(method, "foreign/silk/StackOverride", "stack_override");
        }
        method.visitInsn(Opcodes.RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String name) {
        method.visitTypeInsn(Opcodes.NEW, owner); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }
    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
    private enum Kind { PLAIN, RENDER, HAS_TILE, SILK, STACKED }
}
