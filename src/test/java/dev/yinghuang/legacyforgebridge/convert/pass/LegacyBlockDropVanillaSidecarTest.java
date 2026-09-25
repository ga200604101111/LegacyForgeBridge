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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropVanillaSidecarTest {
    @TempDir Path tempDir;

    @Test void vanillaDropTargetKeepsLegacyIdentityWithoutFalseConvertedRegistryWarningOrPrematureModernId() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/vanillasidecar/StickOre.class", stickOre());
            put(out, "foreign/vanillasidecar/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:stick_ore", "fixture:stick_ore");
        new LegacyBlockDropAnalysisPass().apply(context);

        Path sidecar = tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.ANALYSIS_PATH);
        assertTrue(Files.isRegularFile(sidecar));
        JsonObject root = JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject block = root.getAsJsonArray("blocks").get(0).getAsJsonObject();
        assertEquals("fixture:stick_ore", block.get("id").getAsString());

        JsonObject item = block.getAsJsonObject("item");
        assertEquals("ITEM", item.get("kind").getAsString());
        assertEquals("minecraft", item.get("legacyNamespace").getAsString());
        assertEquals("stick", item.get("legacyRegistryName").getAsString());
        assertEquals("net/minecraft/init/Items", item.get("sourceFieldOwner").getAsString());
        assertEquals("field_151055_y", item.get("sourceFieldName").getAsString());
        assertFalse(item.has("id"), "Vanilla drop target must wait for metadata-aware DFU flattening");
        assertTrue(root.getAsJsonArray("diagnostics").isEmpty(), root.getAsJsonArray("diagnostics").toString());
        assertFalse(context.diagnostics().snapshot().stream().anyMatch(value ->
                value.ruleId().equals("LFB-CONVERT-BLOCK-DROP-0002")), context.diagnostics().snapshot().toString());
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

    private static byte[] stickOre() {
        String owner = "foreign/vanillasidecar/StickOre";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);

        MethodVisitor drop = writer.visitMethod(Opcodes.ACC_PUBLIC, "getItemDropped",
                "(ILjava/util/Random;I)Lnet/minecraft/item/Item;", null, null);
        drop.visitCode();
        drop.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/init/Items", "field_151055_y",
                "Lnet/minecraft/item/Item;");
        drop.visitInsn(Opcodes.ARETURN);
        end(drop);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/vanillasidecar/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/vanillasidecar/StickOre");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/vanillasidecar/StickOre", "<init>", "()V", false);
        method.visitLdcInsn("stick_ore");
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
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
}
