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
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyBlockBehaviorAnalysisPassTest {
    @TempDir Path tempDir;

    @Test void sidecarLinksProvenModernBlockIdentityToSourceCallbackProvenance() throws Exception {
        Path source = tempDir.resolve("foreign-block.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "other/world/TurnLamp.class", block());
            put(out, "other/world/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata("foreign-block.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "foreign-block.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
        context.recordRegistryIdentity("blocks", "foreign:turn_lamp", "foreign:turn_lamp");

        new LegacyBlockBehaviorAnalysisPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyBlockBehaviorAnalysisPass.ANALYSIS_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject value = root.getAsJsonArray("blocks").get(0).getAsJsonObject();
        assertEquals("foreign:turn_lamp", value.get("id").getAsString());
        assertEquals("turn_lamp", value.get("legacyRegistryName").getAsString());
        assertEquals("other/world/TurnLamp", value.get("sourceClass").getAsString());
        JsonObject callback = value.getAsJsonArray("callbacks").get(0).getAsJsonObject();
        assertEquals("ACTIVATE", callback.get("kind").getAsString());
        assertEquals("other/world/TurnLamp", callback.get("sourceOwner").getAsString());
        assertEquals("func_149727_a", callback.get("sourceMethod").getAsString());
    }

    private static byte[] block() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner = "other/world/TurnLamp";
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(1, 10);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner = "other/world/Bootstrap";
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor av = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        av.visitEnd();
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, "other/world/TurnLamp");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "other/world/TurnLamp", "<init>", "()V", false);
        m.visitLdcInsn("turn_lamp");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
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
