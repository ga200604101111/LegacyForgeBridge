package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionResult;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockDropAnalysisPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockDropRuntimeReadinessPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockHarvestMaterialProofPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockMaterialProvenancePass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockDropCandidateIntegrationTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC = "Lnet/minecraft/block/material/Material;";

    @TempDir Path tempDir;

    @Test
    void fullConversionEmbedsSchemaSixHarvestProofAndStaticDropReadinessWithoutRuntime() throws Exception {
        Path source = tempDir.resolve("HarvestFastPathLegacy.jar");
        String metadata = "[{\"modid\":\"harvestfast\",\"name\":\"Harvest Fast\",\"version\":\"1.0\",\"mcversion\":\"1.7.10\",\"dependencies\":[]}]";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            add(out, "mcmod.info", metadata.getBytes(StandardCharsets.UTF_8));
            add(out, "foreign/harvestcandidate/Wood.class", woodBlock());
            add(out, "foreign/harvestcandidate/Bootstrap.class", bootstrap());
        }

        ConversionResult result = new LegacyConversionEngine().convert(
                source,
                tempDir.resolve("converted"),
                tempDir.resolve("manifests")
        );
        assertTrue(result.candidateJar().isPresent(), result.diagnostics().toString());
        assertTrue(result.appliedPasses().contains("legacy-block-material-provenance"));
        assertTrue(result.appliedPasses().contains("legacy-block-drop-analysis"));
        assertTrue(result.appliedPasses().contains("legacy-block-harvest-material-proof"));
        assertTrue(result.appliedPasses().contains("legacy-block-drop-runtime-readiness"));

        try (JarFile jar = new JarFile(result.candidateJar().orElseThrow().toFile())) {
            assertNotNull(jar.getJarEntry(LegacyBlockMaterialProvenancePass.OUTPUT_PATH));
            JarEntry dropEntry = jar.getJarEntry(LegacyBlockDropAnalysisPass.PLAN_PATH);
            assertNotNull(dropEntry);
            JsonObject root = read(jar, dropEntry);

            assertEquals(6, root.get("schemaVersion").getAsInt());
            assertEquals(LegacyBlockHarvestMaterialProofPass.RULE_VERSION,
                    root.get("materialHarvestRuleVersion").getAsString());
            assertEquals(1, root.get("harvestEligibilityProofCompletePlans").getAsInt());
            assertFalse(root.get("gameplayDropRuntimeWired").getAsBoolean());

            JsonObject plan = root.getAsJsonArray("plans").get(0).getAsJsonObject();
            assertEquals("wood", plan.get("legacyRegistryName").getAsString());
            assertTrue(plan.get("harvestEligibilityProofComplete").getAsBoolean());
            assertEquals("wood", plan.getAsJsonObject("legacyMaterial").get("namedMaterial").getAsString());
            assertTrue(plan.getAsJsonObject("legacyMaterial").get("toolNotRequired").getAsBoolean());
            assertTrue(contains(plan, LegacyBlockHarvestMaterialProofPass.GAMEPLAY_RUNTIME_BLOCKER));
            assertFalse(contains(plan, "harvest-eligibility-proof-pending"));
            assertFalse(plan.get("runtimeComplete").getAsBoolean());

            JarEntry readinessEntry = jar.getJarEntry(LegacyBlockDropRuntimeReadinessPass.OUTPUT_PATH);
            assertNotNull(readinessEntry);
            JsonObject readiness = read(jar, readinessEntry);
            assertEquals(6, readiness.get("sourcePlanSchemaVersion").getAsInt());
            assertEquals(1, readiness.get("normalSilkStaticSelfDropReadyPlans").getAsInt());
            assertEquals(0, readiness.get("blockedPlans").getAsInt());
            assertFalse(readiness.get("lootRuntimeGenerated").getAsBoolean());
            assertFalse(readiness.get("explosionRuntimeMappingReady").getAsBoolean());
            JsonObject ready = readiness.getAsJsonArray("ready").get(0).getAsJsonObject();
            assertEquals("harvestfast:wood", ready.get("id").getAsString());
            assertTrue(ready.get("normalSilkStaticSelfDropReady").getAsBoolean());
            assertTrue(ready.get("metadataIndependent").getAsBoolean());
        }
    }

    private static JsonObject read(JarFile jar, JarEntry entry) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean contains(JsonObject plan, String blocker) {
        for (var value : plan.getAsJsonArray("runtimeBlockers")) {
            if (blocker.equals(value.getAsString())) return true;
        }
        return false;
    }

    private static byte[] woodBlock() {
        String owner = "foreign/harvestcandidate/Wood";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, "field_151575_d", MATERIAL_DESC);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner = "foreign/harvestcandidate/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/harvestcandidate/Wood");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/harvestcandidate/Wood", "<init>", "()V", false);
        method.visitLdcInsn("wood");
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void add(JarOutputStream out, String name, byte[] value) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(value);
        out.closeEntry();
    }
}
