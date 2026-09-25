package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

class LegacyPlantPresentationPassTest {
    @TempDir Path tempDir;

    @Test void provenPlantAssetsReplaceCubeFallbackWithCrossModelsWhileRuntimeStaysClosed() throws Exception {
        Path source = tempDir.resolve("plants.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "q/Crop.class", block("q/Crop", "net/minecraft/block/BlockCrops", "plants:herb"));
            put(out, "q/MissingCrop.class", block("q/MissingCrop", "net/minecraft/block/BlockCrops", "plants:missing"));
            put(out, "q/Reed.class", block("q/Reed", "net/minecraft/block/BlockReed", "plants:reed"));
            put(out, "q/Bootstrap.class", bootstrap());
        }

        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),
                "{\"items\":[],\"blocks\":["
                        + "{\"id\":\"foreign:pure_crop\",\"legacyRegistryName\":\"pure_crop\",\"sourceClass\":\"q/Crop\"},"
                        + "{\"id\":\"foreign:missing_crop\",\"legacyRegistryName\":\"missing_crop\",\"sourceClass\":\"q/MissingCrop\"},"
                        + "{\"id\":\"foreign:reed\",\"legacyRegistryName\":\"reed\",\"sourceClass\":\"q/Reed\"}]}\n",
                StandardCharsets.UTF_8);
        for (int i = 0; i < 8; i++) texture(staging, "plants", "herb_stage_" + i);
        for (int i = 0; i < 7; i++) texture(staging, "plants", "missing_stage_" + i);
        texture(staging, "plants", "reed");

        LegacyModMetadata metadata = new LegacyModMetadata("plants.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "plants.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyPlantPresentationPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantPresentationPass.OUTPUT))).getAsJsonObject();
        assertEquals(3, root.get("classifiedBlocks").getAsInt());
        assertEquals(3, root.get("textureNameProofCompleteBlocks").getAsInt());
        assertEquals(2, root.get("assetProofCompleteBlocks").getAsInt());
        assertEquals(2, root.get("presentationCompleteBlocks").getAsInt());
        assertEquals(0, root.get("runtimeCompleteBlocks").getAsInt());

        JsonObject crop = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(crop.get("presentationComplete").getAsBoolean());
        assertTrue(crop.get("cutoutRuntimeComplete").getAsBoolean());
        assertFalse(crop.get("runtimeComplete").getAsBoolean());
        assertEquals("cross_stage_0_7", crop.get("modelFamily").getAsString());

        JsonObject stage7 = JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/models/block/pure_crop_stage_7.json"))).getAsJsonObject();
        assertEquals("minecraft:block/cross", stage7.get("parent").getAsString());
        assertEquals("plants:blocks/herb_stage_7", stage7.getAsJsonObject("textures").get("cross").getAsString());
        JsonObject cropVariants = JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/blockstates/pure_crop.json"))).getAsJsonObject().getAsJsonObject("variants");
        assertEquals(16, cropVariants.size());
        assertTrue(cropVariants.has("legacy_meta=0"));
        assertTrue(cropVariants.has("legacy_meta=7"));
        assertTrue(cropVariants.has("legacy_meta=8"));
        assertTrue(cropVariants.has("legacy_meta=15"));
        assertEquals("foreign:block/pure_crop_stage_0",
                cropVariants.getAsJsonObject("legacy_meta=0").get("model").getAsString());
        assertEquals("foreign:block/pure_crop_stage_7",
                cropVariants.getAsJsonObject("legacy_meta=7").get("model").getAsString());
        assertEquals("foreign:block/pure_crop_stage_7",
                cropVariants.getAsJsonObject("legacy_meta=8").get("model").getAsString());
        assertEquals("foreign:block/pure_crop_stage_7",
                cropVariants.getAsJsonObject("legacy_meta=15").get("model").getAsString());

        JsonObject missing = root.getAsJsonArray("rules").get(1).getAsJsonObject();
        assertFalse(missing.get("assetProofComplete").getAsBoolean());
        assertEquals("missing-stage-7", missing.get("assetProofReason").getAsString());
        assertFalse(missing.get("presentationComplete").getAsBoolean());
        assertFalse(Files.exists(staging.resolve("assets/foreign/models/block/missing_crop_stage_0.json")));

        JsonObject reed = root.getAsJsonArray("rules").get(2).getAsJsonObject();
        assertTrue(reed.get("presentationComplete").getAsBoolean());
        JsonObject reedModel = JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/models/block/reed.json"))).getAsJsonObject();
        assertEquals("plants:blocks/reed", reedModel.getAsJsonObject("textures").get("cross").getAsString());
        JsonObject reedVariants = JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/blockstates/reed.json"))).getAsJsonObject().getAsJsonObject("variants");
        assertEquals(16, reedVariants.size());
        for (int meta = 0; meta < 16; meta++) {
            assertEquals("foreign:block/reed",
                    reedVariants.getAsJsonObject("legacy_meta=" + meta).get("model").getAsString());
        }
    }

    private static byte[] block(String name, String superName, String texture) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, superName, null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        c.visitVarInsn(Opcodes.ALOAD, 0); c.visitLdcInsn(texture);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/block/Block", "setBlockTextureName",
                "(Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        c.visitInsn(Opcodes.POP); c.visitInsn(Opcodes.RETURN); c.visitMaxs(0, 0); c.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "q/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        register(m, "q/Crop", "pure_crop");
        register(m, "q/MissingCrop", "missing_crop");
        register(m, "q/Reed", "reed");
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static void register(MethodVisitor m, String type, String id) {
        m.visitTypeInsn(Opcodes.NEW, type); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false);
        m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void texture(Path staging, String namespace, String name) throws Exception {
        Path file = staging.resolve("assets/" + namespace + "/textures/blocks/" + name + ".png");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
