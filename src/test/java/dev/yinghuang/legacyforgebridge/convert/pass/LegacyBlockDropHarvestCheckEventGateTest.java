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

class LegacyBlockDropHarvestCheckEventGateTest {
    @TempDir Path tempDir;

    @Test
    void provenHarvestCheckHandlerGatesSourceHarvestEligibilityWithoutChangingDropStackProof() throws Exception {
        Path jar = tempDir.resolve("HarvestCheckGate.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/harvestcheck/Plain.class", plainBlock());
            put(out, "foreign/harvestcheck/HarvestCheckListener.class", harvestCheckListener());
            put(out, "foreign/harvestcheck/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:plain", "fixture:plain");
        context.recordRegistryIdentity("items", "fixture:plain", "fixture:plain");
        new LegacyBlockDropAnalysisPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(4, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("sourceHarvestDropsEventFree").getAsBoolean());
        assertEquals(0, root.get("harvestDropsEventHandlerCount").getAsInt());
        assertFalse(root.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertEquals(1, root.get("harvestCheckEventHandlerCount").getAsInt());
        assertEquals(1, root.get("normalDropProofCompletePlans").getAsInt());
        assertEquals(0, root.get("sourceHarvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(0, root.get("harvestEligibilityProofCompletePlans").getAsInt());
        assertEquals(1, root.get("explosionDropProofCompletePlans").getAsInt());
        assertEquals(0, root.getAsJsonArray("eventAnalysisDiagnostics").size());
        assertEquals(0, root.getAsJsonArray("harvestEligibilityAnalysisDiagnostics").size());

        JsonArray handlers = root.getAsJsonArray("harvestCheckEventHandlers");
        assertEquals(1, handlers.size());
        JsonObject handler = handlers.get(0).getAsJsonObject();
        assertEquals("foreign/harvestcheck/HarvestCheckListener", handler.get("handlerClass").getAsString());
        assertEquals("onHarvestCheck", handler.get("method").getAsString());
        assertEquals("FORGE", handler.get("bus").getAsString());
        assertEquals("COMMON", handler.get("side").getAsString());
        assertEquals("NORMAL", handler.get("priority").getAsString());
        assertFalse(handler.get("receiveCanceled").getAsBoolean());

        JsonObject plan = root.getAsJsonArray("plans").get(0).getAsJsonObject();
        assertTrue(plan.get("normalDropProofComplete").getAsBoolean());
        assertTrue(plan.get("sourceHarvestEligibilityCustomizationFree").getAsBoolean());
        assertFalse(plan.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertFalse(plan.get("sourceHarvestEligibilityProofComplete").getAsBoolean());
        assertFalse(plan.get("harvestEligibilityProofComplete").getAsBoolean());
        assertTrue(plan.get("explosionDropProofComplete").getAsBoolean());
        assertFalse(plan.get("runtimeComplete").getAsBoolean());
        assertEquals(3, plan.getAsJsonArray("runtimeBlockers").size());

        boolean harvestCheckBlocker = false;
        for (var blocker : plan.getAsJsonArray("runtimeBlockers")) {
            if ("harvest-check-event-runtime-pending".equals(blocker.getAsString())) {
                harvestCheckBlocker = true;
                break;
            }
        }
        assertTrue(harvestCheckBlocker);
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging"); Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata("fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] plainBlock() {
        String owner = "foreign/harvestcheck/Plain";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN); end(init); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] harvestCheckListener() {
        String owner = "foreign/harvestcheck/HarvestCheckListener";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "onHarvestCheck",
                "(Lnet/minecraftforge/event/entity/player/PlayerEvent$HarvestCheck;)V", null, null);
        AnnotationVisitor subscribe = method.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;", true);
        subscribe.visitEnd(); method.visitCode(); method.visitInsn(Opcodes.RETURN); end(method);
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner = "foreign/harvestcheck/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd(); method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/harvestcheck/Plain"); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/harvestcheck/Plain", "<init>", "()V", false);
        method.visitLdcInsn("plain");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;");
        method.visitTypeInsn(Opcodes.NEW, "foreign/harvestcheck/HarvestCheckListener"); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/harvestcheck/HarvestCheckListener", "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus", "register",
                "(Ljava/lang/Object;)V", false);
        method.visitInsn(Opcodes.RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, String parent) {
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); end(init);
    }

    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
