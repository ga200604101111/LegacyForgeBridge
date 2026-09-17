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

class LegacyEntityDataWatcherAccessPassTest {
    @TempDir Path tempDir;

    @Test void materializesTypedAccessInventoryAndMarksOnlyStaticHelperClosureComplete() throws Exception {
        Path source = tempDir.resolve("entity-access.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "unrelated/Carrier.class", entity());
            put(out, "unrelated/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging"); Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata("entity-access.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis("entity-access.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyEntityDataWatcherPass().apply(context);
        new LegacyEntityDataWatcherAccessPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(staging.resolve(LegacyEntityDataWatcherAccessPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertFalse(root.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, root.get("proofCompleteLineageAccessSurfaces").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:carrier", rule.get("id").getAsString());
        assertTrue(rule.get("sourceLineageAccessSurfaceComplete").getAsBoolean());
        assertTrue(rule.get("reachableStaticHelperClosureComplete").getAsBoolean());
        assertFalse(rule.get("reachableHelperClosureComplete").getAsBoolean());
        assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(2, rule.get("accessCount").getAsInt());
        assertTrue(rule.getAsJsonArray("accesses").asList().stream().anyMatch(e -> e.getAsJsonObject().get("operation").getAsString().equals("read")));
        assertTrue(rule.getAsJsonArray("accesses").asList().stream().anyMatch(e -> e.getAsJsonObject().get("operation").getAsString().equals("write")));
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Carrier", null, "net/minecraft/entity/Entity", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        watcher(init); init.visitIntInsn(Opcodes.BIPUSH, 15); init.visitInsn(Opcodes.ICONST_0); init.visitInsn(Opcodes.I2B);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a", "(ILjava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PUBLIC, "set", "(B)V", null, null);
        set.visitCode(); watcher(set); set.visitIntInsn(Opcodes.BIPUSH, 15); set.visitVarInsn(Opcodes.ILOAD, 1); set.visitInsn(Opcodes.I2B);
        set.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        set.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75692_b", "(ILjava/lang/Object;)V", false);
        set.visitInsn(Opcodes.RETURN); set.visitMaxs(0, 0); set.visitEnd();

        MethodVisitor get = w.visitMethod(Opcodes.ACC_PUBLIC, "get", "()B", null, null);
        get.visitCode(); watcher(get); get.visitIntInsn(Opcodes.BIPUSH, 15);
        get.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        get.visitInsn(Opcodes.IRETURN); get.visitMaxs(0, 0); get.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }
    private static void watcher(MethodVisitor m) {
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
    }
    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor a = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true); a.visitEnd();
        m.visitCode(); m.visitLdcInsn(Type.getObjectType("unrelated/Carrier")); m.visitLdcInsn("carrier"); m.visitIntInsn(Opcodes.BIPUSH, 5);
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitIntInsn(Opcodes.BIPUSH, 64); m.visitInsn(Opcodes.ICONST_2); m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/EntityRegistry", "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V", false);
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
