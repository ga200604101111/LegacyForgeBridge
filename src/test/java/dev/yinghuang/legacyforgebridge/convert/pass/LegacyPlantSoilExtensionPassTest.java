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

class LegacyPlantSoilExtensionPassTest {
    @TempDir Path tempDir;

    @Test void sidecarKeepsSourceSoilOverridesExplicitAndRuntimeClosed() throws Exception {
        Path source = tempDir.resolve("soil.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "e/Plain.class", block("e/Plain", false));
            put(out, "e/Custom.class", block("e/Custom", true));
            put(out, "e/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),
                "{\"items\":[],\"blocks\":["
                        + "{\"id\":\"demo:plain\",\"legacyRegistryName\":\"plain\",\"sourceClass\":\"e/Plain\"},"
                        + "{\"id\":\"demo:custom\",\"legacyRegistryName\":\"custom\",\"sourceClass\":\"e/Custom\"}]}\n",
                StandardCharsets.UTF_8);
        LegacyModMetadata metadata = new LegacyModMetadata("soil.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("demo", "Demo", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "soil.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");

        new LegacyPlantSoilExtensionPass().apply(context);
        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantSoilExtensionPass.OUTPUT))).getAsJsonObject();
        assertEquals(2, root.get("registeredBlocks").getAsInt());
        assertEquals(2, root.get("modernIdentityCompleteBlocks").getAsInt());
        assertEquals(1, root.get("sourceCanSustainPlantOverrides").getAsInt());
        assertEquals(1, root.get("sourceFertilityOverrides").getAsInt());
        assertEquals(0, root.get("runtimeCompleteBlocks").getAsInt());
        JsonObject plain = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("demo:plain", plain.get("modernId").getAsString());
        assertTrue(plain.get("forgeDefaultSustainInherited").getAsBoolean());
        assertTrue(plain.get("forgeDefaultFertilityInherited").getAsBoolean());
        JsonObject custom = root.getAsJsonArray("rules").get(1).getAsJsonObject();
        assertFalse(custom.get("forgeDefaultSustainInherited").getAsBoolean());
        assertFalse(custom.get("forgeDefaultFertilityInherited").getAsBoolean());
        assertEquals(1, custom.getAsJsonArray("canSustainPlantHooks").size());
        assertEquals(1, custom.getAsJsonArray("fertilityHooks").size());
    }

    private static byte[] block(String name, boolean custom) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        if (custom) {
            MethodVisitor sustain = w.visitMethod(Opcodes.ACC_PUBLIC, "canSustainPlant",
                    "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraftforge/common/IPlantable;)Z",
                    null, null);
            sustain.visitCode(); sustain.visitInsn(Opcodes.ICONST_1); sustain.visitInsn(Opcodes.IRETURN); sustain.visitMaxs(0, 0); sustain.visitEnd();
            MethodVisitor fertile = w.visitMethod(Opcodes.ACC_PUBLIC, "isFertile", "(Lnet/minecraft/world/World;III)Z", null, null);
            fertile.visitCode(); fertile.visitInsn(Opcodes.ICONST_1); fertile.visitInsn(Opcodes.IRETURN); fertile.visitMaxs(0, 0); fertile.visitEnd();
        }
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "e/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode(); register(m, "e/Plain", "plain"); register(m, "e/Custom", "custom");
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }
    private static void register(MethodVisitor m, String type, String id) {
        m.visitTypeInsn(Opcodes.NEW, type); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false); m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
