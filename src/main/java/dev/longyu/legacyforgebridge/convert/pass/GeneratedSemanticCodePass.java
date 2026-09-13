package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Compiles neutral conversion semantics into classes owned by the converted mod. */
public final class GeneratedSemanticCodePass implements ConversionPass {
    @Override
    public String id() { return "generated-semantic-mod-code"; }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path manifest = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        JsonObject root = Files.isRegularFile(manifest) ? read(manifest) : new JsonObject();
        String contentBinary = GeneratedModEntrypointPass.generatedContentClass(context.metadata());
        String clientBinary = GeneratedModEntrypointPass.generatedClientClass(context.metadata());
        Path contentPath = context.stagingDir().resolve(contentBinary.replace('.', '/') + ".class");
        Path clientPath = context.stagingDir().resolve(clientBinary.replace('.', '/') + ".class");
        Files.createDirectories(contentPath.getParent());
        Files.createDirectories(clientPath.getParent());
        GenerationStats stats = new GenerationStats();
        Files.write(contentPath, generateContent(contentBinary.replace('.', '/'), context.metadata().fabricId(), root, stats));
        Files.write(clientPath, generateClient(clientBinary.replace('.', '/'), context.metadata().fabricId(), root, stats));
        context.diagnostics().info("LFB-CONVERT-CODEGEN-0001", SupportLevel.ADAPTED,
                "Compiled neutral conversion semantics into mod-owned Java 21 bytecode: items=" + stats.items
                        + ", creativeTabs=" + stats.creativeTabs + ", equipmentRenderers=" + stats.equipmentRenderers + ".");
    }

    private static byte[] generateContent(String internalName, String modId, JsonObject root, GenerationStats stats) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        privateConstructor(writer);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "initialize", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn(modId);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/convert/runtime/GeneratedModSupport", "beginMod", "(Ljava/lang/String;)V", false);
        JsonArray items = root.getAsJsonArray("items");
        if (items != null) {
            for (JsonElement element : items) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = string(item, "id", null);
                if (id == null) continue;
                method.visitLdcInsn(id);
                method.visitLdcInsn(string(item, "kind", "item"));
                method.visitLdcInsn(string(item, "descriptionKey", "item." + id.replace(':', '.')));
                pushInt(method, integer(item, "durability", 0));
                method.visitLdcInsn(number(item, "attackDamage", 0.0F));
                method.visitLdcInsn(number(item, "attackSpeed", 0.0F));
                method.visitLdcInsn(number(item, "armor", 0.0F));
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/convert/runtime/GeneratedModSupport", "registerItem",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IFFF)V", false);
                stats.items++;
            }
        }
        JsonArray tabs = root.getAsJsonArray("creativeTabs");
        if (tabs != null) {
            for (JsonElement element : tabs) {
                if (!element.isJsonObject()) continue;
                JsonObject tab = element.getAsJsonObject();
                String id = string(tab, "id", null);
                JsonArray itemIds = tab.getAsJsonArray("items");
                if (id == null || itemIds == null || itemIds.isEmpty()) continue;
                method.visitLdcInsn(id);
                method.visitLdcInsn(string(tab, "titleKey", ""));
                method.visitLdcInsn(string(tab, "title", id));
                method.visitLdcInsn(string(tab, "icon", itemIds.get(0).getAsString()));
                emitStringArray(method, primitiveStrings(itemIds));
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/convert/runtime/GeneratedModSupport", "registerCreativeTab",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V", false);
                stats.creativeTabs++;
            }
        }
        method.visitLdcInsn(modId);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/convert/runtime/GeneratedModSupport", "finishMod", "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] generateClient(String internalName, String modId, JsonObject root, GenerationStats stats) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        privateConstructor(writer);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "initialize", "()V", null, null);
        method.visitCode();
        JsonArray items = root.getAsJsonArray("items");
        if (items != null) {
            for (JsonElement element : items) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                JsonObject render = object(item, "equipmentRender");
                String itemId = string(item, "id", null);
                if (render == null || itemId == null) continue;
                JsonArray parts = render.getAsJsonArray("parts");
                if (parts == null || parts.isEmpty()) continue;
                List<String> models = new ArrayList<>();
                List<String> textureSets = new ArrayList<>();
                for (JsonElement partElement : parts) {
                    if (!partElement.isJsonObject()) continue;
                    JsonObject part = partElement.getAsJsonObject();
                    String model = string(part, "model", null);
                    if (model == null) continue;
                    models.add(model);
                    List<String> textures = new ArrayList<>();
                    JsonArray textureArray = part.getAsJsonArray("textures");
                    if (textureArray != null) textures.addAll(primitiveStrings(textureArray));
                    String single = string(part, "texture", null);
                    if (single != null) textures.add(0, single);
                    textureSets.add(String.join("\u001f", textures));
                }
                if (models.isEmpty()) continue;
                method.visitLdcInsn(itemId);
                method.visitLdcInsn(string(render, "anchor", "root"));
                method.visitInsn(bool(render, "autoCenter", true) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                method.visitLdcInsn(number(render, "fit", 1.0F));
                method.visitInsn(bool(render, "translucent", false) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                emitStringArray(method, models);
                emitStringArray(method, textureSets);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/render/GeneratedEquipmentSupport", "register",
                        "(Ljava/lang/String;Ljava/lang/String;ZFZ[Ljava/lang/String;[Ljava/lang/String;)V", false);
                stats.equipmentRenderers++;
            }
        }
        method.visitLdcInsn(modId);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/render/GeneratedEquipmentSupport", "finishMod", "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void privateConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
    }

    private static void emitStringArray(MethodVisitor method, List<String> values) {
        pushInt(method, values.size());
        method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < values.size(); i++) {
            method.visitInsn(Opcodes.DUP);
            pushInt(method, i);
            method.visitLdcInsn(values.get(i));
            method.visitInsn(Opcodes.AASTORE);
        }
    }

    private static void pushInt(MethodVisitor method, int value) {
        if (value == -1) method.visitInsn(Opcodes.ICONST_M1);
        else if (value >= 0 && value <= 5) method.visitInsn(Opcodes.ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) method.visitIntInsn(Opcodes.BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) method.visitIntInsn(Opcodes.SIPUSH, value);
        else method.visitLdcInsn(value);
    }

    private static List<String> primitiveStrings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonPrimitive()) result.add(element.getAsString());
        return result;
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static JsonObject object(JsonObject parent, String key) { JsonElement v = parent.get(key); return v != null && v.isJsonObject() ? v.getAsJsonObject() : null; }
    private static String string(JsonObject object, String key, String fallback) { JsonElement v = object.get(key); return v != null && v.isJsonPrimitive() ? v.getAsString() : fallback; }
    private static int integer(JsonObject object, String key, int fallback) { JsonElement v = object.get(key); return v != null && v.isJsonPrimitive() ? v.getAsInt() : fallback; }
    private static float number(JsonObject object, String key, float fallback) { JsonElement v = object.get(key); return v != null && v.isJsonPrimitive() ? v.getAsFloat() : fallback; }
    private static boolean bool(JsonObject object, String key, boolean fallback) { JsonElement v = object.get(key); return v != null && v.isJsonPrimitive() ? v.getAsBoolean() : fallback; }
    private static final class GenerationStats { int items; int creativeTabs; int equipmentRenderers; }
}
