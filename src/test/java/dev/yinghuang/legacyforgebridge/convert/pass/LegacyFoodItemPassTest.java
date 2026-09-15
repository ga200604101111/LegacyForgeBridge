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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFoodItemPassTest {
    @TempDir Path tempDir;

    @Test void sidecarLinksProvenFoodToGeneratedIdentity() throws Exception {
        Path source = tempDir.resolve("foreign-food.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "other/food/Berry.class", food());
            put(out, "other/food/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Path lfb = staging.resolve("legacyforgebridge");
        Files.createDirectories(lfb);
        Files.writeString(lfb.resolve("converted-content.json"),
                "{\"items\":[{\"id\":\"foreign:berry\",\"legacyRegistryName\":\"berry\",\"sourceClass\":\"other/food/Berry\"}]}\n",
                StandardCharsets.UTF_8);

        LegacyModMetadata metadata = new LegacyModMetadata("foreign-food.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "foreign-food.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(source), metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");

        new LegacyFoodItemPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyFoodItemPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("runtimeCompleteRules").getAsInt());
        assertEquals(0, root.get("skippedRules").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:berry", rule.get("id").getAsString());
        assertEquals("berry", rule.get("legacyRegistryName").getAsString());
        assertEquals("other/food/Berry", rule.get("sourceClass").getAsString());
        assertEquals(4, rule.get("nutrition").getAsInt());
        assertEquals(0.3F, rule.get("saturationModifier").getAsFloat());
        assertTrue(rule.get("alwaysEdible").getAsBoolean());
        assertTrue(rule.get("runtimeComplete").getAsBoolean());
    }

    private static byte[] food() {
        String owner = "other/food/Berry";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/item/ItemFood", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ICONST_4);
        init.visitLdcInsn(0.3F);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemFood", "<init>", "(IFZ)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setAlwaysEdible", "()Lnet/minecraft/item/ItemFood;", false);
        init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner = "other/food/Bootstrap";
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, "other/food/Berry");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "other/food/Berry", "<init>", "()V", false);
        m.visitLdcInsn("berry");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
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
