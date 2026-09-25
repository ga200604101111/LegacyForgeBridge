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

class LegacyPlantPlacementReedProofPassTest {
    @TempDir Path tempDir;

    @Test void inheritedItemReedWithInheritedTargetCallbacksBecomesRuntimeComplete() throws Exception {
        Path source = tempDir.resolve("reed-placement.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "pkg/ReedBlock.class", block());
            put(out, "pkg/PureReed.class", reed());
            put(out, "pkg/Bootstrap.class", bootstrap());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        write(staging, LegacyItemBlockBindingPass.OUTPUT, """
                {"schemaVersion":2,"sourceSha256":"sha","bindings":[
                  {"id":"demo:reed_item","legacyRegistryName":"reed_item","sourceClass":"pkg/PureReed","family":"reed","topologyProofComplete":true,"modernIdentityComplete":true,"targetBlock":{"modernId":"demo:reed"}}
                ]}
                """);
        write(staging, LegacyPlantBlockPass.OUTPUT, """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[
                  {"legacyRegistryName":"reed","sourceClass":"pkg/ReedBlock","family":"reed","modernId":"demo:reed","modernIdentityComplete":true,"inheritedVanillaLifecycleOnly":true,"sourceOverrides":[]}
                ]}
                """);
        write(staging, LegacyPlantSoilProofPass.OUTPUT, """
                {"schemaVersion":2,"sourceSha256":"sha","proofs":[
                  {"family":"reed","modernId":"demo:reed","plantRuntimeProofComplete":true,"placementTargetProofComplete":true,"survivalSoilProofComplete":true}
                ]}
                """);

        LegacyModMetadata metadata = new LegacyModMetadata("reed-placement.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("demo", "Demo", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "reed-placement.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        ConversionContext context = new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
        new LegacyPlantPlacementProofPass().apply(context);

        JsonObject root = JsonParser.parseString(
                Files.readString(staging.resolve(LegacyPlantPlacementProofPass.OUTPUT))).getAsJsonObject();
        assertEquals(1, root.get("classifiedItems").getAsInt());
        assertEquals(1, root.get("placementProofCompleteItems").getAsInt());
        assertEquals(1, root.get("runtimeCompleteItems").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("inheritedVanillaPlacement").getAsBoolean());
        assertTrue(rule.get("targetPlacementCallbacksInherited").getAsBoolean());
        assertTrue(rule.get("runtimeComplete").getAsBoolean());
        assertEquals("item_reed_1_7_10", rule.get("placementAdapter").getAsString());
    }

    private static byte[] reed() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "pkg/PureReed", null, "net/minecraft/item/ItemReed", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitFieldInsn(Opcodes.GETSTATIC, "pkg/Bootstrap", "REED", "Lnet/minecraft/block/Block;");
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemReed", "<init>",
                "(Lnet/minecraft/block/Block;)V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] block() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "pkg/ReedBlock", null, "net/minecraft/block/Block", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitInsn(Opcodes.ACONST_NULL);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "pkg/Bootstrap", null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "REED", "Lnet/minecraft/block/Block;", null, null).visitEnd();
        MethodVisitor bind = w.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bindBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", null, null);
        bind.visitCode();
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitInsn(Opcodes.ARETURN);
        bind.visitMaxs(0, 0);
        bind.visitEnd();

        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, "pkg/ReedBlock");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/ReedBlock", "<init>", "()V", false);
        m.visitLdcInsn("reed");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Bootstrap", "bindBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        m.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Bootstrap", "REED", "Lnet/minecraft/block/Block;");
        m.visitTypeInsn(Opcodes.NEW, "pkg/PureReed");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/PureReed", "<init>", "()V", false);
        m.visitLdcInsn("reed_item");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void write(Path staging, String relative, String json) throws Exception {
        Path path = staging.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
