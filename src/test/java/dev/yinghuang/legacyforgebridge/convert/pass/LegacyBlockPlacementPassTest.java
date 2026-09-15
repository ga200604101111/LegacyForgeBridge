package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockPlacementPassTest {
    @TempDir Path tempDir;

    @Test void emitsPureItemBlockToBlockPipelineAndSkipsCustomPlaceBlockAt() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/place/Panel.class", block("foreign/place/Panel"));
            put(out, "foreign/place/ExtendedPanel.class", block("foreign/place/ExtendedPanel"));
            put(out, "foreign/item/MaskItemBlock.class", maskItemBlock());
            put(out, "foreign/item/ExtendedItemBlock.class", extendedItemBlock());
            put(out, "foreign/place/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:panel", "fixture:panel");
        context.recordRegistryIdentity("blocks", "fixture:extended", "fixture:extended");
        new LegacyBlockPlacementPass().apply(context);

        Path rulesPath = tempDir.resolve("staging/" + LegacyBlockPlacementPass.RULES_PATH);
        assertTrue(Files.isRegularFile(rulesPath));
        JsonObject root = JsonParser.parseString(Files.readString(rulesPath, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.getAsJsonArray("rules").size());
        assertEquals(1, root.get("skippedRules").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("fixture:panel", rule.get("id").getAsString());
        assertEquals("IAND", rule.getAsJsonObject("itemMetadata")
                .getAsJsonArray("instructions").get(2).getAsJsonObject().get("op").getAsString());
        assertEquals("IOR", rule.getAsJsonObject("blockPlacement")
                .getAsJsonArray("instructions").get(2).getAsJsonObject().get("op").getAsString());
        assertTrue(context.diagnostics().snapshot().stream()
                .anyMatch(value -> "LFB-CONVERT-BLOCK-PLACEMENT-0004".equals(value.ruleId())));
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] block(String name) {
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

        MethodVisitor placed = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149660_a",
                "(Lnet/minecraft/world/World;IIIIFFFI)I", null, null);
        placed.visitCode();
        placed.visitVarInsn(Opcodes.ILOAD, 9);
        placed.visitIntInsn(Opcodes.BIPUSH, 8);
        placed.visitInsn(Opcodes.IOR);
        placed.visitInsn(Opcodes.IRETURN);
        placed.visitMaxs(2, 10);
        placed.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] maskItemBlock() {
        ClassWriter w = itemBlockClass("foreign/item/MaskItemBlock");
        MethodVisitor metadata = w.visitMethod(Opcodes.ACC_PUBLIC, "func_77647_b", "(I)I", null, null);
        metadata.visitCode();
        metadata.visitVarInsn(Opcodes.ILOAD, 1);
        metadata.visitIntInsn(Opcodes.BIPUSH, 7);
        metadata.visitInsn(Opcodes.IAND);
        metadata.visitInsn(Opcodes.IRETURN);
        metadata.visitMaxs(2, 2);
        metadata.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] extendedItemBlock() {
        ClassWriter w = itemBlockClass("foreign/item/ExtendedItemBlock");
        MethodVisitor metadata = w.visitMethod(Opcodes.ACC_PUBLIC, "getMetadata", "(I)I", null, null);
        metadata.visitCode();
        metadata.visitVarInsn(Opcodes.ILOAD, 1);
        metadata.visitInsn(Opcodes.IRETURN);
        metadata.visitMaxs(1, 2);
        metadata.visitEnd();
        MethodVisitor place = w.visitMethod(Opcodes.ACC_PUBLIC, "placeBlockAt",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/world/World;IIIIFFFI)Z", null, null);
        place.visitCode();
        place.visitInsn(Opcodes.ICONST_1);
        place.visitInsn(Opcodes.IRETURN);
        place.visitMaxs(1, 13);
        place.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static ClassWriter itemBlockClass(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemBlock", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/block/Block;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemBlock", "<init>",
                "(Lnet/minecraft/block/Block;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        return w;
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/place/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        register(m, "foreign/place/Panel", "foreign/item/MaskItemBlock", "panel");
        register(m, "foreign/place/ExtendedPanel", "foreign/item/ExtendedItemBlock", "extended");
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void register(MethodVisitor m, String blockClass, String itemBlockClass, String name) {
        m.visitTypeInsn(Opcodes.NEW, blockClass);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        m.visitLdcInsn(Type.getObjectType(itemBlockClass));
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/Class;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        m.visitInsn(Opcodes.POP);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
