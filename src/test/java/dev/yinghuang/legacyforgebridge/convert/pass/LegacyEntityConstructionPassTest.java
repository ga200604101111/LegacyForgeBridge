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

class LegacyEntityConstructionPassTest {
    @TempDir Path tempDir;

    @Test void materializesConstantSizeProofButKeepsUnknownConstructorEffectsAndRuntimeClosed() throws Exception {
        Path source = tempDir.resolve("entity-construction.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "unrelated/Orb.class", entity());
            put(out, "unrelated/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata("entity-construction.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("entity-construction.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyEntityDataWatcherPass().apply(context);
        new LegacyEntityConstructionPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityConstructionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertFalse(root.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, root.get("entityConstructionCount").getAsInt());
        assertEquals(1, root.get("sizeProofCompleteCount").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:orb", rule.get("id").getAsString());
        assertTrue(rule.get("worldConstructorPresent").getAsBoolean());
        assertTrue(rule.get("constructorChainComplete").getAsBoolean());
        assertTrue(rule.get("constructorControlFlowSimple").getAsBoolean());
        assertTrue(rule.get("sizeProofComplete").getAsBoolean());
        assertEquals(0.5F, rule.get("width").getAsFloat());
        assertEquals(1.0F, rule.get("height").getAsFloat());
        assertFalse(rule.get("runtimeConstructionReady").getAsBoolean());
        assertEquals(2, rule.get("unmappedConstructorEffectCount").getAsInt());
        assertTrue(rule.getAsJsonArray("effects").asList().stream()
                .anyMatch(element -> element.getAsJsonObject().get("kind").getAsString().equals("this-field-write")));
        assertTrue(rule.getAsJsonArray("effects").asList().stream()
                .anyMatch(element -> element.getAsJsonObject().get("kind").getAsString().equals("method-call")));
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Orb", null, "net/minecraft/entity/Entity", null);
        w.visitField(Opcodes.ACC_PRIVATE, "marker", "I", null, null).visitEnd();

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/World;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "<init>", "(Lnet/minecraft/world/World;)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(0.5F);
        init.visitInsn(Opcodes.FCONST_1);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity", "func_70105_a", "(FF)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitIntInsn(Opcodes.BIPUSH, 3);
        init.visitFieldInsn(Opcodes.PUTFIELD, "unrelated/Orb", "marker", "I");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "unrelated/Orb", "touch", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor touch = w.visitMethod(Opcodes.ACC_PRIVATE, "touch", "()V", null, null);
        touch.visitCode();
        touch.visitInsn(Opcodes.RETURN);
        touch.visitMaxs(0, 0);
        touch.visitEnd();

        MethodVisitor entityInit = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        entityInit.visitCode();
        entityInit.visitVarInsn(Opcodes.ALOAD, 0);
        entityInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        entityInit.visitVarInsn(Opcodes.ALOAD, 0);
        entityInit.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
        entityInit.visitIntInsn(Opcodes.BIPUSH, 15);
        entityInit.visitInsn(Opcodes.ICONST_0);
        entityInit.visitInsn(Opcodes.I2B);
        entityInit.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        entityInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a",
                "(ILjava/lang/Object;)V", false);
        entityInit.visitInsn(Opcodes.RETURN);
        entityInit.visitMaxs(0, 0);
        entityInit.visitEnd();

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
