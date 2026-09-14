package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockActivationPassTest {
    @TempDir Path tempDir;

    @Test void materializesPureSideDecisionAndSkipsWorldDependentActivation() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/use/SideGate.class", sideGate());
            put(out, "foreign/use/WorldGate.class", worldGate());
            put(out, "foreign/use/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("blocks", "fixture:side_gate", "fixture:side_gate");
        context.recordRegistryIdentity("blocks", "fixture:world_gate", "fixture:world_gate");
        new LegacyBlockActivationPass().apply(context);

        Path rulesPath = tempDir.resolve("staging/" + LegacyBlockActivationPass.RULES_PATH);
        assertTrue(Files.isRegularFile(rulesPath));
        JsonObject root = JsonParser.parseString(Files.readString(rulesPath, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, root.get("sourceCallbacks").getAsInt());
        assertEquals(1, root.get("skippedRules").getAsInt());
        assertEquals(1, root.getAsJsonArray("rules").size());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("fixture:side_gate", rule.get("id").getAsString());
        assertEquals("foreign/use/SideGate", rule.getAsJsonObject("provenance").get("owner").getAsString());
        assertEquals("LOAD_INT", rule.getAsJsonObject("program").getAsJsonArray("instructions")
                .get(0).getAsJsonObject().get("op").getAsString());
        assertEquals("IAND", rule.getAsJsonObject("program").getAsJsonArray("instructions")
                .get(2).getAsJsonObject().get("op").getAsString());

        assertTrue(context.diagnostics().snapshot().stream()
                .anyMatch(value -> "LFB-CONVERT-BLOCK-ACTIVATION-0001".equals(value.ruleId())));
        assertTrue(context.diagnostics().snapshot().stream()
                .anyMatch(value -> "LFB-CONVERT-BLOCK-ACTIVATION-0002".equals(value.ruleId())
                        && value.message().contains("Unsupported pure activation callback")));
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] sideGate() {
        ClassWriter w = blockClass("foreign/use/SideGate");
        MethodVisitor method = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IAND);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(2, 10);
        method.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] worldGate() {
        ClassWriter w = blockClass("foreign/use/WorldGate");
        MethodVisitor method = w.visitMethod(Opcodes.ACC_PUBLIC, "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/World", "isRemote", "Z");
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 10);
        method.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static ClassWriter blockClass(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        return w;
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/use/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/use/SideGate", "side_gate");
        register(method, "foreign/use/WorldGate", "world_gate");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void register(MethodVisitor method, String blockClass, String name) {
        method.visitTypeInsn(Opcodes.NEW, blockClass);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, blockClass, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
