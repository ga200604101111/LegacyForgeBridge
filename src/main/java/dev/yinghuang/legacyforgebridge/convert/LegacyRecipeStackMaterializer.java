package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Converts a proven legacy recipe stack into current item identity/component data. */
public final class LegacyRecipeStackMaterializer {
    private LegacyRecipeStackMaterializer() { }

    public record ModernStack(String id, int count, JsonObject components, boolean wildcardMeta) {
        public ModernStack {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            if (count <= 0) throw new IllegalArgumentException("count");
            components = components == null ? new JsonObject() : components.deepCopy();
        }

        /** JSON accepted by vanilla/Fabric ingredient codecs in 1.21.11. */
        public JsonElement ingredientJson() {
            if (wildcardMeta || components.isEmpty()) return new JsonPrimitive(id);
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("fabric:type", "fabric:components");
            ingredient.addProperty("base", id);
            ingredient.add("components", components.deepCopy());
            return ingredient;
        }

        /** ItemStack-shaped JSON used by modern crafting/cooking recipe result codecs. */
        public JsonObject resultJson() {
            if (wildcardMeta) {
                throw new IllegalStateException("Wildcard legacy metadata cannot be a concrete recipe result");
            }
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            if (count != 1) result.addProperty("count", count);
            if (!components.isEmpty()) result.add("components", components.deepCopy());
            return result;
        }
    }

    public static Optional<ModernStack> materialize(
            LegacyRecipeStackResolver.StackSpec stack,
            ConversionContext context
    ) {
        LegacyRecipeAnalyzer.RegistryValue registry = stack.registry();
        String namespace = registry.legacyNamespace();
        if (namespace == null || namespace.isBlank()) {
            namespace = context.metadata().primary().modId();
        }

        if ("minecraft".equalsIgnoreCase(namespace)) {
            if (stack.wildcardMeta()) {
                // One old pre-flattening vanilla id may fan out to many modern ids. A simple item
                // ingredient cannot preserve that meaning, so leave it unresolved for a later
                // wildcard/tag expansion stage instead of silently choosing meta 0.
                return Optional.empty();
            }
            try {
                LegacyVanillaStackDataFix.ModernStack fixed = LegacyVanillaStackDataFix.upgrade(
                        registry.registryName(), stack.meta());
                JsonObject exactComponents = fixed.components().deepCopy();
                preserveExplicitZeroDamage(registry.registryName(), stack.meta(), fixed.id(), exactComponents);
                return Optional.of(new ModernStack(fixed.id(), stack.count(), exactComponents, false));
            } catch (RuntimeException invalidVanillaStack) {
                return Optional.empty();
            }
        }

        String legacyIdentity = namespace + ":" + registry.registryName();
        Map<String, String> items = context.registryIdentities().getOrDefault("items", Map.of());
        String modernId = items.get(legacyIdentity);
        if (modernId == null) modernId = items.get(legacyIdentity.toLowerCase(Locale.ROOT));
        if (modernId == null || modernId.isBlank()) return Optional.empty();

        JsonObject components = new JsonObject();
        boolean wildcard = stack.wildcardMeta();
        if (!wildcard) {
            components.addProperty(LegacyStackComponents.LEGACY_META_ID.toString(), stack.meta());
        }
        return Optional.of(new ModernStack(modernId, stack.count(), components, wildcard));
    }

    /**
     * Current ItemStack serialization omits unchanged default components. In 1.7, however, a
     * recipe ingredient with data value 0 is still exact: an undamaged sword must not match a
     * damaged sword. Probe data value 1 through the same DFU chain; if it proves this legacy data
     * slot became modern DAMAGE on the same item identity, explicitly require DAMAGE=0.
     */
    private static void preserveExplicitZeroDamage(
            String legacyRegistryName,
            int meta,
            String modernId,
            JsonObject components
    ) {
        if (meta != 0 || components.has("minecraft:damage")) return;
        LegacyVanillaStackDataFix.ModernStack one = LegacyVanillaStackDataFix.upgrade(legacyRegistryName, 1);
        JsonElement damage = one.components().get("minecraft:damage");
        if (modernId.equals(one.id())
                && damage != null
                && damage.isJsonPrimitive()
                && damage.getAsJsonPrimitive().isNumber()
                && damage.getAsInt() == 1) {
            components.addProperty("minecraft:damage", 0);
        }
    }
}
