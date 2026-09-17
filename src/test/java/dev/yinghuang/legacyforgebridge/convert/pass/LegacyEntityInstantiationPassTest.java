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

class LegacyEntityInstantiationPassTest {
    @TempDir Path tempDir;

    @Test void materializesSpawnMigrationEvidenceAndJoinsModernRuntimeReplacement() throws Exception {
        Path source = tempDir.resolve("instantiation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "unrelated/Orb.class", orb());
            put(out, "unrelated/Spawner.class", spawner());
            put(out, "unrelated/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyPlainEntityRuntimePass.OUTPUT), """
                {
                  "schemaVersion":1,
                  "sourceSha256":"sha",
                  "runtimeImplementationWired":true,
                  "rules":[
                    {"id":"foreign:orb","sourceClass":"unrelated/Orb","runtimeComplete":true}
                  ]
                }
                """, StandardCharsets.UTF_8);

        LegacyModMetadata metadata = new LegacyModMetadata("instantiation.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("instantiation.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyEntityInstantiationPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityInstantiationPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("sourceInstantiationInventoryComplete").getAsBoolean());
        assertFalse(root.get("spawnRewriteWired").getAsBoolean());
        assertEquals(1, root.get("registeredEntityCount").getAsInt());
        assertEquals(1, root.get("directConstructionCount").getAsInt());
        assertEquals(2, root.get("worldSpawnCallCount").getAsInt());
        assertEquals(1, root.get("unresolvedWorldSpawnArgumentCount").getAsInt());
        assertEquals(1, root.get("runtimeReplacementEntityCount").getAsInt());
        assertFalse(root.get("spawnMigrationComplete").getAsBoolean());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("unrelated/Orb", rule.get("sourceClass").getAsString());
        assertEquals(1, rule.get("directConstructionCount").getAsInt());
        assertEquals(1, rule.get("provenWorldSpawnCount").getAsInt());
        assertTrue(rule.get("modernRuntimeReplacementAvailable").getAsBoolean());
        assertEquals("foreign:orb", rule.get("modernRuntimeId").getAsString());
        assertTrue(rule.get("sourceInstantiationRewriteRequired").getAsBoolean());
        assertFalse(rule.get("sourceInstantiationRewriteWired").getAsBoolean());
    }

    private static byte[] orb() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Orb", null, "net/minecraft/entity/Entity", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/world/World;)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "<init>",
                "(Lnet/minecraft/world/World;)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] spawner() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Spawner", null, "java/lang/Object", null);
        MethodVisitor spawn = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawnOrb",
                "(Lnet/minecraft/world/World;)V", null, null);
        spawn.visitCode();
        spawn.visitTypeInsn(Opcodes.NEW, "unrelated/Orb");
        spawn.visitInsn(Opcodes.DUP);
        spawn.visitVarInsn(Opcodes.ALOAD, 0);
        spawn.visitMethodInsn(Opcodes.INVOKESPECIAL, "unrelated/Orb", "<init>",
                "(Lnet/minecraft/world/World;)V", false);
        spawn.visitVarInsn(Opcodes.ASTORE, 1);
        spawn.visitVarInsn(Opcodes.ALOAD, 0);
        spawn.visitVarInsn(Opcodes.ALOAD, 1);
        spawn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false);
        spawn.visitInsn(Opcodes.POP);
        spawn.visitInsn(Opcodes.RETURN);
        spawn.visitMaxs(0, 0);
        spawn.visitEnd();

        MethodVisitor unknown = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawnUnknown",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)V", null, null);
        unknown.visitCode();
        unknown.visitVarInsn(Opcodes.ALOAD, 0);
        unknown.visitVarInsn(Opcodes.ALOAD, 1);
        unknown.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false);
        unknown.visitInsn(Opcodes.POP);
        unknown.visitInsn(Opcodes.RETURN);
        unknown.visitMaxs(0, 0);
        unknown.visitEnd();
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
        m.visitIntInsn(Opcodes.BIPUSH, 17);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 80);
        m.visitInsn(Opcodes.ICONST_2);
        m.visitInsn(Opcodes.ICONST_1);
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
