package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pure conversion of one source-proven legacy recipe registration into current recipe JSON. */
public final class LegacyRecipeJsonMaterializer {
    private LegacyRecipeJsonMaterializer() { }

    public static Optional<JsonObject> materialize(
            LegacyRecipeAnalyzer.Registration registration,
            ConversionContext context,
            LegacyOreDictionaryIndex oreDictionary
    ) {
        LegacyRecipeAnalyzer.Registration resolved = new LegacyRecipeValueResolver().resolve(registration);
        List<LegacyRecipeAnalyzer.Value> args = resolved.arguments();
        return switch (resolved.kind()) {
            case SHAPED -> shaped(args, context, oreDictionary);
            case SHAPELESS -> shapeless(args, context, oreDictionary);
            case SMELTING -> smelting(args, context, oreDictionary);
            default -> Optional.empty();
        };
    }

    private static Optional<JsonObject> shaped(
            List<LegacyRecipeAnalyzer.Value> args,
            ConversionContext context,
            LegacyOreDictionaryIndex oreDictionary
    ) {
        if (args.size() != 2 || !(args.get(1) instanceof LegacyRecipeAnalyzer.ArrayValue spec)) {
            return Optional.empty();
        }
        Optional<LegacyRecipeStackMaterializer.ModernStack> result = result(args.getFirst(), context);
        if (result.isEmpty()) return Optional.empty();

        List<LegacyRecipeAnalyzer.Value> values = spec.elements();
        List<String> pattern = new ArrayList<>();
        int index = 0;
        while (index < values.size() && values.get(index) instanceof LegacyRecipeAnalyzer.TextValue row) {
            pattern.add(row.value());
            index++;
        }
        if (!validPattern(pattern) || ((values.size() - index) & 1) != 0) return Optional.empty();

        Map<Character, JsonElement> keys = new LinkedHashMap<>();
        while (index < values.size()) {
            LegacyRecipeAnalyzer.Value keyValue = values.get(index++);
            LegacyRecipeAnalyzer.Value ingredientValue = values.get(index++);
            if (!(keyValue instanceof LegacyRecipeAnalyzer.CharacterValue key) || key.value() == ' ') {
                return Optional.empty();
            }
            Optional<JsonElement> ingredient = ingredient(ingredientValue, context, oreDictionary);
            if (ingredient.isEmpty()) return Optional.empty();
            keys.put(key.value(), ingredient.get());
        }

        Set<Character> used = new LinkedHashSet<>();
        for (String row : pattern) {
            for (int column = 0; column < row.length(); column++) {
                char value = row.charAt(column);
                if (value != ' ') used.add(value);
            }
        }
        if (!keys.keySet().containsAll(used)) return Optional.empty();

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "misc");
        JsonArray rows = new JsonArray();
        pattern.forEach(rows::add);
        json.add("pattern", rows);
        JsonObject keyJson = new JsonObject();
        for (Map.Entry<Character, JsonElement> entry : keys.entrySet()) {
            if (used.contains(entry.getKey())) keyJson.add(String.valueOf(entry.getKey()), entry.getValue().deepCopy());
        }
        json.add("key", keyJson);
        json.add("result", result.get().resultJson());
        return Optional.of(json);
    }

    private static Optional<JsonObject> shapeless(
            List<LegacyRecipeAnalyzer.Value> args,
            ConversionContext context,
            LegacyOreDictionaryIndex oreDictionary
    ) {
        if (args.size() != 2 || !(args.get(1) instanceof LegacyRecipeAnalyzer.ArrayValue sourceIngredients)) {
            return Optional.empty();
        }
        Optional<LegacyRecipeStackMaterializer.ModernStack> result = result(args.getFirst(), context);
        if (result.isEmpty()) return Optional.empty();
        if (sourceIngredients.elements().isEmpty() || sourceIngredients.elements().size() > 9) return Optional.empty();

        JsonArray ingredients = new JsonArray();
        for (LegacyRecipeAnalyzer.Value source : sourceIngredients.elements()) {
            Optional<JsonElement> ingredient = ingredient(source, context, oreDictionary);
            if (ingredient.isEmpty()) return Optional.empty();
            ingredients.add(ingredient.get());
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.addProperty("category", "misc");
        json.add("ingredients", ingredients);
        json.add("result", result.get().resultJson());
        return Optional.of(json);
    }

    private static Optional<JsonObject> smelting(
            List<LegacyRecipeAnalyzer.Value> args,
            ConversionContext context,
            LegacyOreDictionaryIndex oreDictionary
    ) {
        if (args.size() != 3 || !(args.get(2) instanceof LegacyRecipeAnalyzer.NumberValue experience)) {
            return Optional.empty();
        }
        Optional<JsonElement> ingredient = ingredient(args.get(0), context, oreDictionary);
        Optional<LegacyRecipeStackMaterializer.ModernStack> result = result(args.get(1), context);
        if (ingredient.isEmpty() || result.isEmpty()) return Optional.empty();
        double xp = experience.value().doubleValue();
        if (!Double.isFinite(xp) || xp < 0.0D) return Optional.empty();

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:smelting");
        json.addProperty("category", "misc");
        json.add("ingredient", ingredient.get());
        json.add("result", result.get().resultJson());
        json.addProperty("experience", xp);
        json.addProperty("cookingtime", 200);
        return Optional.of(json);
    }

    private static Optional<LegacyRecipeStackMaterializer.ModernStack> result(
            LegacyRecipeAnalyzer.Value value,
            ConversionContext context
    ) {
        Optional<LegacyRecipeStackResolver.StackSpec> stack = LegacyRecipeStackResolver.resolve(value);
        if (stack.isEmpty() || stack.get().wildcardMeta()) return Optional.empty();
        return LegacyRecipeStackMaterializer.materialize(stack.get(), context)
                .filter(modern -> !modern.wildcardMeta());
    }

    private static Optional<JsonElement> ingredient(
            LegacyRecipeAnalyzer.Value value,
            ConversionContext context,
            LegacyOreDictionaryIndex oreDictionary
    ) {
        if (value instanceof LegacyRecipeAnalyzer.TextValue oreName) {
            return oreDictionary.ingredient(oreName.value());
        }
        Optional<LegacyRecipeStackResolver.StackSpec> stack = LegacyRecipeStackResolver.resolve(value);
        if (stack.isEmpty()) return Optional.empty();
        return LegacyRecipeStackMaterializer.materialize(stack.get(), context)
                .map(LegacyRecipeStackMaterializer.ModernStack::ingredientJson);
    }

    private static boolean validPattern(List<String> pattern) {
        if (pattern.isEmpty() || pattern.size() > 3) return false;
        int width = pattern.getFirst().length();
        if (width < 1 || width > 3) return false;
        for (String row : pattern) if (row.length() != width) return false;
        return true;
    }
}
