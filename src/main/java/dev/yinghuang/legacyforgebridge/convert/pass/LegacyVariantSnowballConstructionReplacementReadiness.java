package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Proves that the source Item constructor still evaluated by the neutralized registerItem site has
 * an equivalent generated behavior constructor replacement. This is proof only: the source
 * allocation is not removed here.
 */
public final class LegacyVariantSnowballConstructionReplacementReadiness {
    public static final String OUTPUT =
            "legacyforgebridge/variant-snowball-construction-replacement-readiness.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LegacyVariantSnowballConstructionReplacementReadiness() { }

    public static void materialize(ConversionContext context) throws Exception {
        Path runtimePath = context.stagingDir().resolve(LegacyVariantSnowballRuntimePass.OUTPUT);
        Path itemStripPath = context.stagingDir()
                .resolve(LegacyVariantSnowballItemRegistrationStripPass.OUTPUT);
        Path behaviorPath = context.stagingDir().resolve("legacyforgebridge/behavior-analysis.json");
        Path contentPath = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        Path markerPath = context.stagingDir().resolve(LegacyBehaviorPass.MARKER);
        if (!Files.isRegularFile(runtimePath)
                || !Files.isRegularFile(itemStripPath)
                || !Files.isRegularFile(behaviorPath)
                || !Files.isRegularFile(contentPath)
                || !Files.isRegularFile(markerPath)) {
            return;
        }

        JsonObject runtime = read(runtimePath);
        JsonObject itemStrip = read(itemStripPath);
        JsonObject behavior = read(behaviorPath);
        JsonObject content = read(contentPath);
        if (integer(runtime, "schemaVersion", -1) != LegacyVariantSnowballRuntimePass.SCHEMA
                || integer(itemStrip, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(runtime, "sourceSha256", ""))
                || !context.sourceHash().equals(string(itemStrip, "sourceSha256", ""))) {
            return;
        }

        String bootstrap = Files.readString(markerPath, StandardCharsets.UTF_8).trim();
        boolean bootstrapValid = !bootstrap.isBlank()
                && Files.isRegularFile(context.stagingDir().resolve(bootstrap + ".class"));

        Map<String,JsonObject> contentItems = index(content, "items", "id");
        Map<String,JsonObject> itemStripRules = index(itemStrip, "rules", "id");
        Map<String,JsonObject> behaviorItems = uniqueIndex(behavior, "items", "id");

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("constructionReplacementAnalysisWired", true);
        root.addProperty("generatedBehaviorConstructorReplacementRequired", true);
        root.addProperty("sourceAllocationStripWired", false);
        root.addProperty("sourceClassDeletionWired", false);
        root.addProperty("behaviorBootstrapPresent", bootstrapValid);

        JsonArray rules = new JsonArray();
        int proven = 0, blocked = 0;

        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject runtimeRule = element.getAsJsonObject();
            if (!completeRuntime(runtimeRule)) continue;

            String id = string(runtimeRule, "id", null);
            String sourceClass = string(runtimeRule, "sourceItemClass", null);
            if (id == null || sourceClass == null) continue;

            LinkedHashSet<String> blockers = new LinkedHashSet<>();
            JsonObject contentItem = contentItems.get(id);
            JsonObject stripRule = itemStripRules.get(id);
            JsonObject behaviorItem = behaviorItems.get(id);

            if (!bootstrapValid) blockers.add("generated-behavior-bootstrap-missing");
            if (contentItem == null) blockers.add("converted-content-item-missing");
            if (stripRule == null
                    || !bool(stripRule, "itemRegistrationStripComplete", false)
                    || integer(stripRule, "strippedItemRegistrationSites", 0) != 1) {
                blockers.add("item-registration-strip-incomplete");
            }

            String constructorDescriptor = null;
            JsonArray sourceArgs = null;
            if (contentItem != null) {
                if (!sourceClass.equals(string(contentItem, "sourceClass", null))) {
                    blockers.add("converted-content-source-class-mismatch");
                }
                constructorDescriptor = string(contentItem, "sourceConstructor", null);
                sourceArgs = contentItem.getAsJsonArray("sourceConstructorArgs");
                if (constructorDescriptor == null) {
                    blockers.add("converted-content-constructor-descriptor-missing");
                }
            }

            JsonObject allocation = behaviorItem == null ? null
                    : object(behaviorItem, "allocation");
            if (behaviorItem == null) {
                blockers.add("generated-behavior-item-binding-missing");
            } else {
                if (!sourceClass.equals(string(behaviorItem, "sourceClass", null))) {
                    blockers.add("generated-behavior-source-class-mismatch");
                }
                if (allocation == null) {
                    blockers.add("generated-behavior-allocation-missing");
                }
            }

            if (allocation != null) {
                if (!sourceClass.equals(string(allocation, "itemClass", null))) {
                    blockers.add("generated-behavior-allocation-class-mismatch");
                }
                String behaviorDescriptor = string(allocation, "constructorDescriptor", null);
                if (constructorDescriptor == null
                        || !constructorDescriptor.equals(behaviorDescriptor)) {
                    blockers.add("generated-behavior-constructor-descriptor-mismatch");
                } else if (!argumentsMatch(
                        constructorDescriptor,
                        sourceArgs,
                        allocation.getAsJsonArray("arguments"))) {
                    blockers.add("generated-behavior-constructor-arguments-mismatch");
                }
            }

            String generatedClass = bootstrap + "Source/" + sourceClass + ".class";
            boolean generatedClassPresent = bootstrapValid
                    && Files.isRegularFile(context.stagingDir().resolve(generatedClass));
            if (!generatedClassPresent) {
                blockers.add("generated-behavior-source-class-missing");
            }

            boolean replacement = blockers.isEmpty();
            if (replacement) proven++; else blocked++;

            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceItemClass", sourceClass);
            if (constructorDescriptor != null) {
                value.addProperty("sourceConstructor", constructorDescriptor);
            }
            value.addProperty("generatedBehaviorBootstrap", bootstrap);
            value.addProperty("generatedBehaviorClass", generatedClass);
            value.addProperty("generatedBehaviorClassPresent", generatedClassPresent);
            value.addProperty("itemRegistrationStripComplete",
                    stripRule != null
                            && bool(stripRule, "itemRegistrationStripComplete", false));
            value.addProperty("constructorReplacementProven", replacement);
            value.addProperty("sourceAllocationRetirementRequired", true);
            value.addProperty("sourceAllocationStripWired", false);
            value.addProperty("sourceClassDeletionAuthorized", false);
            value.add("blockers", strings(blockers));
            rules.add(value);
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty("constructorReplacementProvenRules", proven);
        root.addProperty("blockedConstructorReplacementRules", blocked);
        root.addProperty("sourceAllocationStripCompleteRules", 0);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (proven > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-CONSTRUCTION-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Proved generated behavior constructor replacement for " + proven
                            + " variant-snowball source item allocation(s); source allocation "
                            + "removal remains intentionally unwired.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-CONSTRUCTION-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Constructor replacement remains unproven for " + blocked
                            + " variant-snowball runtime rule(s); source allocation retirement "
                            + "stays fail-closed.");
        }
    }

    private static boolean argumentsMatch(
            String descriptor,
            JsonArray sourceArgs,
            JsonArray behaviorArgs) {
        Type[] types;
        try {
            types = Type.getArgumentTypes(descriptor);
        } catch (RuntimeException invalid) {
            return false;
        }
        int sourceSize = sourceArgs == null ? 0 : sourceArgs.size();
        int behaviorSize = behaviorArgs == null ? 0 : behaviorArgs.size();
        if (types.length != sourceSize || types.length != behaviorSize) return false;

        for (int index = 0; index < types.length; index++) {
            JsonElement expected = sourceArgs.get(index);
            JsonElement raw = behaviorArgs.get(index);
            if (!raw.isJsonObject()) return false;
            JsonObject argument = raw.getAsJsonObject();
            if (!types[index].getDescriptor().equals(string(argument, "descriptor", null))) {
                return false;
            }
            JsonElement actual = argument.get("value");
            if (actual == null) actual = JsonNull.INSTANCE;
            if (!sameScalar(expected, actual)) return false;
        }
        return true;
    }

    private static boolean sameScalar(JsonElement left, JsonElement right) {
        if (left == null) left = JsonNull.INSTANCE;
        if (right == null) right = JsonNull.INSTANCE;
        if (left.isJsonNull() || right.isJsonNull()) {
            return left.isJsonNull() && right.isJsonNull();
        }
        if (!left.isJsonPrimitive() || !right.isJsonPrimitive()) return false;
        var a = left.getAsJsonPrimitive();
        var b = right.getAsJsonPrimitive();
        if (a.isNumber() && b.isNumber()) {
            return Double.compare(a.getAsDouble(), b.getAsDouble()) == 0;
        }
        return a.equals(b);
    }

    private static boolean completeRuntime(JsonObject rule) {
        return bool(rule, "runtimeRuleReady", false)
                && bool(rule, "itemRuntimeWired", false)
                && bool(rule, "projectileRuntimeWired", false)
                && bool(rule, "projectileImpactRuntimeWired", false)
                && bool(rule, "rendererRuntimeWired", false)
                && bool(rule, "runtimeImplementationWired", false);
    }

    private static Map<String,JsonObject> index(
            JsonObject root, String arrayName, String keyName) {
        Map<String,JsonObject> output = new LinkedHashMap<>();
        for (JsonElement element : array(root, arrayName)) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String key = string(value, keyName, null);
            if (key != null) output.putIfAbsent(key, value);
        }
        return output;
    }

    private static Map<String,JsonObject> uniqueIndex(
            JsonObject root, String arrayName, String keyName) {
        Map<String,JsonObject> output = new LinkedHashMap<>();
        Set<String> duplicates = new java.util.HashSet<>();
        for (JsonElement element : array(root, arrayName)) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String key = string(value, keyName, null);
            if (key == null) continue;
            if (output.putIfAbsent(key, value) != null) duplicates.add(key);
        }
        for (String duplicate : duplicates) output.remove(duplicate);
        return output;
    }

    private static JsonObject object(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
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

    private static JsonArray strings(Iterable<String> values) {
        JsonArray output = new JsonArray();
        for (String value : values) output.add(value);
        return output;
    }
}
