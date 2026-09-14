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
 * Exact, source-proven OreDictionary view for one converted legacy mod.
 *
 * <p>Metadata-specific registrations cannot safely be represented by a plain modern item tag. The
 * index therefore preserves every proven registered stack as an Ingredient and combines multiple
 * entries with Fabric's {@code fabric:any}. Cross-mod/global OreDictionary interoperability is a
 * separate layer; an unproven ore name is intentionally left unresolved here.</p>
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
        List<JsonElement> values = entries.get(oreName);
        if (values == null || values.isEmpty()) return Optional.empty();
        if (values.size() == 1) return Optional.of(values.getFirst().deepCopy());

        JsonObject any = new JsonObject();
        any.addProperty("fabric:type", "fabric:any");
        JsonArray ingredients = new JsonArray();
        values.forEach(value -> ingredients.add(value.deepCopy()));
        any.add("ingredients", ingredients);
        return Optional.of(any);
    }

    public Set<String> names() {
        return new LinkedHashSet<>(entries.keySet());
    }

    public int entryCount(String oreName) {
        return entries.getOrDefault(oreName, List.of()).size();
    }
}
