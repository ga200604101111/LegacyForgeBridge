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

class LegacyBlockDropHarvestEventGateTest {
    @TempDir Path tempDir;

    @Test
    void harvestDropsEventGatesFinalNormalSilkAndExplosionResultsWithoutErasingSourceProofs() throws Exception {
        Path jar = tempDir.resolve("HarvestEventGate.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropevent/Plain.class", plainBlock());
            put(out, "foreign/dropevent/HarvestListener.class", harvestListener());
            put(out, "foreign/dropevent/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:plain", "fixture:plain");
        context.recordRegistryIdentity("items", "fixture:plain", "fixture:plain");
        new LegacyBlockDropAnalysisPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(5, root.get("schemaVersion").getAsInt());
        assertFalse(root.get("sourceHarvestDropsEventFree").getAsBoolean());
        assertEquals(1, root.get("harvestDropsEventHandlerCount").getAsInt());
        assertTrue(root.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertEquals(0, root.get("harvestCheckEventHandlerCount").getAsInt());
        assertEquals(1, root.get("preHarvestEventDropProofCompletePlans").getAsInt());
        assertEquals(0, root.get("normalDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("sourceHarvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(0, root.get("harvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(0, root.get("silkTouchProofCompletePlans").getAsInt());
        assertEquals(0, root.get("explosionDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("sourceExplosionDestructionOverrideFreePlans").getAsInt());
        assertEquals(0, root.getAsJsonArray("eventAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("harvestEligibilityAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("explosionAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("silkTouchAnalysisDiagnostics").size());

        JsonArray handlers = root.getAsJsonArray("harvestDropsEventHandlers");
        assertEquals(1, handlers.size());
        JsonObject handler = handlers.get(0).getAsJsonObject();
        assertEquals("foreign/dropevent/HarvestListener", handler.get("handlerClass").getAsString());
        assertEquals("onHarvest", handler.get("method").getAsString());
        assertEquals("FORGE", handler.get("bus").getAsString());
        assertEquals("COMMON", handler.get("side").getAsString());
        assertEquals("NORMAL", handler.get("priority").getAsString());
        assertFalse(handler.get("receiveCanceled").getAsBoolean());

        JsonObject plan = root.getAsJsonArray("plans").get(0).getAsJsonObject();
        assertTrue(plan.get("preHarvestEventDropProofComplete").getAsBoolean());
        assertFalse(plan.get("forgeHarvestEventProofComplete").getAsBoolean());
        assertFalse(plan.get("normalDropProofComplete").getAsBoolean());
        assertTrue(plan.get("sourceHarvestEligibilityCustomizationFree").getAsBoolean());
        assertTrue(plan.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertTrue(plan.get("sourceHarvestEligibilityProofComplete").getAsBoolean());
        assertFalse(plan.get("harvestEligibilityProofComplete").getAsBoolean());
        assertTrue(plan.get("silkTouchEligibilityProofComplete").getAsBoolean());
        assertTrue(plan.get("silkTouchEligible").getAsBoolean());
        assertTrue(plan.get("silkTouchStackProofComplete").getAsBoolean());
        assertFalse(plan.get("silkTouchProofComplete").getAsBoolean());
        assertTrue(plan.get("sourceExplosionDropEligibilityProofComplete").getAsBoolean());
        assertTrue(plan.get("sourceExplosionDestructionOverrideFree").getAsBoolean());
        assertFalse(plan.get("explosionDropProofComplete").getAsBoolean());
        assertFalse(plan.get("runtimeComplete").getAsBoolean());
        assertEquals(2, plan.getAsJsonArray("runtimeBlockers").size());
        assertTrue(contains(plan, "harvest-eligibility-proof-pending"));
        assertTrue(contains(plan, "harvest-drops-event-runtime-pending"));
    }

    private static boolean contains(JsonObject plan, String expected) {
        for (var blocker : plan.getAsJsonArray("runtimeBlockers")) {
            if (expected.equals(blocker.getAsString())) return true;
        }
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

    private static byte[] plainBlock() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropevent/Plain", null,
                "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] harvestListener() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropevent/HarvestListener", null,
                "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "onHarvest",
                "(Lnet/minecraftforge/event/world/BlockEvent$HarvestDropsEvent;)V", null, null);
        AnnotationVisitor subscribe = method.visitAnnotation(
                "Lcpw/mods/fml/common/eventhandler/SubscribeEvent;", true);
        subscribe.visitEnd();
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropevent/Bootstrap", null,
                "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/dropevent/Plain");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/dropevent/Plain", "<init>", "()V", false);
        method.visitLdcInsn("plain");
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitFieldInsn(Opcodes.GETSTATIC,
                "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;");
        method.visitTypeInsn(Opcodes.NEW, "foreign/dropevent/HarvestListener");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "foreign/dropevent/HarvestListener", "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "cpw/mods/fml/common/eventhandler/EventBus", "register",
                "(Ljava/lang/Object;)V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, String parent) {
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
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
