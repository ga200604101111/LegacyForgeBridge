package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Generates one isolated modern Entity subclass for each admitted plain synchronized-data entity. */
public final class LegacyPlainEntityCodegenPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-generated-classes.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String ENTITY_TYPE = "net/minecraft/world/entity/EntityType";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String SYNCHED = "net/minecraft/network/syncher/SynchedEntityData";
    private static final String BUILDER = SYNCHED + "$Builder";
    private static final String ACCESSOR = "net/minecraft/network/syncher/EntityDataAccessor";
    private static final String SERIALIZER = "net/minecraft/network/syncher/EntityDataSerializer";
    private static final String SERIALIZERS = "net/minecraft/network/syncher/EntityDataSerializers";
    private static final String ACCESSOR_DESC = "L" + ACCESSOR + ";";
    private static final String SERIALIZER_DESC = "L" + SERIALIZER + ";";
    private static final String ENTITY_CTOR_DESC = "(L" + ENTITY_TYPE + ";L" + LEVEL + ";)V";
    private static final String DEFINE_ID_DESC = "(Ljava/lang/Class;" + SERIALIZER_DESC + ")" + ACCESSOR_DESC;
    private static final String BUILDER_DEFINE_DESC = "(" + ACCESSOR_DESC + "Ljava/lang/Object;)L" + BUILDER + ";";

    private record Entry(int sourceIndex, String serializer, String modernValueKind,
                         String adapter, JsonElement defaultValue) { }

    @Override public String id() { return "legacy-plain-entity-class-codegen"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path admissionPath = context.stagingDir().resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT);
        if (!Files.isRegularFile(admissionPath)) return;
        JsonObject admission = JsonParser.parseString(Files.readString(admissionPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(admission, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(admission, "sourceSha256", ""))) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeClassGenerationWired", true);
        root.addProperty("entityTypeRegistrationWired", false);
        root.addProperty("runtimeImplementationWired", false);
        JsonArray generated = new JsonArray();
        JsonArray skipped = new JsonArray();

        for (JsonElement element : array(admission, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!bool(rule, "admitted", false)) continue;
            if (!LegacyEntityRuntimeAdmissionPass.FAMILY_PLAIN_SYNCHED_DATA_ONLY.equals(string(rule, "family", ""))) continue;

            List<Entry> entries = parseEntries(rule);
            if (entries == null) {
                JsonObject skip = new JsonObject();
                copy(rule, skip, "id"); copy(rule, skip, "legacyRegistryName"); copy(rule, skip, "sourceClass");
                skip.addProperty("reason", "Admitted entity contains malformed/unsupported synchronized-data mapping entries.");
                skipped.add(skip);
                continue;
            }

            String id = string(rule, "id", null);
            if (id == null || id.isBlank()) continue;
            String binaryName = generatedBinaryName(context, id);
            String internalName = binaryName.replace('.', '/');
            Path classPath = context.stagingDir().resolve(internalName + ".class");
            Files.createDirectories(classPath.getParent());
            Files.write(classPath, generate(internalName, entries));

            JsonObject item = new JsonObject();
            copy(rule, item, "id"); copy(rule, item, "legacyRegistryName"); copy(rule, item, "sourceClass");
            copy(rule, item, "trackingRange"); copy(rule, item, "updateFrequency"); copy(rule, item, "velocityUpdates");
            copy(rule, item, "width"); copy(rule, item, "height");
            item.addProperty("family", LegacyEntityRuntimeAdmissionPass.FAMILY_PLAIN_SYNCHED_DATA_ONLY);
            item.addProperty("generatedClass", binaryName);
            item.addProperty("generatedInternalName", internalName);
            item.addProperty("classGenerated", true);
            item.addProperty("synchedDataAccessorCount", entries.size());
            item.addProperty("entityTypeRegistrationWired", false);
            generated.add(item);
        }

        root.add("generatedClasses", generated);
        root.add("skipped", skipped);
        root.addProperty("generatedClassCount", generated.size());
        root.addProperty("skippedClassCount", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!generated.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-CODEGEN-0001", SupportLevel.RUNTIME_BRIDGE,
                "Generated " + generated.size() + " isolated Java 21 plain Entity subclass(es) with modern SynchedEntityData accessor definitions; EntityType registration remains intentionally unwired.");
        if (!skipped.isEmpty()) context.diagnostics().warning("LFB-CONVERT-ENTITY-CODEGEN-0002", SupportLevel.RUNTIME_BRIDGE,
                "Skipped " + skipped.size() + " admitted entity rule(s) because generated synchronized-data class inputs were malformed or unsupported.");
    }

    private static List<Entry> parseEntries(JsonObject rule) {
        List<Entry> output = new ArrayList<>();
        java.util.HashSet<Integer> indices = new java.util.HashSet<>();
        for (JsonElement element : array(rule, "synchedDataEntries")) {
            if (!element.isJsonObject()) return null;
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("sourceIndex") || !entry.has("serializer") || !entry.has("modernValueKind")
                    || !entry.has("adapter") || !entry.has("defaultValue")) return null;
            int index = entry.get("sourceIndex").getAsInt();
            if (index < 0 || index > 31 || !indices.add(index)) return null;
            String serializer = entry.get("serializer").getAsString();
            String modernKind = entry.get("modernValueKind").getAsString();
            String adapter = entry.get("adapter").getAsString();
            if (!supported(serializer, modernKind, adapter, entry.get("defaultValue"))) return null;
            output.add(new Entry(index, serializer, modernKind, adapter, entry.get("defaultValue").deepCopy()));
        }
        output.sort(Comparator.comparingInt(Entry::sourceIndex));
        return List.copyOf(output);
    }

    private static boolean supported(String serializer, String kind, String adapter, JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return false;
        try {
            return switch (serializer) {
                case "BYTE" -> "byte".equals(kind) && "identity".equals(adapter)
                        && value.getAsInt() >= Byte.MIN_VALUE && value.getAsInt() <= Byte.MAX_VALUE;
                case "INT" -> "int".equals(kind) && ("identity".equals(adapter) || "signed_short_widen".equals(adapter));
                case "FLOAT" -> "float".equals(kind) && "identity".equals(adapter) && Float.isFinite(value.getAsFloat());
                case "STRING" -> "string".equals(kind) && "identity".equals(adapter) && value.getAsJsonPrimitive().isString();
                default -> false;
            };
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static byte[] generate(String internalName, List<Entry> entries) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                internalName, null, ENTITY, null);

        for (Entry entry : entries)
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    field(entry), ACCESSOR_DESC, null, null).visitEnd();

        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        for (Entry entry : entries) {
            clinit.visitLdcInsn(Type.getObjectType(internalName));
            clinit.visitFieldInsn(Opcodes.GETSTATIC, SERIALIZERS, entry.serializer(), SERIALIZER_DESC);
            clinit.visitMethodInsn(Opcodes.INVOKESTATIC, SYNCHED, "defineId", DEFINE_ID_DESC, false);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, internalName, field(entry), ACCESSOR_DESC);
        }
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ENTITY_CTOR_DESC, null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitVarInsn(Opcodes.ALOAD, 2);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, ENTITY, "<init>", ENTITY_CTOR_DESC, false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor define = writer.visitMethod(Opcodes.ACC_PROTECTED, "defineSynchedData",
                "(L" + BUILDER + ";)V", null, null);
        define.visitCode();
        for (Entry entry : entries) {
            define.visitVarInsn(Opcodes.ALOAD, 1);
            define.visitFieldInsn(Opcodes.GETSTATIC, internalName, field(entry), ACCESSOR_DESC);
            pushDefault(define, entry);
            define.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BUILDER, "define", BUILDER_DEFINE_DESC, false);
            define.visitInsn(Opcodes.POP);
        }
        define.visitInsn(Opcodes.RETURN);
        define.visitMaxs(0, 0);
        define.visitEnd();

        emptyProtected(writer, "readAdditionalSaveData", "(Lnet/minecraft/world/level/storage/ValueInput;)V");
        emptyProtected(writer, "addAdditionalSaveData", "(Lnet/minecraft/world/level/storage/ValueOutput;)V");

        MethodVisitor hurt = writer.visitMethod(Opcodes.ACC_PUBLIC, "hurtServer",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", null, null);
        hurt.visitCode();
        hurt.visitInsn(Opcodes.ICONST_0);
        hurt.visitInsn(Opcodes.IRETURN);
        hurt.visitMaxs(0, 0);
        hurt.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emptyProtected(ClassWriter writer, String name, String descriptor) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PROTECTED, name, descriptor, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void pushDefault(MethodVisitor method, Entry entry) {
        switch (entry.serializer()) {
            case "BYTE" -> {
                pushInt(method, entry.defaultValue().getAsInt());
                method.visitInsn(Opcodes.I2B);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            }
            case "INT" -> {
                pushInt(method, entry.defaultValue().getAsInt());
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            }
            case "FLOAT" -> {
                method.visitLdcInsn(entry.defaultValue().getAsFloat());
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            }
            case "STRING" -> method.visitLdcInsn(entry.defaultValue().getAsString());
            default -> throw new IllegalArgumentException("Unsupported serializer " + entry.serializer());
        }
    }

    private static void pushInt(MethodVisitor method, int value) {
        if (value == -1) method.visitInsn(Opcodes.ICONST_M1);
        else if (value >= 0 && value <= 5) method.visitInsn(Opcodes.ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) method.visitIntInsn(Opcodes.BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) method.visitIntInsn(Opcodes.SIPUSH, value);
        else method.visitLdcInsn(value);
    }

    private static String field(Entry entry) { return "DATA_" + entry.sourceIndex(); }

    private static String generatedBinaryName(ConversionContext context, String id) {
        String content = GeneratedModEntrypointPass.generatedContentClass(context.metadata());
        String base = content.substring(0, content.lastIndexOf('.'));
        String safe = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_");
        if (safe.length() > 48) safe = safe.substring(0, 48);
        if (safe.isBlank()) safe = "legacy_entity";
        return base + ".entity.PlainEntity_" + safe + "_" + Integer.toUnsignedString(id.hashCode(), 16);
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }
    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    private static void copy(JsonObject source, JsonObject target, String name) {
        JsonElement value = source.get(name);
        if (value != null) target.add(name, value.deepCopy());
    }
}
