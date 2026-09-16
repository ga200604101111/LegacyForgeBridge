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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropExplosionGateTest {
    @TempDir Path tempDir;

    @Test
    void explosionDropEligibilityAndDestructionCallbacksRemainIndependentOfNormalDropPlan() throws Exception {
        Path jar = tempDir.resolve("ExplosionDropGate.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/explosionpass/NoDrop.class", block("foreign/explosionpass/NoDrop", Kind.NO_DROP));
            put(out, "foreign/explosionpass/CustomExploded.class",
                    block("foreign/explosionpass/CustomExploded", Kind.ON_EXPLODED));
            put(out, "foreign/explosionpass/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        for (String name : List.of("no_drop", "custom_exploded")) {
            context.recordRegistryIdentity("blocks", "fixture:" + name, "fixture:" + name);
            context.recordRegistryIdentity("items", "fixture:" + name, "fixture:" + name);
        }
        new LegacyBlockDropAnalysisPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(4, root.get("schemaVersion").getAsInt());
        assertEquals(2, root.get("normalDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("explosionDropProofCompletePlans").getAsInt());
        assertEquals(1, root.get("sourceExplosionDestructionOverrideFreePlans").getAsInt());

        Map<String, JsonObject> plans = new HashMap<>();
        for (var element : root.getAsJsonArray("plans")) {
            JsonObject value = element.getAsJsonObject();
            plans.put(value.get("legacyRegistryName").getAsString(), value);
        }

        JsonObject noDrop = plans.get("no_drop");
        assertTrue(noDrop.get("normalDropProofComplete").getAsBoolean());
        assertFalse(noDrop.get("sourceExplosionDropEligibilityProofComplete").getAsBoolean());
        assertTrue(noDrop.get("sourceExplosionDestructionOverrideFree").getAsBoolean());
        assertFalse(noDrop.get("explosionDropProofComplete").getAsBoolean());
        assertTrue(contains(noDrop, "explosion-can-drop-callback-runtime-pending"));
        assertFalse(contains(noDrop, "explosion-destruction-callback-runtime-pending"));

        JsonObject exploded = plans.get("custom_exploded");
        assertTrue(exploded.get("normalDropProofComplete").getAsBoolean());
        assertTrue(exploded.get("sourceExplosionDropEligibilityProofComplete").getAsBoolean());
        assertFalse(exploded.get("sourceExplosionDestructionOverrideFree").getAsBoolean());
        assertTrue(exploded.get("explosionDropProofComplete").getAsBoolean());
        assertFalse(contains(exploded, "explosion-can-drop-callback-runtime-pending"));
        assertTrue(contains(exploded, "explosion-destruction-callback-runtime-pending"));
    }

    private static boolean contains(JsonObject plan, String expected) {
        for (var blocker : plan.getAsJsonArray("runtimeBlockers")) {
            if (expected.equals(blocker.getAsString())) return true;
        }
        return false;
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

    private static byte[] block(String owner, Kind kind) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN); end(init);
        if (kind == Kind.NO_DROP) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149659_a",
                    "(Lnet/minecraft/world/Explosion;)Z", null, null);
            method.visitCode(); method.visitInsn(Opcodes.ICONST_0); method.visitInsn(Opcodes.IRETURN); end(method);
        } else {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "onBlockExploded",
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/world/Explosion;)V", null, null);
            method.visitCode(); method.visitInsn(Opcodes.RETURN); end(method);
        }
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/explosionpass/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd(); method.visitCode();
        register(method, "foreign/explosionpass/NoDrop", "no_drop");
        register(method, "foreign/explosionpass/CustomExploded", "custom_exploded");
        method.visitInsn(Opcodes.RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String name) {
        method.visitTypeInsn(Opcodes.NEW, owner); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }
    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
    private enum Kind { NO_DROP, ON_EXPLODED }
}
