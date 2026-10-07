package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyCloningRecipeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyOreDictionaryIndex;
import dev.yinghuang.legacyforgebridge.convert.LegacyRecipeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRecipeJsonMaterializer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVanillaStackDataFix;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
        LegacyCloningRecipeAnalyzer.Analysis cloning = new LegacyCloningRecipeAnalyzer().analyze(context.sourceJar());
        materialize(context, analysis, cloning);
    }

    static Summary materialize(ConversionContext context, LegacyRecipeAnalyzer.Analysis analysis) throws Exception {
        return materialize(context, analysis, new LegacyCloningRecipeAnalyzer.Analysis(List.of(), List.of()));
    }

    static Summary materialize(
            ConversionContext context,
            LegacyRecipeAnalyzer.Analysis analysis,
            LegacyCloningRecipeAnalyzer.Analysis cloning
    ) throws Exception {
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

        int provenCloningRecipes = cloning.rules().size();
        int emittedCloningRecipes = 0;
        int cloneOrdinal = 0;
        for (LegacyCloningRecipeAnalyzer.Rule rule : cloning.rules()) {
            Optional<String> fullItem = modernItem(rule.fullItem(), context);
            Optional<String> blankItem = modernItem(rule.blankItem(), context);
            int ordinal = cloneOrdinal++;
            if (fullItem.isEmpty() || blankItem.isEmpty() || fullItem.get().equals(blankItem.get())) {
                skipped++;
                JsonObject skippedRegistration = new JsonObject();
                skippedRegistration.addProperty("ordinal", ordinal);
                skippedRegistration.addProperty("kind", "legacy_clone");
                skippedRegistration.addProperty("sourceOwner", rule.sourceOwner());
                skippedRegistration.addProperty("sourceMethod", rule.sourceMethod());
                skippedRegistrations.add(skippedRegistration);
                context.diagnostics().warning(
                        "LFB-CONVERT-RECIPE-0012",
                        SupportLevel.MANUAL_REQUIRED,
                        "Source-proven cloning recipe could not resolve both modern item identities without guessing: source="
                                + rule.sourceOwner() + "." + rule.sourceMethod()
                                + ", recipeClass=" + rule.sourceRecipeClass() + ", ordinal=" + ordinal + "."
                );
                continue;
            }

            JsonObject json = new JsonObject();
            json.addProperty("type", "legacyforgebridge:legacy_clone");
            json.addProperty("full_item", fullItem.get());
            json.addProperty("blank_item", blankItem.get());
            json.addProperty("copy_custom_name", rule.copyCustomName());
            Path output = context.stagingDir()
                    .resolve("data")
                    .resolve(context.metadata().fabricId())
                    .resolve("recipe")
                    .resolve("legacy_clone_" + String.format("%04d", ordinal) + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            emitted++;
            emittedCloningRecipes++;
        }

        for (String diagnostic : cloning.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-RECIPE-0013",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic
            );
        }

        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        report.addProperty("emittedRecipes", emitted);
        report.addProperty("skippedRecipes", skipped);
        report.addProperty("provenOreDictionaryNames", oreDictionary.names().size());
        report.addProperty("provenCloningRecipes", provenCloningRecipes);
        report.addProperty("emittedCloningRecipes", emittedCloningRecipes);
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
        if (emittedCloningRecipes > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-RECIPE-0014",
                    SupportLevel.ADAPTED,
                    "Emitted " + emittedCloningRecipes + " source-proven legacy cloning recipes using the shared LFB custom recipe serializer."
            );
        }
        return new Summary(emitted, skipped, oreDictionary.names().size());
    }

    private static Optional<String> modernItem(
            LegacyCloningRecipeAnalyzer.ItemRef item,
            ConversionContext context
    ) {
        String namespace = item.legacyNamespace();
        if (namespace == null || namespace.isBlank()) namespace = context.metadata().primary().modId();

        if ("minecraft".equalsIgnoreCase(namespace)) {
            try {
                return Optional.of(LegacyVanillaStackDataFix.upgrade(item.registryName(), 0).id());
            } catch (RuntimeException unresolved) {
                return Optional.empty();
            }
        }

        String legacyIdentity = namespace + ":" + item.registryName();
        Map<String, String> items = context.registryIdentities().getOrDefault("items", Map.of());
        String modern = items.get(legacyIdentity);
        if (modern == null) modern = items.get(legacyIdentity.toLowerCase(Locale.ROOT));
        return modern == null || modern.isBlank() ? Optional.empty() : Optional.of(modern);
    }

    record Summary(int emittedRecipes, int skippedRecipes, int oreDictionaryNames) { }
}
