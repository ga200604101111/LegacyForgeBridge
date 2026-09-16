package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockMaterialProvenancePassTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC = "Lnet/minecraft/block/material/Material;";

    @TempDir Path tempDir;

    @Test
    void writesRawMaterialEvidenceWithoutAssigningHarvestSemantics() throws Exception {
        Path jar = tempDir.resolve("MaterialSidecar.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/materialsidecar/Plain.class", plainBlock());
            put(out, "foreign/materialsidecar/Unknown.class", unknownBlock());
            put(out, "foreign/materialsidecar/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        LegacyBlockMaterialProvenancePass pass = new LegacyBlockMaterialProvenancePass();
        assertEquals("legacy-block-material-provenance", pass.id());
        pass.apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockMaterialProvenancePass.OUTPUT_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals("sha", root.get("sourceSha256").getAsString());
        assertEquals(1, root.get("completeBlocks").getAsInt());
        assertEquals(1, root.get("incompleteBlocks").getAsInt());
        assertEquals(0, root.getAsJsonArray("diagnostics").size());

        Map<String, JsonObject> blocks = new HashMap<>();
        for (var element : root.getAsJsonArray("blocks")) {
            JsonObject block = element.getAsJsonObject();
            blocks.put(block.get("legacyRegistryName").getAsString(), block);
        }
        assertEquals(2, blocks.size());

        JsonObject plain = blocks.get("plain");
        assertTrue(plain.get("complete").getAsBoolean());
        assertEquals("foreign/materialsidecar/Plain", plain.get("sourceClass").getAsString());
        assertEquals("foreign/materialsidecar/Plain", plain.get("directBlockSourceClass").getAsString());
        assertEquals(0, plain.getAsJsonArray("reasons").size());
        JsonObject material = plain.getAsJsonObject("material");
        assertEquals(MATERIAL, material.get("owner").getAsString());
        assertEquals("field_151575_d", material.get("fieldName").getAsString());
        assertEquals(MATERIAL_DESC, material.get("descriptor").getAsString());
        assertFalse(plain.has("toolRequired"));
        assertFalse(plain.has("harvestLevel"));
        assertFalse(plain.has("modernTag"));

        JsonObject unknown = blocks.get("unknown");
        assertFalse(unknown.get("complete").getAsBoolean());
        assertFalse(unknown.has("material"));
        JsonArray reasons = unknown.getAsJsonArray("reasons");
        assertFalse(reasons.isEmpty());
        assertTrue(reasons.get(0).getAsString().contains("not one direct static Material field"));
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] plainBlock() {
        String owner = "foreign/materialsidecar/Plain";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, "field_151575_d", MATERIAL_DESC);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] unknownBlock() {
        String owner = "foreign/materialsidecar/Unknown";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/materialsidecar/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/materialsidecar/Plain", "plain");
        register(method, "foreign/materialsidecar/Unknown", "unknown");
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
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
