package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyFuelHandlerAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyFuelRuleMaterializer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Emits declarative, current-identity fuel rules for the converted mod runtime. */
public final class LegacyFuelHandlerPass implements ConversionPass {
    public static final String RULES_PATH = "legacyforgebridge/fuel-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-fuel-handler"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyFuelHandlerAnalyzer.Analysis analysis = new LegacyFuelHandlerAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.diagnostics().isEmpty()) return;

        JsonArray rules = new JsonArray();
        int skipped = 0;
        for (LegacyFuelHandlerAnalyzer.FuelRule source : analysis.rules()) {
            var modern = LegacyFuelRuleMaterializer.materialize(source, context);
            if (modern.isEmpty()) {
                skipped++;
                context.diagnostics().warning(
                        "LFB-CONVERT-FUEL-0003", SupportLevel.MANUAL_REQUIRED,
                        "Fuel rule could not be represented in the current item/component identity space without guessing: "
                                + source.sourceOwner() + "." + source.sourceMethod()
                                + " item=" + source.registry().registryName()
                                + " meta=" + (source.metadata() == null ? "*" : source.metadata()) + ".");
                continue;
            }
            JsonObject rule = new JsonObject();
            rule.addProperty("id", modern.get().id());
            rule.addProperty("legacyMeta", modern.get().legacyMeta());
            rule.addProperty("damage", modern.get().damage());
            rule.addProperty("burnTicks", modern.get().burnTicks());
            rule.addProperty("sourceOwner", source.sourceOwner());
            rule.addProperty("sourceMethod", source.sourceMethod());
            rules.add(rule);
        }
        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning("LFB-CONVERT-FUEL-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("rules", rules);
        root.addProperty("skippedRules", skipped);
        Path output = context.stagingDir().resolve(RULES_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!rules.isEmpty()) {
            context.diagnostics().info(
                    "LFB-CONVERT-FUEL-0001", SupportLevel.RUNTIME_BRIDGE,
                    "Materialized " + rules.size() + " source-proven Forge 1.7 fuel rules; skipped=" + skipped + ".");
        }
    }
}
