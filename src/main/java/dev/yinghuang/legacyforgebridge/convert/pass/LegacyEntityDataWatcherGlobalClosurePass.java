package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityDataWatcherGlobalClosureAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Materializes a source-wide direct DataWatcher call closure gate before entity runtime generation. */
public final class LegacyEntityDataWatcherGlobalClosurePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-datawatcher-global-closure.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-datawatcher-global-closure"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        if (!Files.isRegularFile(context.stagingDir().resolve(LegacyEntityDataWatcherAccessPass.OUTPUT))) return;

        LegacyEntityDataWatcherGlobalClosureAnalyzer.Analysis analysis =
                new LegacyEntityDataWatcherGlobalClosureAnalyzer().analyze(context.sourceJar());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceWideDataWatcherCallClosureComplete", analysis.sourceWideClosureComplete());
        root.addProperty("runtimeImplementationWired", false);
        root.addProperty("runtimeDataWatcherCallCount", analysis.runtimeCallCount());
        root.addProperty("provenRuntimeDataWatcherCallCount", analysis.provenRuntimeCallCount());

        JsonArray accounted = new JsonArray();
        for (LegacyEntityDataWatcherGlobalClosureAnalyzer.MethodSurface method : analysis.accounted())
            accounted.add(method(method));
        root.add("accountedMethods", accounted);

        JsonArray unresolved = new JsonArray();
        for (LegacyEntityDataWatcherGlobalClosureAnalyzer.MethodSurface method : analysis.unresolved()) {
            JsonObject value = method(method);
            JsonArray unsupported = new JsonArray();
            method.unsupportedCalls().forEach(unsupported::add);
            value.add("unsupportedCalls", unsupported);
            if (!method.unsupportedCalls().isEmpty())
                value.addProperty("reason", "Source method contains DataWatcher calls outside the currently mapped primitive/string read/update surface.");
            else value.addProperty("reason", "Bounded entity access proof accounts for " + method.provenAccessCount()
                    + " of " + method.runtimeCallCount() + " source-wide runtime DataWatcher call(s) in this method.");
            unresolved.add(value);
        }
        root.add("unresolvedMethods", unresolved);
        root.addProperty("accountedMethodCount", accounted.size());
        root.addProperty("unresolvedMethodCount", unresolved.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-CLOSURE-0003", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (analysis.sourceWideClosureComplete()) {
            context.diagnostics().info("LFB-CONVERT-ENTITY-CLOSURE-0001", SupportLevel.RUNTIME_BRIDGE,
                    "Every direct runtime DataWatcher call in the source JAR is accounted for by bounded entity access proof; entity runtime materialization remains a separate gate.");
        } else {
            context.diagnostics().warning("LFB-CONVERT-ENTITY-CLOSURE-0002", SupportLevel.RUNTIME_BRIDGE,
                    "Source-wide DataWatcher call closure remains incomplete: " + analysis.unresolved().size()
                            + " method(s) contain unaccounted or unsupported runtime watcher calls.");
        }
    }

    private static JsonObject method(LegacyEntityDataWatcherGlobalClosureAnalyzer.MethodSurface method) {
        JsonObject value = new JsonObject();
        value.addProperty("sourceOwner", method.sourceOwner());
        value.addProperty("sourceMethod", method.sourceMethod());
        value.addProperty("sourceDescriptor", method.sourceDescriptor());
        value.addProperty("runtimeCallCount", method.runtimeCallCount());
        value.addProperty("provenAccessCount", method.provenAccessCount());
        return value;
    }
}
