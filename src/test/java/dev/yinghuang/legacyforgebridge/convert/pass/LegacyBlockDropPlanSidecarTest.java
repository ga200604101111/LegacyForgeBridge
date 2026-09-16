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
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropPlanSidecarTest {
    @TempDir Path tempDir;

    @Test
    void writesEventFreeNormalAndExplosionDropProofsButKeepsGameplayRuntimeGated() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropplanpass/Plain.class", plainBlock());
            put(out, "foreign/dropplanpass/Silk.class", silkBlock());
            put(out, "foreign/dropplanpass/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:plain", "fixture:plain");
        context.recordRegistryIdentity("items", "fixture:plain", "fixture:plain");
        context.recordRegistryIdentity("blocks", "fixture:silk", "fixture:silk");
        context.recordRegistryIdentity("items", "fixture:silk", "fixture:silk");

        new LegacyBlockDropAnalysisPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8
        )).getAsJsonObject();

        assertEquals(4, root.get("schemaVersion").getAsInt());
        assertEquals("sha", root.get("sourceSha256").getAsString());
        assertTrue(root.get("sourceHarvestDropsEventFree").getAsBoolean());
        assertEquals(0, root.get("harvestDropsEventHandlerCount").getAsInt());
        assertTrue(root.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertEquals(0, root.get("harvestCheckEventHandlerCount").getAsInt());
        assertEquals("inverse_explosion_size_1_7_10", root.get("legacyExplosionChanceMode").getAsString());
        assertEquals(0, root.get("legacyExplosionFortune").getAsInt());
        assertEquals(0, root.getAsJsonArray("harvestDropsEventHandlers").size());
        assertEquals(0, root.getAsJsonArray("harvestCheckEventHandlers").size());
        assertEquals(0, root.getAsJsonArray("eventAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("harvestEligibilityAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("explosionAnalysisDiagnostics").size());
        assertEquals(1, root.get("preHarvestEventDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("normalDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("sourceHarvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(0, root.get("harvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(1, root.get("explosionDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("sourceExplosionDestructionOverrideFreePlans").getAsInt());
        assertEquals(0, root.get("runtimeCompletePlans").getAsInt());
        assertEquals(1, root.get("incompletePlans").getAsInt());

        JsonArray plans = root.getAsJsonArray("plans");
        assertEquals(1, plans.size());
        JsonObject plan = plans.get(0).getAsJsonObject();
        assertEquals("plain", plan.get("legacyRegistryName").getAsString());
        assertEquals("fixture:plain", plan.get("id").getAsString());
        assertEquals("foreign/dropplanpass/Plain", plan.get("sourceClass").getAsString());
        assertTrue(plan.get("preHarvestEventDropProofComplete").getAsBoolean());
        assertTrue(plan.get("forgeHarvestEventProofComplete").getAsBoolean());
        assertTrue(plan.get("normalDropProofComplete").getAsBoolean());
        assertTrue(plan.get("sourceDropPathOverrideFree").getAsBoolean());
        assertTrue(plan.get("sourceHarvestEligibilityCustomizationFree").getAsBoolean());
        assertTrue(plan.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertTrue(plan.get("sourceHarvestEligibilityProofComplete").getAsBoolean());
        assertFalse(plan.get("harvestEligibilityProofComplete").getAsBoolean());
        assertEquals(0, plan.getAsJsonArray("harvestEligibilityReasons").size());
        assertTrue(plan.get("sourceExplosionDropEligibilityProofComplete").getAsBoolean());
        assertTrue(plan.get("sourceExplosionDestructionOverrideFree").getAsBoolean());
        assertTrue(plan.get("explosionDropProofComplete").getAsBoolean());
        assertEquals("inverse_explosion_size_1_7_10", plan.get("legacyExplosionChanceMode").getAsString());
        assertEquals(0, plan.get("legacyExplosionFortune").getAsInt());
        assertEquals(0, plan.getAsJsonArray("explosionDropEligibilityReasons").size());
        assertEquals(0, plan.getAsJsonArray("explosionDestructionReasons").size());
        assertFalse(plan.get("runtimeComplete").getAsBoolean());
        assertTrue(plan.get("modernIdentityComplete").getAsBoolean());
        assertEquals(1, plan.get("quantity").getAsInt());

        JsonObject item = plan.getAsJsonObject("item");
        assertEquals("SELF_BLOCK_ITEM", item.get("kind").getAsString());
        assertEquals("plain", item.get("legacyRegistryName").getAsString());
        assertEquals("fixture:plain", item.get("modernId").getAsString());

        JsonArray damage = plan.getAsJsonArray("itemDamageByBlockMeta");
        assertEquals(16, damage.size());
        for (int index = 0; index < damage.size(); index++) assertEquals(0, damage.get(index).getAsInt());

        JsonArray defaults = plan.getAsJsonArray("platformDefaults");
        assertEquals(3, defaults.size());
        assertEquals("item", defaults.get(0).getAsString());
        assertEquals("quantity", defaults.get(1).getAsString());
        assertEquals("damage", defaults.get(2).getAsString());

        JsonArray blockers = plan.getAsJsonArray("runtimeBlockers");
        assertEquals(2, blockers.size());
        assertEquals("harvest-eligibility-proof-pending", blockers.get(0).getAsString());
        assertEquals("silk-touch-stacked-item-proof-pending", blockers.get(1).getAsString());

        JsonArray incomplete = root.getAsJsonArray("incomplete");
        assertEquals(1, incomplete.size());
        JsonObject silk = incomplete.get(0).getAsJsonObject();
        assertEquals("silk", silk.get("legacyRegistryName").getAsString());
        assertFalse(silk.get("preHarvestEventDropProofComplete").getAsBoolean());
        assertFalse(silk.get("forgeHarvestEventProofComplete").getAsBoolean());
        assertFalse(silk.get("normalDropProofComplete").getAsBoolean());
        assertFalse(silk.get("sourceHarvestEligibilityProofComplete").getAsBoolean());
        assertFalse(silk.get("harvestEligibilityProofComplete").getAsBoolean());
        assertFalse(silk.get("explosionDropProofComplete").getAsBoolean());
        assertFalse(silk.get("runtimeComplete").getAsBoolean());
        boolean silkReasonFound = false;
        for (var reason : silk.getAsJsonArray("reasons")) {
            if (reason.getAsString().contains("canSilkHarvest")) { silkReasonFound = true; break; }
        }
        assertTrue(silkReasonFound);
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

    private static byte[] plainBlock() { return block(false); }
    private static byte[] silkBlock() { return block(true); }

    private static byte[] block(boolean silkOverride) {
        String owner = silkOverride ? "foreign/dropplanpass/Silk" : "foreign/dropplanpass/Plain";
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
        if (silkOverride) {
            MethodVisitor silk = writer.visitMethod(Opcodes.ACC_PROTECTED, "func_149700_E", "()Z", null, null);
            silk.visitCode(); silk.visitInsn(Opcodes.ICONST_1); silk.visitInsn(Opcodes.IRETURN); end(silk);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropplanpass/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd(); method.visitCode();
        register(method, "foreign/dropplanpass/Plain", "plain");
        register(method, "foreign/dropplanpass/Silk", "silk");
        method.visitInsn(Opcodes.RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, owner); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }
    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
