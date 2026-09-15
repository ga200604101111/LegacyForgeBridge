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

class LegacyBlockDropAnalysisPassTest {
    @TempDir Path tempDir;

    @Test void composesIndependentItemQuantityAndMetadataEvidenceWithoutInventingRuntimeDrops() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropsidecar/Seed.class", item());
            put(out, "foreign/dropsidecar/Crop.class", crop());
            put(out, "foreign/dropsidecar/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        context.recordRegistryIdentity("items", "fixture:seed", "fixture:seed");
        context.recordRegistryIdentity("blocks", "fixture:crop", "fixture:crop");
        new LegacyBlockDropAnalysisPass().apply(context);

        Path sidecar = tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.ANALYSIS_PATH);
        assertTrue(Files.isRegularFile(sidecar));
        JsonObject root = JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.getAsJsonArray("blocks").size());
        JsonObject block = root.getAsJsonArray("blocks").get(0).getAsJsonObject();
        assertEquals("fixture:crop", block.get("id").getAsString());
        assertEquals("foreign/dropsidecar/Crop", block.get("sourceClass").getAsString());

        JsonObject item = block.getAsJsonObject("item");
        assertEquals("ITEM", item.get("kind").getAsString());
        assertEquals("fixture:seed", item.get("id").getAsString());
        assertEquals("seed", item.get("legacyRegistryName").getAsString());
        assertEquals("foreign/dropsidecar/Bootstrap", item.get("sourceFieldOwner").getAsString());

        assertEquals(2, block.getAsJsonObject("quantity").get("value").getAsInt());
        var damage = block.getAsJsonObject("damage").getAsJsonArray("byBlockMeta");
        assertEquals(16, damage.size());
        assertEquals(0, damage.get(0).getAsInt());
        assertEquals(7, damage.get(7).getAsInt());
        assertEquals(3, damage.get(11).getAsInt());
        assertEquals(7, damage.get(15).getAsInt());
        assertTrue(root.getAsJsonArray("diagnostics").isEmpty(), root.getAsJsonArray("diagnostics").toString());
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

    private static byte[] item() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropsidecar/Seed", null,
                "net/minecraft/item/Item", null);
        constructor(writer, "net/minecraft/item/Item");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] crop() {
        String owner = "foreign/dropsidecar/Crop";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        constructor(writer, "net/minecraft/block/Block");

        MethodVisitor item = writer.visitMethod(Opcodes.ACC_PUBLIC, "getItemDropped",
                "(ILjava/util/Random;I)Lnet/minecraft/item/Item;", null, null);
        item.visitCode();
        item.visitFieldInsn(Opcodes.GETSTATIC, "foreign/dropsidecar/Bootstrap", "dropItem", "Lnet/minecraft/item/Item;");
        item.visitInsn(Opcodes.ARETURN);
        end(item);

        MethodVisitor quantity = writer.visitMethod(Opcodes.ACC_PUBLIC, "quantityDropped", "(Ljava/util/Random;)I", null, null);
        quantity.visitCode();
        quantity.visitInsn(Opcodes.ICONST_2);
        quantity.visitInsn(Opcodes.IRETURN);
        end(quantity);

        MethodVisitor damage = writer.visitMethod(Opcodes.ACC_PUBLIC, "damageDropped", "(I)I", null, null);
        damage.visitCode();
        damage.visitVarInsn(Opcodes.ILOAD, 1);
        damage.visitIntInsn(Opcodes.BIPUSH, 7);
        damage.visitInsn(Opcodes.IAND);
        damage.visitInsn(Opcodes.IRETURN);
        end(damage);

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner = "foreign/dropsidecar/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dropItem", "Lnet/minecraft/item/Item;", null, null).visitEnd();
        constructor(writer, "java/lang/Object");
        itemHelper(writer, owner);
        blockHelper(writer, owner);

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();

        method.visitTypeInsn(Opcodes.NEW, "foreign/dropsidecar/Seed");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/dropsidecar/Seed", "<init>", "()V", false);
        method.visitLdcInsn("seed");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "item",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, owner, "dropItem", "Lnet/minecraft/item/Item;");

        method.visitTypeInsn(Opcodes.NEW, "foreign/dropsidecar/Crop");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/dropsidecar/Crop", "<init>", "()V", false);
        method.visitLdcInsn("crop");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        method.visitInsn(Opcodes.POP);

        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void itemHelper(ClassWriter writer, String owner) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "item",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
    }

    private static void blockHelper(ClassWriter writer, String owner) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
    }

    private static void constructor(ClassWriter writer, String parent) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
    }

    private static void end(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
