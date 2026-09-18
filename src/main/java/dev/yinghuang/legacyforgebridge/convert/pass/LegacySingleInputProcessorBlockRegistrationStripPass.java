package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockRegistrationCallStripper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Neutralizes only a uniquely proven source GameRegistry.registerBlock call already replaced by a
 * runtime-complete single-input processor. Block constructor/argument evaluation is preserved.
 */
public final class LegacySingleInputProcessorBlockRegistrationStripPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-block-registration-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-single-input-processor-block-registration-strip";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        if (!Files.isRegularFile(processorPath)) return;

        JsonObject processor = JsonParser.parseString(
                Files.readString(processorPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(processor, "schemaVersion", -1) != 4
                || !context.sourceHash().equals(string(processor, "sourceSha256", ""))) {
            return;
        }

        LegacyRegistryAnalyzer.Analysis registry =
                new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        SourceMethods sourceMethods = inspectSourceMethods(context.sourceJar());

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("blockRegistrationStripWired", true);
        root.addProperty("runtimeCompleteRequired", true);
        root.addProperty("argumentEvaluationPreserved", true);
        root.addProperty("constructorSideEffectsPreserved", true);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int strippedRules = 0;
        int strippedSites = 0;

        for (JsonElement element : array(processor, "machines")) {
            if (!element.isJsonObject()) continue;
            JsonObject machine = element.getAsJsonObject();
            if (!bool(machine, "runtimeComplete", false)
                    || !bool(machine, "baseRuntimeComplete", false)
                    || !bool(machine, "sourcePresentationComplete", false)) {
                continue;
            }

            String id = string(machine, "id", null);
            String sourceBlockClass = string(machine, "sourceBlockClass", null);
            if (id == null || sourceBlockClass == null) continue;

            List<LegacyRegistryAnalyzer.Registration> matches =
                    registry.blocks().stream()
                            .filter(registration ->
                                    sourceBlockClass.equals(
                                            registration.implementationClass()))
                            .toList();

            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", sourceBlockClass);
            JsonArray blockers = new JsonArray();

            int sites = 0;
            String registryName = null;
            String sourceOwner = null;
            String sourceMethod = null;
            String sourceDescriptor = null;

            if (matches.size() != 1) {
                blockers.add(matches.isEmpty()
                        ? "exact-block-registration-proof-missing"
                        : "ambiguous-block-registration-proof:" + matches.size());
            } else {
                LegacyRegistryAnalyzer.Registration registration = matches.getFirst();
                registryName = registration.registryName();
                sourceOwner = registration.sourceOwner();
                sourceMethod = registration.sourceMethod();
                sourceDescriptor = registration.sourceDescriptor();

                long sameDirectSource = registry.blocks().stream()
                        .filter(candidate ->
                                sourceOwner.equals(candidate.sourceOwner())
                                        && sourceMethod.equals(candidate.sourceMethod())
                                        && sourceDescriptor.equals(candidate.sourceDescriptor()))
                        .count();
                if (sameDirectSource != 1) {
                    blockers.add("shared-registerBlock-direct-source:" + sameDirectSource);
                }

                String sourceSafety = sourceMethods.safety(
                        sourceOwner, sourceMethod, sourceDescriptor);
                if (sourceSafety != null) blockers.add(sourceSafety);

                Path classPath = context.stagingDir().resolve(sourceOwner + ".class");
                if (!Files.isRegularFile(classPath)) {
                    blockers.add("block-registration-source-class-missing");
                }

                if (blockers.isEmpty()) {
                    var target = new LegacyBlockRegistrationCallStripper.Target(
                            sourceMethod, sourceDescriptor);
                    var result = new LegacyBlockRegistrationCallStripper().strip(
                            Files.readAllBytes(classPath), target);
                    sites = result.strippedSites();
                    for (String blocker : result.blockers()) blockers.add(blocker);
                    if (sites == 1 && blockers.isEmpty()) {
                        Files.write(classPath, result.bytes());
                    }
                }
            }

            if (registryName != null) value.addProperty("legacyRegistryName", registryName);
            if (sourceOwner != null) value.addProperty("sourceOwner", sourceOwner);
            if (sourceMethod != null) value.addProperty("sourceMethod", sourceMethod);
            if (sourceDescriptor != null) {
                value.addProperty("sourceDescriptor", sourceDescriptor);
            }
            value.addProperty("blockRegistrationStripComplete",
                    sites == 1 && blockers.isEmpty());
            value.addProperty("strippedBlockRegistrationSites", sites);
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
        root.addProperty("blockRegistrationStripCompleteRules", strippedRules);
        root.addProperty("strippedBlockRegistrationSites", strippedSites);
        root.addProperty("blockedBlockRegistrationStripRules",
                rules.size() - strippedRules);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (strippedRules > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-BLOCKSTRIP-0001",
                    SupportLevel.ADAPTED,
                    "Neutralized " + strippedSites
                            + " uniquely proven legacy registerBlock callsite(s) for "
                            + strippedRules
                            + " runtime-complete processor rule(s) while preserving "
                            + "block construction and argument evaluation.");
        }
        if (strippedRules < rules.size()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-BLOCKSTRIP-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + (rules.size() - strippedRules)
                            + " processor block registration(s) because the direct source was "
                            + "shared, externally callable, ambiguous, used a return value, or "
                            + "otherwise was not safe to neutralize independently.");
        }
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
            if (info == null) return "registerBlock-direct-source-method-missing";
            if ((info.access() & Opcodes.ACC_PRIVATE) == 0) {
                return "registerBlock-helper-not-private";
            }

            int calls = callCounts.getOrDefault(key(owner, name, descriptor), 0);
            if (calls != 1) {
                return "registerBlock-private-helper-call-count:" + calls;
            }
            return null;
        }
    }

    private static String key(String owner, String name, String descriptor) {
        return owner + "\u0000" + name + "\u0000" + descriptor;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
