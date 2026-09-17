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
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityBehaviorSurfacePassTest {
    @TempDir Path tempDir;

    @Test void materializesClassifiedCallbacksAndUnclassifiedMethodsWithoutClaimingRuntimeReadiness() throws Exception {
        Path source = tempDir.resolve("entity-surface.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "unrelated/Orb.class", entity());
            put(out, "unrelated/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata("entity-surface.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("entity-surface.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyEntityDataWatcherPass().apply(context);
        new LegacyEntityBehaviorSurfacePass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertFalse(root.get("behaviorRuntimeWired").getAsBoolean());
        assertEquals(1, root.get("entitySurfaceCount").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:orb", rule.get("id").getAsString());
        assertEquals("net/minecraft/entity/Entity", rule.get("externalBaseClass").getAsString());
        assertTrue(rule.get("sourceOwnedBehaviorInventoryComplete").getAsBoolean());
        assertFalse(rule.get("runtimeBehaviorReady").getAsBoolean());
        assertEquals(2, rule.get("callbackCount").getAsInt());
        assertEquals(3, rule.get("sourceMethodCount").getAsInt());
        assertEquals(1, rule.get("unclassifiedSourceMethodCount").getAsInt());
        assertTrue(rule.getAsJsonArray("callbacks").asList().stream()
                .anyMatch(value -> value.getAsJsonObject().get("kind").getAsString().equals("TICK")));
        assertTrue(rule.getAsJsonArray("sourceMethods").asList().stream()
                .anyMatch(value -> value.getAsJsonObject().get("method").getAsString().equals("helper")
                        && !value.getAsJsonObject().has("callbackKind")));
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Orb", null, "net/minecraft/entity/Entity", null);

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
        init.visitIntInsn(Opcodes.BIPUSH, 15);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitInsn(Opcodes.I2B);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a",
                "(ILjava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor tick = w.visitMethod(Opcodes.ACC_PUBLIC, "func_70071_h_", "()V", null, null);
        tick.visitCode();
        tick.visitInsn(Opcodes.RETURN);
        tick.visitMaxs(0, 0);
        tick.visitEnd();

        MethodVisitor helper = w.visitMethod(Opcodes.ACC_PRIVATE, "helper", "()V", null, null);
        helper.visitCode();
        helper.visitInsn(Opcodes.RETURN);
        helper.visitMaxs(0, 0);
        helper.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("unrelated/Orb"));
        m.visitLdcInsn("orb");
        m.visitIntInsn(Opcodes.BIPUSH, 5);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 64);
        m.visitInsn(Opcodes.ICONST_2);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/EntityRegistry", "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
