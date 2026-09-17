package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

class LegacyEntityPresentationPassTest {
    @TempDir Path tempDir;

    @Test void materializesNoOpRendererProofAndMissingRendererBlocker() throws Exception {
        Path source = tempDir.resolve("presentation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "third/client/ClientRegistrar.class", registrar());
            put(out, "third/client/RenderEmpty.class", renderer());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyEntityDataWatcherPass.OUTPUT), """
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "rules":[
                    {"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"third/entity/Orb"},
                    {"id":"foreign:hidden","legacyRegistryName":"hidden","sourceClass":"third/entity/Hidden"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        LegacyModMetadata metadata = new LegacyModMetadata("presentation.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("presentation.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyEntityPresentationPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityPresentationPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, root.get("evaluatedRegistrations").getAsInt());
        assertEquals(1, root.get("sourceNoOpRendererProofs").getAsInt());
        JsonObject orb = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(orb.get("sourceNoOpRendererProven").getAsBoolean());
        assertEquals("modern-noop-renderer-runtime-not-materialized",
                orb.getAsJsonArray("presentationBlockers").get(0).getAsString());
        JsonObject hidden = root.getAsJsonArray("rules").get(1).getAsJsonObject();
        assertFalse(hidden.get("sourceNoOpRendererProven").getAsBoolean());
        assertEquals("source-entity-renderer-registration-missing",
                hidden.getAsJsonArray("presentationBlockers").get(0).getAsString());
    }

    private static byte[] registrar() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/client/ClientRegistrar", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "()V", null, null);
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/entity/Orb"));
        m.visitTypeInsn(Opcodes.NEW, "third/client/RenderEmpty");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/client/RenderEmpty", "<init>", "()V", false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/client/registry/RenderingRegistry",
                "registerEntityRenderingHandler", "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] renderer() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/client/RenderEmpty", null,
                "net/minecraft/client/renderer/entity/Render", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/client/renderer/entity/Render", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        MethodVisitor render = w.visitMethod(Opcodes.ACC_PUBLIC, "doRender",
                "(Lthird/entity/Orb;DDDFF)V", null, null);
        render.visitCode();
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
