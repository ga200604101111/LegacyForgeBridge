package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Converts admitted processor recipes into current item/component identities without runtime source code. */
public final class LegacySingleInputProcessorRecipeMaterializer {
    private LegacySingleInputProcessorRecipeMaterializer() { }

    public static Optional<JsonObject> materialize(
            LegacySingleInputProcessorRecipeAnalyzer.Recipe recipe,
            ConversionContext context
    ) {
        LegacyRecipeValueResolver resolver = new LegacyRecipeValueResolver();
        JsonArray inputAlternatives = new JsonArray();
        if (recipe.input() instanceof LegacySingleInputProcessorRecipeAnalyzer.StackInput input) {
            LegacyRecipeAnalyzer.Value resolvedInput = resolver.resolve(input.stack());
            Optional<LegacyRecipeStackResolver.StackSpec> source = LegacyRecipeStackResolver.resolve(resolvedInput);
            if (source.isEmpty() || !materializeInput(source.get(), context, inputAlternatives)) return Optional.empty();
        } else {
            // Ore-key processor recipes need a modern tag/member expansion stage. Keep them fail-closed
            // until that evidence is available instead of choosing the first legacy OreDictionary member.
            return Optional.empty();
        }

        Optional<LegacyRecipeStackMaterializer.ModernStack> output = concrete(resolver.resolve(recipe.output()), context);
        if (output.isEmpty()) return Optional.empty();

        JsonObject json = new JsonObject();
        json.add("inputAlternatives", inputAlternatives);
        json.add("output", output.get().resultJson());
        json.addProperty("bonusChance", recipe.bonusChance());
        if (recipe.bonus() != LegacyRecipeAnalyzer.NullValue.INSTANCE) {
            Optional<LegacyRecipeStackMaterializer.ModernStack> bonus = concrete(resolver.resolve(recipe.bonus()), context);
            if (bonus.isEmpty()) return Optional.empty();
            json.add("bonus", bonus.get().resultJson());
        }
        json.addProperty("sourceOwner", recipe.sourceOwner());
        json.addProperty("sourceMethod", recipe.sourceMethod());
        return Optional.of(json);
    }

    private static Optional<LegacyRecipeStackMaterializer.ModernStack> concrete(
            LegacyRecipeAnalyzer.Value value, ConversionContext context) {
        Optional<LegacyRecipeStackResolver.StackSpec> stack = LegacyRecipeStackResolver.resolve(value);
        if (stack.isEmpty() || stack.get().wildcardMeta()) return Optional.empty();
        return LegacyRecipeStackMaterializer.materialize(stack.get(), context)
                .filter(value1 -> !value1.wildcardMeta());
    }

    private static boolean materializeInput(
            LegacyRecipeStackResolver.StackSpec source,
            ConversionContext context,
            JsonArray output
    ) {
        if (!source.wildcardMeta()) {
            Optional<LegacyRecipeStackMaterializer.ModernStack> modern =
                    LegacyRecipeStackMaterializer.materialize(source, context);
            if (modern.isEmpty()) return false;
            output.add(inputJson(modern.get()));
            return true;
        }

        LegacyRecipeAnalyzer.RegistryValue registry = source.registry();
        String namespace = registry.legacyNamespace();
        if (namespace == null || namespace.isBlank()) namespace = context.metadata().primary().modId();
        if (!"minecraft".equalsIgnoreCase(namespace)
                || registry.kind() != LegacyRegistryAnalyzer.Kind.BLOCK) return false;

        Map<String, JsonObject> unique = new LinkedHashMap<>();
        for (int meta = 0; meta < 16; meta++) {
            try {
                LegacyRecipeStackResolver.StackSpec exact = new LegacyRecipeStackResolver.StackSpec(registry, source.count(), meta);
                Optional<LegacyRecipeStackMaterializer.ModernStack> modern =
                        LegacyRecipeStackMaterializer.materialize(exact, context);
                if (modern.isEmpty()) continue;
                JsonObject input = inputJson(modern.get());
                unique.putIfAbsent(input.toString(), input);
            } catch (RuntimeException ignoredInvalidVariant) {
                // A pre-flattening block may not define every metadata value. Only proven DFU
                // variants are retained; an empty expansion fails below.
            }
        }
        unique.values().forEach(output::add);
        return !unique.isEmpty();
    }

    private static JsonObject inputJson(LegacyRecipeStackMaterializer.ModernStack stack) {
        JsonObject input = stack.resultJson();
        // Processor input matching also requires the source stack count, unlike ordinary recipe
        // ingredients where count is implicit one.
        if (stack.count() != 1) input.addProperty("count", stack.count());
        return input;
    }
}
