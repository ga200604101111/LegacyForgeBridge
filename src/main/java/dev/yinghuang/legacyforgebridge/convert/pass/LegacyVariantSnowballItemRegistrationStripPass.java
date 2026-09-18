package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRegistrationCallStripper;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Neutralizes only a uniquely proven source GameRegistry.registerItem call already replaced by the
 * complete variant-snowball runtime. Argument evaluation is deliberately preserved.
 */
public final class LegacyVariantSnowballItemRegistrationStripPass implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/variant-snowball-item-registration-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-variant-snowball-item-registration-strip";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path runtimePath =
                context.stagingDir().resolve(LegacyVariantSnowballRuntimePass.OUTPUT);
        if (!Files.isRegularFile(runtimePath)) return;

        JsonObject runtime = JsonParser.parseString(
                Files.readString(runtimePath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(runtime, "schemaVersion", -1) != LegacyVariantSnowballRuntimePass.SCHEMA
                || !context.sourceHash().equals(string(runtime, "sourceSha256", ""))
                || !bool(runtime, "runtimeImplementationWired", false)) {
            return;
        }

        LegacyRegistryAnalyzer.Analysis registry =
                new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        SourceMethods sourceMethods = inspectSourceMethods(context.sourceJar());

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("itemRegistrationStripWired", true);
        root.addProperty("argumentEvaluationPreserved", true);
        root.addProperty("constructorSideEffectsPreserved", true);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int strippedRules = 0;
        int strippedSites = 0;

        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject runtimeRule = element.getAsJsonObject();
            if (!completeRuntime(runtimeRule)) continue;

            String id = string(runtimeRule, "id", null);
            String registryName = string(runtimeRule, "legacyRegistryName", null);
            String sourceItemClass = string(runtimeRule, "sourceItemClass", null);
            if (id == null || registryName == null || sourceItemClass == null) continue;

            List<LegacyRegistryAnalyzer.Registration> matches = registry.items().stream()
                    .filter(registration ->
                            registryName.equals(registration.registryName())
                                    && sourceItemClass.equals(registration.implementationClass()))
                    .toList();

            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("legacyRegistryName", registryName);
            value.addProperty("sourceItemClass", sourceItemClass);
            JsonArray blockers = new JsonArray();
            int sites = 0;
            String sourceOwner = null;
            String sourceMethod = null;
            String sourceDescriptor = null;

            if (matches.size() != 1) {
                blockers.add(matches.isEmpty()
                        ? "exact-item-registration-proof-missing"
                        : "ambiguous-item-registration-proof:" + matches.size());
            } else {
                LegacyRegistryAnalyzer.Registration registration = matches.getFirst();
                sourceOwner = registration.sourceOwner();
                sourceMethod = registration.sourceMethod();
                sourceDescriptor = registration.sourceDescriptor();

                long sameDirectSource = registry.items().stream()
                        .filter(candidate ->
                                sourceOwner.equals(candidate.sourceOwner())
                                        && sourceMethod.equals(candidate.sourceMethod())
                                        && sourceDescriptor.equals(candidate.sourceDescriptor()))
                        .count();
                if (sameDirectSource != 1) {
                    blockers.add("shared-registerItem-direct-source:" + sameDirectSource);
                }

                String sourceSafety = sourceMethods.safety(
                        sourceOwner, sourceMethod, sourceDescriptor);
                if (sourceSafety != null) blockers.add(sourceSafety);

                Path classPath = context.stagingDir().resolve(sourceOwner + ".class");
                if (!Files.isRegularFile(classPath)) {
                    blockers.add("item-registration-source-class-missing");
                }

                if (blockers.isEmpty()) {
                    var target = new LegacyItemRegistrationCallStripper.Target(
                            sourceMethod, sourceDescriptor);
                    var result = new LegacyItemRegistrationCallStripper().strip(
                            Files.readAllBytes(classPath), target);
                    sites = result.strippedSites();
                    for (String blocker : result.blockers()) blockers.add(blocker);
                    if (sites == 1 && blockers.isEmpty()) {
                        Files.write(classPath, result.bytes());
                    }
                }
            }

            if (sourceOwner != null) value.addProperty("sourceOwner", sourceOwner);
            if (sourceMethod != null) value.addProperty("sourceMethod", sourceMethod);
            if (sourceDescriptor != null) value.addProperty("sourceDescriptor", sourceDescriptor);
            value.addProperty("itemRegistrationStripComplete",
                    sites == 1 && blockers.isEmpty());
            value.addProperty("strippedItemRegistrationSites", sites);
            value.addProperty("argumentEvaluationPreserved", true);
            value.addProperty("constructorSideEffectsPreserved", true);
            value.add("blockers", blockers);
            rules.add(value);

            if (sites == 1 && blockers.isEmpty()) {
                strippedRules++;
                strippedSites += sites;
            }
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty("itemRegistrationStripCompleteRules", strippedRules);
        root.addProperty("strippedItemRegistrationSites", strippedSites);
        root.addProperty("blockedItemRegistrationStripRules",
                rules.size() - strippedRules);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (strippedRules > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-ITEMSTRIP-0001",
                    SupportLevel.ADAPTED,
                    "Neutralized " + strippedSites
                            + " uniquely proven legacy registerItem callsite(s) for "
                            + strippedRules
                            + " complete variant-snowball runtime rule(s) while preserving "
                            + "argument and constructor evaluation.");
        }
        if (strippedRules < rules.size()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-ITEMSTRIP-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + (rules.size() - strippedRules)
                            + " variant-snowball item registration(s) because the direct source "
                            + "was shared, externally callable, ambiguous, or otherwise not safe "
                            + "to neutralize independently.");
        }
    }

    private static boolean completeRuntime(JsonObject rule) {
        return bool(rule, "runtimeRuleReady", false)
                && bool(rule, "itemRuntimeWired", false)
                && bool(rule, "projectileRuntimeWired", false)
                && bool(rule, "projectileImpactRuntimeWired", false)
                && bool(rule, "rendererRuntimeWired", false)
                && bool(rule, "runtimeImplementationWired", false);
    }

    private static SourceMethods inspectSourceMethods(Path sourceJar) throws Exception {
        Map<String, MethodInfo> methods = new LinkedHashMap<>();
        Map<String, Integer> callCounts = new LinkedHashMap<>();

        try (JarFile jar = new JarFile(sourceJar.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(
                            node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    for (MethodNode method : node.methods) {
                        String key = key(node.name, method.name, method.desc);
                        methods.put(key, new MethodInfo(method.access));
                        for (var instruction : method.instructions) {
                            if (instruction instanceof MethodInsnNode call) {
                                callCounts.merge(
                                        key(call.owner, call.name, call.desc), 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        return new SourceMethods(methods, callCounts);
    }

    private record MethodInfo(int access) { }

    private record SourceMethods(
            Map<String, MethodInfo> methods,
            Map<String, Integer> callCounts) {
        String safety(String owner, String name, String descriptor) {
            if ("<clinit>".equals(name)) return null;
            if (descriptor.contains("Lcpw/mods/fml/common/event/FML")
                    && descriptor.endsWith(")V")) {
                return null;
            }

            MethodInfo info = methods.get(key(owner, name, descriptor));
            if (info == null) return "registerItem-direct-source-method-missing";
            if ((info.access() & Opcodes.ACC_PRIVATE) == 0) {
                return "registerItem-helper-not-private";
            }

            int calls = callCounts.getOrDefault(key(owner, name, descriptor), 0);
            if (calls != 1) {
                return "registerItem-private-helper-call-count:" + calls;
            }
            return null;
        }
    }

    private static String key(String owner, String name, String descriptor) {
        return owner + "\u0000" + name + "\u0000" + descriptor;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
