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

class LegacyBlockHarvestSourceMaterialProofPassTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MAP_COLOR = "net/minecraft/block/material/MapColor";
    private static final String CUSTOM = "foreign/sourceharvest/CustomMaterial";
    private static final String BLOCK = "foreign/sourceharvest/CustomBlock";
    @TempDir Path tempDir;

    @Test
    void sourceOwnedDefaultMaterialCompletesNoToolHarvestFastPath() throws Exception {
        Path jar = tempDir.resolve("source-material.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, CUSTOM + ".class", customMaterial());
            put(out, BLOCK + ".class", block());
            put(out, "foreign/sourceharvest/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:source_material", "fixture:source_material");
        context.recordRegistryIdentity("items", "fixture:source_material", "fixture:source_material");
        new LegacyBlockDropAnalysisPass().apply(context);
        new LegacyBlockHarvestMaterialProofPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(6, root.get("schemaVersion").getAsInt());
        assertEquals(LegacyBlockHarvestMaterialProofPass.RULE_VERSION,
                root.get("materialHarvestRuleVersion").getAsString());
        assertEquals(1, root.get("harvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(0, root.getAsJsonArray("sourceMaterialHarvestAnalysisDiagnostics").size());

        JsonObject plan = root.getAsJsonArray("plans").get(0).getAsJsonObject();
        assertTrue(plan.get("legacyMaterialProvenanceComplete").getAsBoolean());
        assertTrue(plan.get("legacyMaterialHarvestRuleKnown").getAsBoolean());
        assertTrue(plan.get("materialFastPathHarvestEligibilityProofComplete").getAsBoolean());
        assertTrue(plan.get("harvestEligibilityProofComplete").getAsBoolean());
        assertEquals("source_material_tool_not_required_1_7_10",
                plan.get("harvestEligibilityMode").getAsString());
        JsonObject material = plan.getAsJsonObject("legacyMaterial");
        assertEquals(CUSTOM, material.get("owner").getAsString());
        assertEquals("instance", material.get("fieldName").getAsString());
        assertEquals("L" + CUSTOM + ";", material.get("descriptor").getAsString());
        assertTrue(material.get("toolNotRequired").getAsBoolean());
        assertTrue(material.get("sourceOwnedRule").getAsBoolean());
        assertEquals("direct_material_default_without_set_requires_tool",
                material.get("sourceProofMode").getAsString());
        assertFalse(contains(plan, LegacyBlockHarvestMaterialProofPass.MATERIAL_RULE_BLOCKER));
        assertFalse(contains(plan, LegacyBlockHarvestMaterialProofPass.TOOL_PLAYER_ROUTE_BLOCKER));
    }

    private static boolean contains(JsonObject plan, String blocker) {
        for (var value : plan.getAsJsonArray("runtimeBlockers")) {
            if (blocker.equals(value.getAsString())) return true;
        }
        return false;
    }

    private static byte[] customMaterial() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, CUSTOM, null, MATERIAL, null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "instance", "L" + CUSTOM + ";", null, null).visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, MATERIAL, "<init>", "(L" + MAP_COLOR + ";)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MATERIAL, "func_76219_n", "()L" + MATERIAL + ";", false);
        init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitTypeInsn(Opcodes.NEW, CUSTOM);
        clinit.visitInsn(Opcodes.DUP);
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, CUSTOM, "<init>", "()V", false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, CUSTOM, "instance", "L" + CUSTOM + ";");
        clinit.visitInsn(Opcodes.RETURN);
        end(clinit);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] block() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, BLOCK, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, CUSTOM, "instance", "L" + CUSTOM + ";");
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(L" + MATERIAL + ";)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/sourceharvest/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, BLOCK);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, BLOCK, "<init>", "()V", false);
        method.visitLdcInsn("source_material");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, analysis, new DiagnosticCollector(), "generic-test");
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
