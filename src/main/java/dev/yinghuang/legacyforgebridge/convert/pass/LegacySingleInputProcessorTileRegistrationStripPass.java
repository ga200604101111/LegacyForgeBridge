package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyLifecycleAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyTileEntityRegistrationStripper;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Strips only exact legacy registerTileEntity callsites for runtime-complete converted processors.
 */
public final class LegacySingleInputProcessorTileRegistrationStripPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-tile-registration-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record RuntimeRule(
            String id,
            String sourceTileClass,
            String legacyTileId) { }

    @Override
    public String id() {
        return "legacy-single-input-processor-tile-registration-strip";
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

        java.util.ArrayList<RuntimeRule> runtimeRules = new java.util.ArrayList<>();
        for (JsonElement element : array(processor, "machines")) {
            if (!element.isJsonObject()) continue;
            JsonObject machine = element.getAsJsonObject();
            if (!bool(machine, "runtimeComplete", false)
                    || !bool(machine, "baseRuntimeComplete", false)
                    || !bool(machine, "sourcePresentationComplete", false)) {
                continue;
            }
            String id = string(machine, "id", null);
            String sourceTileClass = string(machine, "sourceTileClass", null);
            String legacyTileId = string(machine, "legacyTileId", null);
            if (id == null || sourceTileClass == null || legacyTileId == null
                    || legacyTileId.isBlank()) {
                continue;
            }
            runtimeRules.add(new RuntimeRule(id, sourceTileClass, legacyTileId));
        }
        if (runtimeRules.isEmpty()) return;

        LegacyLifecycleAnalyzer.Analysis lifecycle =
                new LegacyLifecycleAnalyzer().analyze(context.sourceJar());
        LegacyTileEntityRegistrationStripper stripper =
                new LegacyTileEntityRegistrationStripper();

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("tileRegistrationStripWired", true);
        root.addProperty("runtimeCompleteRequired", true);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int strippedRules = 0;
        int strippedSites = 0;

        for (RuntimeRule runtimeRule : runtimeRules) {
            List<LegacyLifecycleAnalyzer.Registration> matches =
                    lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY).stream()
                            .filter(registration -> matches(registration, runtimeRule))
                            .toList();

            JsonObject value = new JsonObject();
            value.addProperty("id", runtimeRule.id());
            value.addProperty("sourceTileClass", runtimeRule.sourceTileClass());
            value.addProperty("legacyTileId", runtimeRule.legacyTileId());
            JsonArray blockers = new JsonArray();

            int sites = 0;
            String sourceOwner = null;
            String sourceMethod = null;
            String sourceDescriptor = null;

            if (matches.size() != 1) {
                blockers.add(matches.isEmpty()
                        ? "exact-tile-lifecycle-registration-proof-missing"
                        : "ambiguous-tile-lifecycle-registration-proof:" + matches.size());
            } else {
                LegacyLifecycleAnalyzer.Registration registration = matches.getFirst();
                sourceOwner = registration.sourceOwner();
                sourceMethod = registration.sourceMethod();
                sourceDescriptor = registration.sourceDescriptor();

                Path classPath = context.stagingDir().resolve(sourceOwner + ".class");
                if (!Files.isRegularFile(classPath)) {
                    blockers.add("tile-registration-source-class-missing");
                } else {
                    var target = new LegacyTileEntityRegistrationStripper.Target(
                            sourceMethod,
                            sourceDescriptor,
                            runtimeRule.sourceTileClass(),
                            runtimeRule.legacyTileId());
                    var result = stripper.strip(Files.readAllBytes(classPath), target);
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
            value.addProperty("tileRegistrationStripComplete",
                    sites == 1 && blockers.isEmpty());
            value.addProperty("strippedTileRegistrationSites", sites);
            value.add("blockers", blockers);
            rules.add(value);

            if (sites == 1 && blockers.isEmpty()) {
                strippedRules++;
                strippedSites += sites;
            }
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", runtimeRules.size());
        root.addProperty("tileRegistrationStripCompleteRules", strippedRules);
        root.addProperty("strippedTileRegistrationSites", strippedSites);
        root.addProperty("blockedTileRegistrationStripRules",
                runtimeRules.size() - strippedRules);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (strippedRules > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-TILESTRIP-0001",
                    SupportLevel.ADAPTED,
                    "Removed " + strippedSites
                            + " exact legacy registerTileEntity callsite(s) for "
                            + strippedRules + " runtime-complete processor rule(s).");
        }
        if (strippedRules < runtimeRules.size()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-TILESTRIP-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + (runtimeRules.size() - strippedRules)
                            + " processor TileEntity registration(s) because exact lifecycle "
                            + "identity or a pure class/id registration slice was not proven.");
        }
        for (String diagnostic : lifecycle.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-TILESTRIP-0003",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic);
        }
    }

    private static boolean matches(
            LegacyLifecycleAnalyzer.Registration registration,
            RuntimeRule rule) {
        List<LegacyLifecycleAnalyzer.Value> args = registration.arguments();
        if (args.size() < 2) return false;
        if (!(args.get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type)
                || !rule.sourceTileClass().equals(type.internalName())) {
            return false;
        }
        return args.get(1) instanceof LegacyLifecycleAnalyzer.TextValue id
                && rule.legacyTileId().equals(id.value());
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
