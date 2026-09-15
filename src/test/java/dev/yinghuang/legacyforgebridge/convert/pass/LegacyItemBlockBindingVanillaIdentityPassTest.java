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

class LegacyItemBlockBindingVanillaIdentityPassTest {
    @TempDir Path tempDir;

    @Test void forge1710FarmlandFieldBecomesExactModernSoilIdentity() throws Exception {
        Path source = tempDir.resolve("seed.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "v/Crop.class", crop());
            put(out, "v/Seed.class", seed());
            put(out, "v/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),
                "{\"items\":[{\"id\":\"demo:seed\",\"legacyRegistryName\":\"seed\",\"sourceClass\":\"v/Seed\"}],"
                        + "\"blocks\":[{\"id\":\"demo:crop\",\"legacyRegistryName\":\"crop\",\"sourceClass\":\"v/Crop\"}]}\n",
                StandardCharsets.UTF_8);
        LegacyModMetadata metadata = new LegacyModMetadata("seed.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("demo", "Demo", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "seed.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");

        new LegacyItemBlockBindingPass().apply(context);
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyItemBlockBindingPass.OUTPUT))).getAsJsonObject();
        assertEquals(2, root.get("schemaVersion").getAsInt());
        assertEquals(1, root.get("modernIdentityCompleteBindings").getAsInt());
        assertEquals(1, root.get("vanillaBlockIdentityReferences").getAsInt());
        JsonObject binding = root.getAsJsonArray("bindings").get(0).getAsJsonObject();
        JsonObject target = binding.getAsJsonObject("targetBlock");
        JsonObject soil = binding.getAsJsonObject("soilBlock");
        assertEquals("demo:crop", target.get("modernId").getAsString());
        assertEquals("converted_content", target.get("modernIdentitySource").getAsString());
        assertFalse(soil.get("registered").getAsBoolean());
        assertEquals("net/minecraft/init/Blocks", soil.get("sourceFieldOwner").getAsString());
        assertEquals("field_150458_ak", soil.get("sourceFieldName").getAsString());
        assertEquals("minecraft:farmland", soil.get("modernId").getAsString());
        assertEquals("vanilla_blocks_field", soil.get("modernIdentitySource").getAsString());
    }

    private static byte[] crop() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "v/Crop", null, "net/minecraft/block/BlockCrops", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/BlockCrops", "<init>", "()V", false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0, 0); c.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] seed() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "v/Seed", null, "net/minecraft/item/ItemSeeds", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitFieldInsn(Opcodes.GETSTATIC, "v/Bootstrap", "CROP", "Lnet/minecraft/block/Block;");
        c.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/init/Blocks", "field_150458_ak", "Lnet/minecraft/block/Block;");
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemSeeds", "<init>",
                "(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V", false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0, 0); c.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "v/Bootstrap", null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "CROP", "Lnet/minecraft/block/Block;", null, null).visitEnd();

        MethodVisitor bind = w.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bindBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", null, null);
        bind.visitCode();
        bind.visitVarInsn(Opcodes.ALOAD, 0); bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        bind.visitVarInsn(Opcodes.ALOAD, 0); bind.visitInsn(Opcodes.ARETURN);
        bind.visitMaxs(0, 0); bind.visitEnd();

        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, "v/Crop"); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "v/Crop", "<init>", "()V", false);
        m.visitLdcInsn("crop");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "v/Bootstrap", "bindBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        m.visitFieldInsn(Opcodes.PUTSTATIC, "v/Bootstrap", "CROP", "Lnet/minecraft/block/Block;");
        m.visitTypeInsn(Opcodes.NEW, "v/Seed"); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "v/Seed", "<init>", "()V", false);
        m.visitLdcInsn("seed");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
