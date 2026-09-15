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

/**
 * Exact source-proven OreDictionary entries plus Forge 1.7.10 platform conventions.
 *
 * <p>Metadata-specific mod registrations cannot safely be represented by a plain item tag, so each
 * proven stack remains an exact Ingredient. Forge's own built-in names are independently bridged by
 * {@link LegacyOreDictionaryConventions1710}; both sources are unioned with Fabric's
 * {@code fabric:any}. Unknown non-platform names remain unresolved rather than guessed.</p>
 */
public final class LegacyOreDictionaryIndex {
    private final Map<String, List<JsonElement>> entries;

    private LegacyOreDictionaryIndex(Map<String, List<JsonElement>> entries) {
        this.entries = entries;
    }

    public static LegacyOreDictionaryIndex build(
            LegacyRecipeAnalyzer.Analysis analysis,
            ConversionContext context
    ) {
        Map<String, List<JsonElement>> entries = new LinkedHashMap<>();
        LegacyRecipeValueResolver resolver = new LegacyRecipeValueResolver();
        for (LegacyRecipeAnalyzer.Registration source : analysis.of(LegacyRecipeAnalyzer.Kind.ORE_REGISTER)) {
            LegacyRecipeAnalyzer.Registration registration = resolver.resolve(source);
            if (registration.arguments().size() < 2) continue;
            LegacyRecipeAnalyzer.Value nameValue = registration.arguments().get(0);
            LegacyRecipeAnalyzer.Value stackValue = registration.arguments().get(1);
            if (!(nameValue instanceof LegacyRecipeAnalyzer.TextValue name) || name.value().isBlank()) continue;

            Optional<LegacyRecipeStackResolver.StackSpec> stack = LegacyRecipeStackResolver.resolve(stackValue);
            if (stack.isEmpty()) continue;
            Optional<LegacyRecipeStackMaterializer.ModernStack> modern =
                    LegacyRecipeStackMaterializer.materialize(stack.get(), context);
            if (modern.isEmpty()) continue;

            entries.computeIfAbsent(name.value(), ignored -> new ArrayList<>())
                    .add(modern.get().ingredientJson());
        }

        Map<String, List<JsonElement>> immutable = new LinkedHashMap<>();
        entries.forEach((name, values) -> {
            LinkedHashMap<String, JsonElement> unique = new LinkedHashMap<>();
            for (JsonElement value : values) unique.putIfAbsent(value.toString(), value.deepCopy());
            immutable.put(name, List.copyOf(unique.values()));
        });
        return new LegacyOreDictionaryIndex(Map.copyOf(immutable));
    }

    public Optional<JsonElement> ingredient(String oreName) {
        LinkedHashMap<String, JsonElement> union = new LinkedHashMap<>();
        LegacyOreDictionaryConventions1710.ingredient(oreName)
                .ifPresent(value -> union.put(value.toString(), value.deepCopy()));
        for (JsonElement value : entries.getOrDefault(oreName, List.of())) {
            union.putIfAbsent(value.toString(), value.deepCopy());
        }
        if (union.isEmpty()) return Optional.empty();
        if (union.size() == 1) return Optional.of(union.values().iterator().next().deepCopy());

        JsonObject any = new JsonObject();
        any.addProperty("fabric:type", "fabric:any");
        JsonArray ingredients = new JsonArray();
        union.values().forEach(value -> ingredients.add(value.deepCopy()));
        any.add("ingredients", ingredients);
        return Optional.of(any);
    }

    /** Names proven by this source JAR; platform conventions are intentionally reported separately. */
    public Set<String> names() {
        return new LinkedHashSet<>(entries.keySet());
    }

    public int entryCount(String oreName) {
        return entries.getOrDefault(oreName, List.of()).size();
    }
}
