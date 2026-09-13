package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.longyu.legacyforgebridge.convert.profile.RpgTool1Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RpgTool1PresentationPassTest {
    private static final String CREATIVE_TABS = "net/minecraft/creativetab/CreativeTabs";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String MOD_ITEMS = "example/ModItems";

    @TempDir
    Path tempDir;

    @Test
    void emitsSeparateExtractedCreativeTabsAndEquipmentRenderDefinitions() throws Exception {
        Path staging = tempDir.resolve("staging");
        Path manifest = staging.resolve("legacyforgebridge/converted-content.json");
        Files.createDirectories(manifest.getParent());

        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        items.add(item("rpgtool1:dark_sword", "sword"));
        items.add(item("rpgtool1:wing01", "wing"));
        items.add(item("rpgtool1:buff2_3", "circle"));
        root.add("items", items);
        Files.writeString(manifest, root.toString(), StandardCharsets.UTF_8);

        Path sourceJar = createLegacySourceJar();
        RpgTool1PresentationPass pass = new RpgTool1PresentationPass();
        pass.apply(context(staging, sourceJar));

        JsonObject converted;
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            converted = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonArray tabs = converted.getAsJsonArray("creativeTabs");
        assertEquals(2, tabs.size());

        JsonObject weapons = tabs.get(0).getAsJsonObject();
        assertEquals("rpgtool1:weapons", weapons.get("id").getAsString());
        assertEquals(
                "lfb.converted.rpgtool1.itemGroup.weapons",
                weapons.get("titleKey").getAsString()
        );
        assertEquals("rpgtool1:dark_sword", weapons.get("icon").getAsString());
        assertEquals(1, weapons.getAsJsonArray("items").size());

        JsonObject gear = tabs.get(1).getAsJsonObject();
        assertEquals("rpgtool1:gear", gear.get("id").getAsString());
        assertEquals(
                "lfb.converted.rpgtool1.itemGroup.gear",
                gear.get("titleKey").getAsString()
        );
        assertEquals("rpgtool1:wing01", gear.get("icon").getAsString());
        assertEquals(2, gear.getAsJsonArray("items").size());

        assertEquals("rpgtool1:weapons", converted.getAsJsonArray("items").get(0).getAsJsonObject()
                .get("creativeTab").getAsString());
        assertEquals("rpgtool1:gear", converted.getAsJsonArray("items").get(1).getAsJsonObject()
                .get("creativeTab").getAsString());
        assertEquals("rpgtool1:gear", converted.getAsJsonArray("items").get(2).getAsJsonObject()
                .get("creativeTab").getAsString());

        JsonObject wingRender = converted.getAsJsonArray("items").get(1).getAsJsonObject()
                .getAsJsonObject("equipmentRender");
        assertNotNull(wingRender);
        assertEquals(2, wingRender.getAsJsonArray("parts").size());
        assertEquals(
                "rpgtool1:textures/wings/left_wing01.obj",
                wingRender.getAsJsonArray("parts").get(0).getAsJsonObject().get("model").getAsString()
        );

        JsonObject circleRender = converted.getAsJsonArray("items").get(2).getAsJsonObject()
                .getAsJsonObject("equipmentRender");
        assertNotNull(circleRender);
        assertEquals(
                "rpgtool1:textures/circle/buff2.obj",
                circleRender.getAsJsonArray("parts").get(0).getAsJsonObject().get("model").getAsString()
        );
    }

    private Path createLegacySourceJar() throws Exception {
        Path source = tempDir.resolve("RPGTool1-1.1-1.7.10.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(source))) {
            writeClass(jar, "example/WeaponsTab", tabClass("example/WeaponsTab", "dark_sword"));
            writeClass(jar, "example/GearTab", tabClass("example/GearTab", "wing01"));
            writeClass(jar, MOD_ITEMS, modItemsClass());
        }
        return source;
    }

    private static byte[] tabClass(String className, String iconField) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, className, null, CREATIVE_TABS, null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, CREATIVE_TABS, "<init>", "(Ljava/lang/String;)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 2);
        constructor.visitEnd();

        MethodVisitor icon = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_78016_d", "()L" + ITEM + ";", null, null);
        icon.visitCode();
        icon.visitFieldInsn(Opcodes.GETSTATIC, MOD_ITEMS, iconField, "L" + ITEM + ";");
        icon.visitInsn(Opcodes.ARETURN);
        icon.visitMaxs(1, 1);
        icon.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] modItemsClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, MOD_ITEMS, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "weaponsTab", "L" + CREATIVE_TABS + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "gearTab", "L" + CREATIVE_TABS + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dark_sword", "L" + ITEM + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "wing01", "L" + ITEM + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "buff2_3", "L" + ITEM + ";", null, null).visitEnd();

        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        createTab(clinit, "example/WeaponsTab", "weapons", "weaponsTab");
        createTab(clinit, "example/GearTab", "gear", "gearTab");
        createItem(clinit, "dark_sword", "weaponsTab");
        createItem(clinit, "wing01", "gearTab");
        createItem(clinit, "buff2_3", "gearTab");
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(4, 0);
        clinit.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void createTab(MethodVisitor method, String tabClass, String label, String field) {
        method.visitTypeInsn(Opcodes.NEW, tabClass);
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn(label);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, tabClass, "<init>", "(Ljava/lang/String;)V", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, MOD_ITEMS, field, "L" + CREATIVE_TABS + ";");
    }

    private static void createItem(MethodVisitor method, String name, String tabField) {
        method.visitTypeInsn(Opcodes.NEW, ITEM);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, ITEM, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITEM, "func_77655_b", "(Ljava/lang/String;)L" + ITEM + ";", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, MOD_ITEMS, tabField, "L" + CREATIVE_TABS + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITEM, "func_77637_a", "(L" + CREATIVE_TABS + ";)L" + ITEM + ";", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, MOD_ITEMS, name, "L" + ITEM + ";");
    }

    private static void writeClass(JarOutputStream jar, String name, byte[] bytes) throws Exception {
        jar.putNextEntry(new JarEntry(name + ".class"));
        jar.write(bytes);
        jar.closeEntry();
    }

    private static JsonObject item(String id, String kind) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("kind", kind);
        return item;
    }

    private ConversionContext context(Path staging, Path sourceJar) {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "RPGTool1-1.1-1.7.10.jar",
                "mcmod.info",
                List.of(new LegacyModMetadata.ModEntry("rpgtool1", "RPGTool1", "1.0", "1.7.10", List.of()))
        );
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "RPGTool1-1.1-1.7.10.jar",
                53,
                0,
                true,
                true,
                29,
                104,
                0,
                1,
                Set.of("cpw/mods/fml/common/Mod"),
                Set.of(),
                Set.of("org/lwjgl/opengl/GL11")
        );
        return new ConversionContext(
                sourceJar,
                staging,
                tempDir.resolve("candidate.jar"),
                RpgTool1Profile.CORPUS_SHA256,
                14_556_748L,
                metadata,
                analysis,
                new DiagnosticCollector(),
                "rpgtool1-1.7.10"
        );
    }
}
