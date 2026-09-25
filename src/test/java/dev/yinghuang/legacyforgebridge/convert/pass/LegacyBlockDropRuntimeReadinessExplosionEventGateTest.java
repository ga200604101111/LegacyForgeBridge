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

class LegacyBlockDropRuntimeReadinessExplosionEventGateTest {
    @TempDir Path tempDir;

    @Test
    void forgeExplosionDetonateHandlerKeepsAffectedSetProofFailClosed() throws Exception {
        Path jar = tempDir.resolve("ExplosionEventGate.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/explosionreadiness/Listener.class", listener());
            put(out, "foreign/explosionreadiness/Bootstrap.class", bootstrap());
        }
        ConversionContext context = context(jar);

        JsonObject source = new JsonObject();
        source.addProperty("schemaVersion", 6);
        source.addProperty("sourceSha256", "sha");
        source.addProperty("legacyExplosionChanceMode", "inverse_explosion_size_1_7_10");
        source.add("plans", new JsonArray());
        Path input = tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH);
        Files.createDirectories(input.getParent());
        Files.writeString(input, source.toString(), StandardCharsets.UTF_8);

        new LegacyBlockDropRuntimeReadinessPass().apply(context);

        JsonObject output = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropRuntimeReadinessPass.OUTPUT_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(output.get("explosionDecayFormulaProofComplete").getAsBoolean());
        assertFalse(output.get("sourceExplosionEventFree").getAsBoolean());
        assertEquals(1, output.get("explosionEventHandlerCount").getAsInt());
        assertFalse(output.get("explosionAffectedSetSourceProofComplete").getAsBoolean());
        assertFalse(output.get("explosionRuntimeMappingReady").getAsBoolean());
        assertEquals(0, output.getAsJsonArray("eventAnalysisDiagnostics").size());

        JsonArray handlers = output.getAsJsonArray("explosionEventHandlers");
        assertEquals(1, handlers.size());
        JsonObject handler = handlers.get(0).getAsJsonObject();
        assertEquals("foreign/explosionreadiness/Listener", handler.get("handlerClass").getAsString());
        assertEquals("onDetonate", handler.get("method").getAsString());
        assertEquals("net/minecraftforge/event/world/ExplosionEvent$Detonate",
                handler.get("eventType").getAsString());
        assertEquals("FORGE", handler.get("bus").getAsString());
        assertEquals("COMMON", handler.get("side").getAsString());
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

    private static byte[] listener() {
        String owner = "foreign/explosionreadiness/Listener";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "onDetonate",
                "(Lnet/minecraftforge/event/world/ExplosionEvent$Detonate;)V", null, null);
        AnnotationVisitor subscribe = method.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;", true);
        subscribe.visitEnd();
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner = "foreign/explosionreadiness/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;");
        method.visitTypeInsn(Opcodes.NEW, "foreign/explosionreadiness/Listener");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/explosionreadiness/Listener", "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus", "register",
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
