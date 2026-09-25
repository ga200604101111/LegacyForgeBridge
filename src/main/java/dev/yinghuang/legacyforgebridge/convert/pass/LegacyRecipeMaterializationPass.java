package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyOreDictionaryIndex;
import dev.yinghuang.legacyforgebridge.convert.LegacyRecipeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRecipeJsonMaterializer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Emits current data-pack recipes only when every source ingredient/result is proven. */
public final class LegacyRecipeMaterializationPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-recipe-materialization";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyRecipeAnalyzer.Analysis analysis = new LegacyRecipeAnalyzer().analyze(context.sourceJar());
        materialize(context, analysis);
    }

    static Summary materialize(ConversionContext context, LegacyRecipeAnalyzer.Analysis analysis) throws Exception {
        LegacyOreDictionaryIndex oreDictionary = LegacyOreDictionaryIndex.build(analysis, context);
        int emitted = 0;
        int skipped = 0;
        int recipeOrdinal = 0;
        JsonArray skippedRegistrations = new JsonArray();

        for (LegacyRecipeAnalyzer.Registration registration : analysis.registrations()) {
            if (registration.kind() != LegacyRecipeAnalyzer.Kind.SHAPED
                    && registration.kind() != LegacyRecipeAnalyzer.Kind.SHAPELESS
                    && registration.kind() != LegacyRecipeAnalyzer.Kind.SMELTING) {
                continue;
            }
            int ordinal = recipeOrdinal++;
            var modern = LegacyRecipeJsonMaterializer.materialize(registration, context, oreDictionary);
            if (modern.isEmpty()) {
                skipped++;
                JsonObject skippedRegistration = new JsonObject();
                skippedRegistration.addProperty("ordinal", ordinal);
                skippedRegistration.addProperty("kind", registration.kind().name().toLowerCase());
                skippedRegistration.addProperty("sourceOwner", registration.sourceOwner());
                skippedRegistration.addProperty("sourceMethod", registration.sourceMethod());
                skippedRegistrations.add(skippedRegistration);
                context.diagnostics().warning(
                        "LFB-CONVERT-RECIPE-0011",
                        SupportLevel.MANUAL_REQUIRED,
                        "Recipe registration could not be materialized without guessing: kind="
                                + registration.kind().name().toLowerCase()
                                + ", source=" + registration.sourceOwner() + "." + registration.sourceMethod()
                                + ", ordinal=" + ordinal + "."
                );
                continue;
            }

            String kind = registration.kind().name().toLowerCase();
            String file = "legacy_" + kind + "_" + String.format("%04d", ordinal) + ".json";
            Path output = context.stagingDir()
                    .resolve("data")
                    .resolve(context.metadata().fabricId())
                    .resolve("recipe")
                    .resolve(file);
            Files.createDirectories(output.getParent());
            Files.writeString(output, GSON.toJson(modern.get()) + "\n", StandardCharsets.UTF_8);
            emitted++;
        }

        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        report.addProperty("emittedRecipes", emitted);
        report.addProperty("skippedRecipes", skipped);
        report.addProperty("provenOreDictionaryNames", oreDictionary.names().size());
        report.add("skipped", skippedRegistrations);
        Path reportPath = context.stagingDir().resolve("legacyforgebridge/recipe-materialization.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, GSON.toJson(report) + "\n", StandardCharsets.UTF_8);

        if (emitted > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-RECIPE-0010",
                    SupportLevel.ADAPTED,
                    "Emitted " + emitted + " modern crafting/smelting recipe resources from source-proven legacy registrations; skipped="
                            + skipped + ", locally proven OreDictionary names=" + oreDictionary.names().size() + "."
            );
        }
        if (!oreDictionary.names().isEmpty()) {
            context.diagnostics().info(
                    "LFB-CONVERT-ORE-0010",
                    SupportLevel.RUNTIME_BRIDGE,
                    "OreDictionary recipe ingredients are currently materialized from exact registrations proven in this converted JAR. Cross-mod/global OreDictionary interoperability remains a separate compatibility stage."
            );
        }
        return new Summary(emitted, skipped, oreDictionary.names().size());
    }

    record Summary(int emittedRecipes, int skippedRecipes, int oreDictionaryNames) { }
}
