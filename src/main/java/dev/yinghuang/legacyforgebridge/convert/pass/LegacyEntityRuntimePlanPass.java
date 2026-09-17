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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Joins the proven legacy entity registration/DataWatcher schema with the proven access surface
 * into a modern synchronized-data mapping plan. This pass is deliberately proof-only: it does not
 * register EntityTypes or define SynchedEntityData accessors yet.
 */
public final class LegacyEntityRuntimePlanPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-runtime-plan.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Mapping(String modernValueKind, String serializer, String adapter) { }

    @Override public String id() { return "legacy-entity-runtime-plan"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path definitionPath = context.stagingDir().resolve(LegacyEntityDataWatcherPass.OUTPUT);
        Path accessPath = context.stagingDir().resolve(LegacyEntityDataWatcherAccessPass.OUTPUT);
        if (!Files.isRegularFile(definitionPath) || !Files.isRegularFile(accessPath)) return;

        JsonObject definitions = read(definitionPath);
        JsonObject accesses = read(accessPath);
        if (intValue(definitions, "schemaVersion", -1) != 1 || intValue(accesses, "schemaVersion", -1) != 1) return;
        if (!context.sourceHash().equals(stringValue(definitions, "sourceSha256", ""))
                || !context.sourceHash().equals(stringValue(accesses, "sourceSha256", ""))) {
            context.diagnostics().warning("LFB-CONVERT-ENTITY-PLAN-0003", SupportLevel.MANUAL_REQUIRED,
                    "Entity runtime plan input sidecars do not match the active source hash; runtime planning remains closed.");
            return;
        }

        boolean sourceWideClosure = sourceWideClosure(context);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceWideDataWatcherCallClosureComplete", sourceWideClosure);
        root.addProperty("postInitWatcherMutationGateWired", true);
        root.addProperty("runtimeAdmissionReady", false);
        root.addProperty("runtimeImplementationWired", false);

        Map<String,JsonObject> accessByKey = new LinkedHashMap<>();
        Set<String> duplicateAccessKeys = new HashSet<>();
        JsonArray accessRules = array(accesses, "rules");
        for (JsonElement element : accessRules) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String key = key(stringValue(rule, "legacyRegistryName", null), stringValue(rule, "sourceClass", null));
            if (key == null) continue;
            if (accessByKey.putIfAbsent(key, rule) != null) duplicateAccessKeys.add(key);
        }

        JsonArray rules = new JsonArray();
        JsonArray skipped = new JsonArray();
        JsonArray definitionRules = array(definitions, "rules");
        for (JsonElement element : definitionRules) {
            if (!element.isJsonObject()) continue;
            JsonObject definition = element.getAsJsonObject();
            String registryName = stringValue(definition, "legacyRegistryName", null);
            String sourceClass = stringValue(definition, "sourceClass", null);
            String ruleKey = key(registryName, sourceClass);
            if (ruleKey == null) {
                skip(skipped, registryName, sourceClass, "Definition rule is missing registry/source identity.");
                continue;
            }
            if (duplicateAccessKeys.contains(ruleKey)) {
                skip(skipped, registryName, sourceClass, "Duplicate DataWatcher access proof rules exist for this entity.");
                continue;
            }
            JsonObject accessRule = accessByKey.get(ruleKey);
            if (accessRule == null) {
                skip(skipped, registryName, sourceClass, "No proven DataWatcher access surface exists for this entity.");
                continue;
            }
            if (!booleanValue(accessRule, "sourceLineageAccessSurfaceComplete", false)
                    || !booleanValue(accessRule, "reachableStaticHelperClosureComplete", false)
                    || !booleanValue(accessRule, "reachableExactDispatchHelperClosureComplete", false)) {
                skip(skipped, registryName, sourceClass, "Bounded DataWatcher lineage/exact-dispatch access proof is incomplete.");
                continue;
            }

            JsonArray definitionEntries = array(definition, "dataWatcherEntries");
            LinkedHashMap<Integer,JsonObject> entryByIndex = new LinkedHashMap<>();
            String validationError = null;
            for (JsonElement entryElement : definitionEntries) {
                if (!entryElement.isJsonObject()) {
                    validationError = "DataWatcher definition entry is not an object.";
                    break;
                }
                JsonObject entry = entryElement.getAsJsonObject();
                if (!entry.has("index") || !entry.has("valueKind")) {
                    validationError = "DataWatcher definition entry is missing index/valueKind.";
                    break;
                }
                int index = entry.get("index").getAsInt();
                if (entryByIndex.putIfAbsent(index, entry) != null) {
                    validationError = "Duplicate DataWatcher definition index " + index + ".";
                    break;
                }
                if (mapping(entry.get("valueKind").getAsString()) == null) {
                    validationError = "No modern synchronized-data mapping exists for source watcher kind "
                            + entry.get("valueKind").getAsString() + " at index " + index + ".";
                    break;
                }
            }
            if (validationError != null) {
                skip(skipped, registryName, sourceClass, validationError);
                continue;
            }

            Map<Integer,int[]> counts = new HashMap<>();
            for (JsonElement accessElement : array(accessRule, "accesses")) {
                if (!accessElement.isJsonObject()) {
                    validationError = "DataWatcher access entry is not an object.";
                    break;
                }
                JsonObject access = accessElement.getAsJsonObject();
                if (!access.has("index") || !access.has("valueKind") || !access.has("operation")) {
                    validationError = "DataWatcher access entry is missing index/valueKind/operation.";
                    break;
                }
                int index = access.get("index").getAsInt();
                JsonObject entry = entryByIndex.get(index);
                if (entry == null) {
                    validationError = "Access references source-unowned DataWatcher index " + index + ".";
                    break;
                }
                String definedKind = entry.get("valueKind").getAsString();
                String accessKind = access.get("valueKind").getAsString();
                if (!definedKind.equals(accessKind)) {
                    validationError = "Access type mismatch at DataWatcher index " + index
                            + ": defined=" + definedKind + ", access=" + accessKind + ".";
                    break;
                }
                int[] value = counts.computeIfAbsent(index, ignored -> new int[2]);
                String operation = access.get("operation").getAsString();
                if ("read".equals(operation)) value[0]++;
                else if ("write".equals(operation)) value[1]++;
                else {
                    validationError = "Unsupported DataWatcher access operation " + operation + " at index " + index + ".";
                    break;
                }
            }
            if (validationError != null) {
                skip(skipped, registryName, sourceClass, validationError);
                continue;
            }

            int sourceOwnedReadCount = 0;
            int sourceOwnedWriteCount = 0;
            for (int[] count : counts.values()) {
                sourceOwnedReadCount += count[0];
                sourceOwnedWriteCount += count[1];
            }
            boolean postInitMutationFree = sourceOwnedWriteCount == 0;
            boolean generalHelperClosure = booleanValue(accessRule, "reachableHelperClosureComplete", false);
            JsonObject plan = new JsonObject();
            copyPrimitive(definition, plan, "id");
            copyPrimitive(definition, plan, "legacyRegistryName");
            copyPrimitive(definition, plan, "sourceClass");
            copyPrimitive(definition, plan, "legacyNumericId");
            copyPrimitive(definition, plan, "trackingRange");
            copyPrimitive(definition, plan, "updateFrequency");
            copyPrimitive(definition, plan, "velocityUpdates");
            plan.addProperty("sourceDataWatcherDefinitionComplete", true);
            plan.addProperty("sourceLineageAccessSurfaceComplete", true);
            plan.addProperty("reachableStaticHelperClosureComplete", true);
            plan.addProperty("reachableExactDispatchHelperClosureComplete", true);
            plan.addProperty("reachableHelperClosureComplete", generalHelperClosure);
            plan.addProperty("sourceWideDataWatcherCallClosureComplete", sourceWideClosure);
            plan.addProperty("synchedDataMappingComplete", true);
            plan.addProperty("sourceOwnedDataWatcherReadCount", sourceOwnedReadCount);
            plan.addProperty("sourceOwnedDataWatcherWriteCount", sourceOwnedWriteCount);
            plan.addProperty("postInitSourceDataWatcherMutationFree", postInitMutationFree);
            plan.addProperty("runtimeAdmissionReady", false);
            plan.addProperty("runtimeImplementationWired", false);

            JsonArray blockers = new JsonArray();
            if (!sourceWideClosure) blockers.add("source-wide-datawatcher-call-closure-incomplete");
            if (!postInitMutationFree) blockers.add("post-init-datawatcher-writes-require-runtime-sync");
            blockers.add("entitytype-syncheddata-runtime-not-materialized");
            plan.add("runtimeBlockers", blockers);

            JsonArray mappedEntries = new JsonArray();
            for (Map.Entry<Integer,JsonObject> sourceEntry : entryByIndex.entrySet()) {
                int index = sourceEntry.getKey();
                JsonObject entry = sourceEntry.getValue();
                String sourceKind = entry.get("valueKind").getAsString();
                Mapping mapping = mapping(sourceKind);
                JsonObject mapped = new JsonObject();
                mapped.addProperty("sourceIndex", index);
                mapped.addProperty("sourceKind", sourceKind);
                mapped.addProperty("modernValueKind", mapping.modernValueKind());
                mapped.addProperty("serializer", mapping.serializer());
                mapped.addProperty("adapter", mapping.adapter());
                if (entry.has("defaultValue")) mapped.add("defaultValue", entry.get("defaultValue").deepCopy());
                copyPrimitive(entry, mapped, "declaredBy");
                int[] count = counts.getOrDefault(index, new int[2]);
                mapped.addProperty("readCount", count[0]);
                mapped.addProperty("writeCount", count[1]);
                mappedEntries.add(mapped);
            }
            plan.add("synchedDataEntries", mappedEntries);
            plan.addProperty("synchedDataEntryCount", mappedEntries.size());
            rules.add(plan);
        }

        root.add("rules", rules);
        root.add("skipped", skipped);
        root.addProperty("mappedRegistrations", rules.size());
        root.addProperty("skippedRegistrations", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (JsonElement element : skipped) {
            JsonObject item = element.getAsJsonObject();
            context.diagnostics().warning("LFB-CONVERT-ENTITY-PLAN-0002", SupportLevel.RUNTIME_BRIDGE,
                    "Entity synchronized-data runtime plan remains closed for "
                            + stringValue(item, "legacyRegistryName", "<unknown>") + ": "
                            + stringValue(item, "reason", "unknown reason"));
        }
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-PLAN-0001", SupportLevel.RUNTIME_BRIDGE,
                "Mapped legacy primitive/string DataWatcher schemas to modern synchronized-data plan IR for "
                        + rules.size() + " entity registration(s); source-wide watcher closure=" + sourceWideClosure
                        + "; post-init source watcher writes remain admission-gated until a dedicated metadata synchronization runtime exists.");
    }

    private static boolean sourceWideClosure(ConversionContext context) throws Exception {
        Path path = context.stagingDir().resolve(LegacyEntityDataWatcherGlobalClosurePass.OUTPUT);
        if (!Files.isRegularFile(path)) return false;
        JsonObject root = read(path);
        if (intValue(root, "schemaVersion", -1) != 1) return false;
        if (!context.sourceHash().equals(stringValue(root, "sourceSha256", ""))) return false;
        return booleanValue(root, "sourceWideDataWatcherCallClosureComplete", false);
    }

    private static Mapping mapping(String sourceKind) {
        return switch (sourceKind) {
            case "byte" -> new Mapping("byte", "BYTE", "identity");
            case "short" -> new Mapping("int", "INT", "signed_short_widen");
            case "int" -> new Mapping("int", "INT", "identity");
            case "float" -> new Mapping("float", "FLOAT", "identity");
            case "string" -> new Mapping("string", "STRING", "identity");
            default -> null;
        };
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String key(String registryName, String sourceClass) {
        if (registryName == null || sourceClass == null) return null;
        return registryName + '\u0000' + sourceClass;
    }

    private static void copyPrimitive(JsonObject source, JsonObject target, String key) {
        JsonElement value = source.get(key);
        if (value != null && value.isJsonPrimitive()) target.add(key, value.deepCopy());
    }

    private static void skip(JsonArray skipped, String registryName, String sourceClass, String reason) {
        JsonObject item = new JsonObject();
        if (registryName != null) item.addProperty("legacyRegistryName", registryName);
        if (sourceClass != null) item.addProperty("sourceClass", sourceClass);
        item.addProperty("reason", reason);
        skipped.add(item);
    }
}
