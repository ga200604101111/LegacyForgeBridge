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

class LegacyBlockDropPlanSidecarTest {
    @TempDir Path tempDir;

    @Test
    void keepsSilkOnlyCustomizationOutOfTheOrdinaryDropPlan() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropplanpass/Plain.class", block("foreign/dropplanpass/Plain", false));
            put(out, "foreign/dropplanpass/Silk.class", block("foreign/dropplanpass/Silk", true));
            put(out, "foreign/dropplanpass/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        for (String name : List.of("plain", "silk")) {
            context.recordRegistryIdentity("blocks", "fixture:" + name, "fixture:" + name);
            context.recordRegistryIdentity("items", "fixture:" + name, "fixture:" + name);
        }
        new LegacyBlockDropAnalysisPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(5, root.get("schemaVersion").getAsInt());
        assertEquals("sha", root.get("sourceSha256").getAsString());
        assertTrue(root.get("sourceHarvestDropsEventFree").getAsBoolean());
        assertTrue(root.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertEquals(0, root.get("harvestDropsEventHandlerCount").getAsInt());
        assertEquals(0, root.get("harvestCheckEventHandlerCount").getAsInt());
        assertEquals(0, root.getAsJsonArray("eventAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("harvestEligibilityAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("explosionAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("silkTouchAnalysisDiagnostics").size());
        assertEquals(2, root.get("preHarvestEventDropProofCompletePlans").getAsInt());
        assertEquals(2, root.get("normalDropProofCompletePlans").getAsInt());
        assertEquals(2, root.get("sourceHarvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(0, root.get("harvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(2, root.get("explosionDropProofCompletePlans").getAsInt());
        assertEquals(2, root.get("sourceExplosionDestructionOverrideFreePlans").getAsInt());
        assertEquals(1, root.get("silkTouchProofCompletePlans").getAsInt());
        assertEquals(0, root.get("runtimeCompletePlans").getAsInt());
        assertEquals(0, root.get("incompletePlans").getAsInt());

        Map<String, JsonObject> plans = new HashMap<>();
        for (var element : root.getAsJsonArray("plans")) {
            JsonObject value = element.getAsJsonObject();
            plans.put(value.get("legacyRegistryName").getAsString(), value);
        }

        JsonObject plain = plans.get("plain");
        assertTrue(plain.get("normalDropProofComplete").getAsBoolean());
        assertTrue(plain.get("sourceHarvestEligibilityProofComplete").getAsBoolean());
        assertFalse(plain.get("harvestEligibilityProofComplete").getAsBoolean());
        assertTrue(plain.get("explosionDropProofComplete").getAsBoolean());
        assertTrue(plain.get("silkTouchEligibilityProofComplete").getAsBoolean());
        assertTrue(plain.get("silkTouchEligible").getAsBoolean());
        assertTrue(plain.get("silkTouchStackProofComplete").getAsBoolean());
        assertTrue(plain.get("silkTouchProofComplete").getAsBoolean());
        assertEquals(0, plain.getAsJsonArray("silkTouchEligibilityReasons").size());
        assertEquals(0, plain.getAsJsonArray("silkTouchStackReasons").size());
        JsonObject stack = plain.getAsJsonObject("silkTouchStack");
        assertEquals("SELF_BLOCK_ITEM", stack.get("kind").getAsString());
        assertEquals(1, stack.get("quantity").getAsInt());
        assertEquals(0, stack.get("legacyDamage").getAsInt());
        assertEquals("fixture:plain", stack.get("modernId").getAsString());
        assertEquals(1, plain.getAsJsonArray("runtimeBlockers").size());
        assertEquals("harvest-eligibility-proof-pending",
                plain.getAsJsonArray("runtimeBlockers").get(0).getAsString());

        JsonObject silk = plans.get("silk");
        assertTrue(silk.get("normalDropProofComplete").getAsBoolean());
        assertTrue(silk.get("sourceHarvestEligibilityProofComplete").getAsBoolean());
        assertTrue(silk.get("explosionDropProofComplete").getAsBoolean());
        assertFalse(silk.get("silkTouchEligibilityProofComplete").getAsBoolean());
        assertTrue(silk.get("silkTouchStackProofComplete").getAsBoolean());
        assertFalse(silk.get("silkTouchProofComplete").getAsBoolean());
        assertTrue(contains(silk.getAsJsonArray("silkTouchEligibilityReasons"), "canSilkHarvest"));
        assertTrue(contains(silk.getAsJsonArray("runtimeBlockers"), "silk-touch-eligibility-runtime-pending"));
        assertEquals(2, silk.getAsJsonArray("runtimeBlockers").size());
    }

    private static boolean contains(JsonArray values, String fragment) {
        for (var value : values) if (value.getAsString().contains(fragment)) return true;
        return false;
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

    private static byte[] block(String owner, boolean silkOverride) {
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
            silk.visitCode();
            silk.visitInsn(Opcodes.ICONST_1);
            silk.visitInsn(Opcodes.IRETURN);
            end(silk);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropplanpass/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/dropplanpass/Plain", "plain");
        register(method, "foreign/dropplanpass/Silk", "silk");
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String name) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(name);
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
