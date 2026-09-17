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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Adds only admission-proven constant base-behavior overrides to generated plain Entity classes. */
public final class LegacyPlainEntityConstantOverrideCodegenPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-constant-override-codegen.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plain-entity-constant-override-codegen"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path generatedPath = context.stagingDir().resolve(LegacyPlainEntityCodegenPass.OUTPUT);
        Path admissionPath = context.stagingDir().resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT);
        if (!Files.isRegularFile(generatedPath) || !Files.isRegularFile(admissionPath)) return;
        JsonObject generated = read(generatedPath), admission = read(admissionPath);
        if (!valid(generated, context.sourceHash()) || !valid(admission, context.sourceHash())) return;

        Map<String,JsonObject> admissionByKey = new LinkedHashMap<>();
        for (JsonElement element : array(admission, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!bool(rule, "admitted", false)) continue;
            String key = key(string(rule, "id", null), string(rule, "sourceClass", null));
            if (key != null) admissionByKey.putIfAbsent(key, rule);
        }

        generated.addProperty("constantBehaviorOverrideCodegenWired", true);
        JsonArray reportRules = new JsonArray();
        int complete = 0, patchedMethods = 0, blocked = 0;
        for (JsonElement element : array(generated, "generatedClasses")) {
            if (!element.isJsonObject()) continue;
            JsonObject generatedRule = element.getAsJsonObject();
            String id = string(generatedRule, "id", null), sourceClass = string(generatedRule, "sourceClass", null);
            String generatedInternalName = string(generatedRule, "generatedInternalName", null);
            JsonObject admissionRule = admissionByKey.get(key(id, sourceClass));
            JsonObject report = new JsonObject();
            copy(generatedRule, report, "id"); copy(generatedRule, report, "sourceClass"); copy(generatedRule, report, "generatedClass");
            JsonArray blockers = new JsonArray();
            JsonArray overrides = admissionRule == null ? new JsonArray() : array(admissionRule, "constantBehaviorOverrides").deepCopy();

            if (admissionRule == null) blockers.add("admitted-runtime-rule-missing");
            if (generatedInternalName == null || generatedInternalName.isBlank()) blockers.add("generated-class-identity-missing");
            Set<String> targetSignatures = new LinkedHashSet<>();
            for (JsonElement overrideElement : overrides) {
                if (!overrideElement.isJsonObject()) { blockers.add("malformed-constant-override"); continue; }
                JsonObject override = overrideElement.getAsJsonObject();
                if (!supported(override)) { blockers.add("unsupported-or-unproven-constant-override"); continue; }
                String signature = string(override, "targetMethod", "") + string(override, "targetDescriptor", "");
                if (!targetSignatures.add(signature)) blockers.add("duplicate-constant-override-target:" + signature);
            }

            int patched = 0;
            if (blockers.isEmpty()) {
                Path classPath = context.stagingDir().resolve(generatedInternalName + ".class");
                if (!Files.isRegularFile(classPath)) blockers.add("generated-class-file-missing");
                else {
                    byte[] original = Files.readAllBytes(classPath);
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    try { new ClassReader(original).accept(node, 0); }
                    catch (RuntimeException malformed) { blockers.add("generated-class-unreadable"); }
                    if (blockers.isEmpty()) {
                        for (JsonElement overrideElement : overrides) {
                            JsonObject override = overrideElement.getAsJsonObject();
                            String method = string(override, "targetMethod", null), descriptor = string(override, "targetDescriptor", null);
                            if (node.methods.stream().anyMatch(existing -> existing.name.equals(method) && existing.desc.equals(descriptor))) {
                                blockers.add("generated-target-method-already-present:" + method + descriptor);
                                break;
                            }
                        }
                    }
                    if (blockers.isEmpty()) {
                        for (JsonElement overrideElement : overrides) {
                            JsonObject override = overrideElement.getAsJsonObject();
                            String descriptor = override.get("targetDescriptor").getAsString();
                            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC,
                                    override.get("targetMethod").getAsString(), descriptor, null, null);
                            method.instructions.add(new InsnNode(override.get("constantBoolean").getAsBoolean()
                                    ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                            method.instructions.add(new InsnNode(Opcodes.IRETURN));
                            method.maxStack = 1;
                            method.maxLocals = instanceLocalSlots(descriptor);
                            node.methods.add(method); patched++;
                        }
                        ClassWriter writer = new ClassWriter(0);
                        node.accept(writer);
                        Files.write(classPath, writer.toByteArray());
                    }
                }
            }

            boolean codegenComplete = blockers.isEmpty();
            generatedRule.addProperty("constantBehaviorOverrideCodegenComplete", codegenComplete);
            generatedRule.addProperty("constantBehaviorOverrideCount", overrides.size());
            generatedRule.add("constantBehaviorOverrides", overrides.deepCopy());
            report.addProperty("constantBehaviorOverrideCodegenComplete", codegenComplete);
            report.addProperty("constantBehaviorOverrideCount", overrides.size());
            report.addProperty("patchedMethodCount", codegenComplete ? patched : 0);
            report.add("constantBehaviorOverrides", overrides.deepCopy());
            report.add("blockers", blockers);
            reportRules.add(report);
            if (codegenComplete) { complete++; patchedMethods += patched; } else blocked++;
        }

        Files.writeString(generatedPath, GSON.toJson(generated) + "\n", StandardCharsets.UTF_8);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1); root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("constantBehaviorOverrideCodegenWired", true); root.add("rules", reportRules);
        root.addProperty("codegenCompleteClasses", complete); root.addProperty("patchedConstantOverrideMethods", patchedMethods);
        root.addProperty("blockedCodegenClasses", blocked);
        Path output = context.stagingDir().resolve(OUTPUT); Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (patchedMethods > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-CONSTCODEGEN-0001", SupportLevel.RUNTIME_BRIDGE,
                "Generated " + patchedMethods + " proven constant modern Entity override method(s) across " + complete + " plain Entity class(es)." );
        if (blocked > 0) context.diagnostics().warning("LFB-CONVERT-ENTITY-CONSTCODEGEN-0002", SupportLevel.RUNTIME_BRIDGE,
                "Blocked constant-behavior codegen for " + blocked + " generated plain Entity class(es); runtime candidacy remains fail-closed for those classes.");
    }

    private static boolean supported(JsonObject value) {
        String sourceKind = string(value, "sourceKind", null);
        return LegacyEntityConstantOverridePass.supported(sourceKind)
                && LegacyEntityConstantOverridePass.targetMethod(sourceKind).equals(string(value, "targetMethod", null))
                && LegacyEntityConstantOverridePass.targetDescriptor(sourceKind).equals(string(value, "targetDescriptor", null))
                && LegacyEntityConstantOverridePass.mappingSemantics(sourceKind).equals(string(value, "mappingSemantics", null))
                && bool(value, "sourceConstantProofComplete", false) && bool(value, "runtimeCodegenReady", false)
                && value.has("constantBoolean") && value.get("constantBoolean").isJsonPrimitive()
                && value.get("constantBoolean").getAsJsonPrimitive().isBoolean();
    }
    static int instanceLocalSlots(String descriptor) {
        int slots = 1;
        for (Type argument : Type.getArgumentTypes(descriptor)) slots += argument.getSize();
        return slots;
    }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static boolean valid(JsonObject root, String hash) { return integer(root, "schemaVersion", -1) == 1 && hash.equals(string(root, "sourceSha256", "")); }
    private static JsonArray array(JsonObject root, String name) { JsonElement value = root.get(name); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray(); }
    private static boolean bool(JsonObject root, String name, boolean fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback; }
    private static int integer(JsonObject root, String name, int fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback; }
    private static String string(JsonObject root, String name, String fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback; }
    private static String key(String id, String sourceClass) { return id == null || sourceClass == null ? null : id + '\u0000' + sourceClass; }
    private static void copy(JsonObject source, JsonObject target, String name) { JsonElement value = source.get(name); if (value != null) target.add(name, value.deepCopy()); }
}
