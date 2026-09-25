package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlantingItemBehaviorAnalyzerTest {
    @TempDir Path tempDir;

    @Test void sourceInstanceMethodsFailClosedWhilePureSeedLineageIsAccepted() throws Exception {
        Path jar = tempDir.resolve("PlantingBehavior.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "pkg/Crop.class", block("pkg/Crop"));
            put(out, "pkg/Soil.class", block("pkg/Soil"));
            put(out, "pkg/PureSeed.class", seed("pkg/PureSeed", false));
            put(out, "pkg/CustomSeed.class", seed("pkg/CustomSeed", true));
            put(out, "pkg/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyPlantingItemBehaviorAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(2, analysis.rules().size());
        Map<String,LegacyPlantingItemBehaviorAnalyzer.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyPlantingItemBehaviorAnalyzer.Rule::registryName, Function.identity()));
        assertTrue(rules.get("pure_seed").inheritedVanillaPlacement());
        assertTrue(rules.get("pure_seed").sourceInstanceMethods().isEmpty());
        assertFalse(rules.get("custom_seed").inheritedVanillaPlacement());
        assertEquals(1, rules.get("custom_seed").sourceInstanceMethods().size());
        assertEquals("pkg/CustomSeed#customHook()V", rules.get("custom_seed").sourceInstanceMethods().getFirst());
    }

    private static byte[] seed(String name, boolean custom) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemSeeds", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitFieldInsn(Opcodes.GETSTATIC, "pkg/Bootstrap", "CROP", "Lnet/minecraft/block/Block;");
        c.visitFieldInsn(Opcodes.GETSTATIC, "pkg/Bootstrap", "SOIL", "Lnet/minecraft/block/Block;");
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemSeeds", "<init>",
                "(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V", false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0, 0); c.visitEnd();
        if (custom) {
            MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "customHook", "()V", null, null);
            m.visitCode(); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
        }
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] block(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0); c.visitInsn(Opcodes.ACONST_NULL);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0, 0); c.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "pkg/Bootstrap", null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "CROP", "Lnet/minecraft/block/Block;", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "SOIL", "Lnet/minecraft/block/Block;", null, null).visitEnd();
        MethodVisitor bind = w.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bindBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", null, null);
        bind.visitCode(); bind.visitVarInsn(Opcodes.ALOAD, 0); bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        bind.visitVarInsn(Opcodes.ALOAD, 0); bind.visitInsn(Opcodes.ARETURN); bind.visitMaxs(0, 0); bind.visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null); m.visitCode();
        bindBlock(m, "pkg/Crop", "crop", "CROP"); bindBlock(m, "pkg/Soil", "soil", "SOIL");
        registerItem(m, "pkg/PureSeed", "pure_seed"); registerItem(m, "pkg/CustomSeed", "custom_seed");
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static void bindBlock(MethodVisitor m, String type, String id, String field) {
        m.visitTypeInsn(Opcodes.NEW, type); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false); m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Bootstrap", "bindBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        m.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Bootstrap", field, "Lnet/minecraft/block/Block;");
    }

    private static void registerItem(MethodVisitor m, String type, String id) {
        m.visitTypeInsn(Opcodes.NEW, type); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false); m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
