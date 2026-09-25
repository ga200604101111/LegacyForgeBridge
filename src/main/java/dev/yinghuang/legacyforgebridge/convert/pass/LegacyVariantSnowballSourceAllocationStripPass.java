package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemAllocationResidueStripper;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Removes only the exact inline source item allocation whose constructor replacement was already
 * proven by generated behavior code and whose registerItem call was already neutralized.
 */
public final class LegacyVariantSnowballSourceAllocationStripPass implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/variant-snowball-source-allocation-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() {
        return "legacy-variant-snowball-source-allocation-strip";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path constructionPath = context.stagingDir().resolve(
                LegacyVariantSnowballConstructionReplacementReadiness.OUTPUT);
        Path itemStripPath = context.stagingDir().resolve(
                LegacyVariantSnowballItemRegistrationStripPass.OUTPUT);
        if (!Files.isRegularFile(constructionPath)
                || !Files.isRegularFile(itemStripPath)) return;

        JsonObject construction = read(constructionPath);
        JsonObject itemStrip = read(itemStripPath);
        if (integer(construction, "schemaVersion", -1) != 1
                || integer(itemStrip, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(construction, "sourceSha256", ""))
                || !context.sourceHash().equals(string(itemStrip, "sourceSha256", ""))) {
            return;
        }

        Map<String,JsonObject> stripByItem = new LinkedHashMap<>();
        for (JsonElement element : array(itemStrip, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String item = string(value, "sourceItemClass", null);
            if (item != null) stripByItem.putIfAbsent(item, value);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceAllocationStripWired", true);
        root.addProperty("constructorReplacementRequired", true);
        root.addProperty("freshPostStripReferenceCheckWired", true);
        root.addProperty("sourceClassDeletionWired", false);
        JsonArray rules = new JsonArray();
        int complete = 0, blocked = 0;

        for (JsonElement element : array(construction, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject proof = element.getAsJsonObject();
            String id = string(proof, "id", null);
            String sourceItemClass = string(proof, "sourceItemClass", null);
            String sourceConstructor = string(proof, "sourceConstructor", null);
            if (id == null || sourceItemClass == null) continue;

            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceItemClass", sourceItemClass);
            if (sourceConstructor != null) value.addProperty("sourceConstructor", sourceConstructor);
            JsonArray blockers = new JsonArray();
            int strippedSites = 0;

            if (!bool(proof, "constructorReplacementProven", false)) {
                blockers.add("constructor-replacement-not-proven");
            }

            JsonObject registration = stripByItem.get(sourceItemClass);
            if (registration == null
                    || !bool(registration, "itemRegistrationStripComplete", false)
                    || integer(registration, "strippedItemRegistrationSites", 0) != 1) {
                blockers.add("item-registration-strip-incomplete");
            }

            String sourceOwner = registration == null
                    ? null : string(registration, "sourceOwner", null);
            String sourceMethod = registration == null
                    ? null : string(registration, "sourceMethod", null);
            String sourceDescriptor = registration == null
                    ? null : string(registration, "sourceDescriptor", null);
            String registryName = registration == null
                    ? null : string(registration, "legacyRegistryName", null);
            if (sourceOwner == null || sourceMethod == null
                    || sourceDescriptor == null || registryName == null) {
                blockers.add("source-allocation-owner-identity-incomplete");
            }
            if (sourceConstructor == null) {
                blockers.add("source-constructor-descriptor-missing");
            }

            Path classPath = sourceOwner == null ? null
                    : context.stagingDir().resolve(sourceOwner + ".class");
            if (classPath == null || !Files.isRegularFile(classPath)) {
                blockers.add("source-allocation-owner-class-missing");
            }

            if (blockers.isEmpty()) {
                var target = new LegacyItemAllocationResidueStripper.Target(
                        sourceMethod,
                        sourceDescriptor,
                        sourceItemClass,
                        sourceConstructor,
                        registryName);
                var result = new LegacyItemAllocationResidueStripper().strip(
                        Files.readAllBytes(classPath), target);
                strippedSites = result.strippedSites();
                for (String blocker : result.blockers()) blockers.add(blocker);
                if (strippedSites == 1 && blockers.isEmpty()) {
                    Files.write(classPath, result.bytes());
                }
            }

            if (sourceOwner != null) value.addProperty("sourceOwner", sourceOwner);
            if (sourceMethod != null) value.addProperty("sourceMethod", sourceMethod);
            if (sourceDescriptor != null) value.addProperty("sourceDescriptor", sourceDescriptor);
            value.addProperty("constructorReplacementProven",
                    bool(proof, "constructorReplacementProven", false));
            value.addProperty("sourceAllocationStripComplete",
                    strippedSites == 1 && blockers.isEmpty());
            value.addProperty("strippedSourceAllocationSites", strippedSites);
            value.addProperty("sourceClassDeletionAuthorized", false);
            value.add("blockers", blockers);
            rules.add(value);

            if (strippedSites == 1 && blockers.isEmpty()) complete++;
            else blocked++;
        }

        root.add("rules", rules);
        root.addProperty("evaluatedConstructionRules", rules.size());
        root.addProperty("sourceAllocationStripCompleteRules", complete);
        root.addProperty("blockedSourceAllocationStripRules", blocked);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (complete > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-ALLOCATION-0001",
                    SupportLevel.ADAPTED,
                    "Removed " + complete
                            + " proof-replaced inline legacy variant-snowball item allocation(s) "
                            + "after constructor replacement and registerItem neutralization.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-ALLOCATION-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + blocked
                            + " variant-snowball source item allocation(s) because the exact "
                            + "neutralized inline residue was not safely removable.");
        }
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
}
