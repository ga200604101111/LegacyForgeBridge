package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.compat.LegacyStackComponents;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Maps a proven 1.7 fuel rule onto the current item/component identity space. */
public final class LegacyFuelRuleMaterializer {
    public static final int ANY = -1;

    private LegacyFuelRuleMaterializer() { }

    public record ModernFuelRule(String id, int legacyMeta, int damage, int burnTicks) {
        public ModernFuelRule {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            if (legacyMeta < ANY || damage < ANY || burnTicks <= 0) throw new IllegalArgumentException();
        }
    }

    public static Optional<ModernFuelRule> materialize(
            LegacyFuelHandlerAnalyzer.FuelRule rule,
            ConversionContext context
    ) {
        LegacyRecipeAnalyzer.RegistryValue registry = rule.registry();
        String namespace = registry.legacyNamespace();
        if (namespace == null || namespace.isBlank()) namespace = context.metadata().primary().modId();

        if (rule.anyMetadata()) {
            if ("minecraft".equalsIgnoreCase(namespace)) {
                // A pre-flattening vanilla item identity may represent several current items.
                // Do not narrow an old wildcard rule to the meta-0 modern identity.
                return Optional.empty();
            }
            String id = mappedModItem(context, namespace, registry.registryName());
            return id == null ? Optional.empty() : Optional.of(new ModernFuelRule(id, ANY, ANY, rule.burnTicks()));
        }

        LegacyRecipeStackResolver.StackSpec stack = new LegacyRecipeStackResolver.StackSpec(
                registry, 1, rule.metadata());
        Optional<LegacyRecipeStackMaterializer.ModernStack> modern =
                LegacyRecipeStackMaterializer.materialize(stack, context);
        if (modern.isEmpty() || modern.get().wildcardMeta()) return Optional.empty();

        JsonObject components = modern.get().components();
        int legacyMeta = ANY;
        int damage = ANY;
        for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
            if (LegacyStackComponents.LEGACY_META_ID.toString().equals(entry.getKey())
                    && entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsJsonPrimitive().isNumber()) {
                legacyMeta = entry.getValue().getAsInt();
            } else if ("minecraft:damage".equals(entry.getKey())
                    && entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsJsonPrimitive().isNumber()) {
                damage = entry.getValue().getAsInt();
            } else {
                // The runtime matcher intentionally supports only the two exact scalar component
                // semantics proven here. More complex DFU component output remains fail-closed.
                return Optional.empty();
            }
        }
        return Optional.of(new ModernFuelRule(modern.get().id(), legacyMeta, damage, rule.burnTicks()));
    }

    private static String mappedModItem(ConversionContext context, String namespace, String registryName) {
        String legacy = namespace + ":" + registryName;
        Map<String, String> items = context.registryIdentities().getOrDefault("items", Map.of());
        String modern = items.get(legacy);
        if (modern == null) modern = items.get(legacy.toLowerCase(Locale.ROOT));
        return modern;
    }
}
