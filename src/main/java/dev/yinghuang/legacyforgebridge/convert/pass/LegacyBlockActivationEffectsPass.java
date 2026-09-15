package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.longyu.legacyforgebridge.convert.*;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;

/** Companion to the read-only activation pass; materializes only complete, proven effects. */
public final class LegacyBlockActivationEffectsPass {
    public static final String RULES_PATH = "legacyforgebridge/block-activation-effects.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public record Result(int writtenRules, Set<String> completedCallbacks) {
        public Result { completedCallbacks = Set.copyOf(completedCallbacks); }
    }
    private LegacyBlockActivationEffectsPass() { }

    public static Result materialize(ConversionContext context) throws Exception {
        Files.deleteIfExists(context.stagingDir().resolve(RULES_PATH));
        var behavior = new LegacyBlockBehaviorAnalyzer().analyze(context.sourceJar());
        var registry = new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(context.sourceJar().toFile())) {
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                try (var input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException exception) {
                    // Do not prove stable source fields when any source class could not be read.
                    context.diagnostics().warning("LFB-CONVERT-BLOCK-ACTIVATION-0004", SupportLevel.MANUAL_REQUIRED,
                            "Cannot prove activation effect field stability: unreadable class " + entry.getName());
                    return new Result(0, Set.of());
                }
            }
        }
        Map<LegacyBlockActivationEffectCompiler.FieldKey, String> bindings = bindings(context, registry, classes);
        JsonArray rules = new JsonArray();
        Map<String, Integer> expected = new HashMap<>();
        Map<String, Integer> completed = new HashMap<>();
        for (var block : behavior.blocks()) {
            var callback = block.callbacks().stream().filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.ACTIVATE)
                    .findFirst().orElse(null);
            if (callback == null) continue;
            String key = callback.owner() + "." + callback.method() + callback.descriptor();
            expected.merge(key, 1, Integer::sum);
            ClassNode node = classes.get(callback.owner());
            MethodNode method = node == null ? null : node.methods.stream()
                    .filter(value -> value.name.equals(callback.method()) && value.desc.equals(callback.descriptor()))
                    .findFirst().orElse(null);
            if (method == null || !hasMetadataSetter(method)) continue;
            try {
                String legacyId = legacyId(context, block.legacyNamespace(), block.registryName());
                String modernId = modern(context, "blocks", legacyId);
                if (modernId == null) throw new IllegalArgumentException("unresolved generated block identity " + legacyId);
                var plan = new LegacyBlockActivationEffectCompiler().compile(method, bindings);
                JsonObject rule = new JsonObject();
                rule.addProperty("id", modernId);
                rule.addProperty("legacyId", legacyId);
                rule.addProperty("heldItemId", plan.heldItemId());
                rule.addProperty("legacyNotifyFlags", LegacyBlockActivationEffectPlan.LEGACY_NOTIFY_FLAGS);
                JsonArray outcomes = new JsonArray();
                plan.outcomes().forEach(outcomes::add);
                rule.add("outcomes", outcomes);
                JsonObject provenance = new JsonObject();
                provenance.addProperty("owner", callback.owner());
                provenance.addProperty("method", callback.method());
                provenance.addProperty("descriptor", callback.descriptor());
                rule.add("provenance", provenance);
                rules.add(rule);
                completed.merge(key, 1, Integer::sum);
            } catch (IllegalArgumentException exception) {
                context.diagnostics().warning("LFB-CONVERT-BLOCK-ACTIVATION-0005", SupportLevel.MANUAL_REQUIRED,
                        "Unsupported activation effect " + key + ": " + exception.getMessage());
            }
        }
        if (!rules.isEmpty()) {
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", 1);
            root.add("rules", rules);
            var path = context.stagingDir().resolve(RULES_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            context.diagnostics().info("LFB-CONVERT-BLOCK-ACTIVATION-0006", SupportLevel.RUNTIME_BRIDGE,
                    "Materialized " + rules.size() + " held-item-gated activation effect rules; 1152 proven input tuples each.");
        }
        Set<String> complete = new HashSet<>();
        completed.forEach((key, count) -> { if (count.equals(expected.get(key))) complete.add(key); });
        return new Result(rules.size(), complete);
    }

    private static Map<LegacyBlockActivationEffectCompiler.FieldKey, String> bindings(ConversionContext context,
            LegacyRegistryAnalyzer.Analysis registry, Map<String, ClassNode> classes) {
        Map<LegacyBlockActivationEffectCompiler.FieldKey, Integer> writes = new HashMap<>();
        for (ClassNode node : classes.values()) for (MethodNode method : node.methods)
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC) {
                    writes.merge(new LegacyBlockActivationEffectCompiler.FieldKey(field.owner, field.name, field.desc), 1, Integer::sum);
                }
            }
        Map<LegacyBlockActivationEffectCompiler.FieldKey, Set<String>> candidates = new HashMap<>();
        for (var field : registry.fieldBindings()) {
            if (field.kind() != LegacyRegistryAnalyzer.Kind.ITEM) continue;
            String legacyId = legacyId(context, field.legacyNamespace(), field.registryName());
            String modernId = modern(context, "items", legacyId);
            if (modernId == null) continue;
            var key = new LegacyBlockActivationEffectCompiler.FieldKey(field.owner(), field.name(), field.descriptor());
            candidates.computeIfAbsent(key, ignored -> new HashSet<>()).add(modernId);
        }
        Map<LegacyBlockActivationEffectCompiler.FieldKey, String> result = new HashMap<>();
        candidates.forEach((key, identities) -> {
            if (identities.size() == 1 && writes.getOrDefault(key, 0) == 1) {
                result.put(key, identities.iterator().next());
            }
        });
        return Map.copyOf(result);
    }

    private static boolean hasMetadataSetter(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && "net/minecraft/world/World".equals(call.owner)
                    && ("func_72921_c".equals(call.name) || "setBlockMetadataWithNotify".equals(call.name))
                    && "(IIIII)Z".equals(call.desc)) return true;
        }
        return false;
    }
    private static String legacyId(ConversionContext context, String namespace, String name) {
        return (namespace == null || namespace.isBlank() ? context.metadata().primary().modId() : namespace) + ":" + name;
    }
    private static String modern(ConversionContext context, String kind, String legacyId) {
        var map = context.registryIdentities().get(kind);
        if (map == null) return null;
        String value = map.get(legacyId);
        return value != null ? value : map.get(legacyId.toLowerCase(Locale.ROOT));
    }
}
