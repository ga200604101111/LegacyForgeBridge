package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimePass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-registration runtime rule registry for source-proven metadata-indexed legacy snowballs.
 *
 * The registry intentionally owns only normalized data in this revision. Projectile EntityType,
 * specialized item behavior and renderer registration stay closed until their dedicated runtime
 * slices are admitted.
 */
public final class LegacyVariantSnowballRuntimeRegistry {
    public enum Effect {
        NONE,
        POTION,
        RANDOM_TELEPORT
    }

    public record Variant(int metadata, int baseDamage, Effect effect,
                          String potion, int duration, int amplifier,
                          double horizontalRandomRadius, int verticalRandomBound,
                          int verticalRandomOffset, int portalParticleCount,
                          String portalSound) {
        public Variant {
            if (metadata < 0 || baseDamage < 0 || baseDamage > 127 || effect == null) {
                throw new IllegalArgumentException("Invalid converted variant-snowball variant");
            }
            switch (effect) {
                case NONE -> {
                    if (potion != null || duration != 0 || amplifier != 0
                            || horizontalRandomRadius != 0D || verticalRandomBound != 0
                            || verticalRandomOffset != 0 || portalParticleCount != 0
                            || portalSound != null) {
                        throw new IllegalArgumentException("NONE variant carries unexpected effect data");
                    }
                }
                case POTION -> {
                    if (potion == null || !List.of("poison", "confusion", "regeneration").contains(potion)
                            || duration < 0 || amplifier < 0
                            || horizontalRandomRadius != 0D || verticalRandomBound != 0
                            || verticalRandomOffset != 0 || portalParticleCount != 0
                            || portalSound != null) {
                        throw new IllegalArgumentException("Invalid POTION variant data");
                    }
                }
                case RANDOM_TELEPORT -> {
                    if (potion != null || duration != 0 || amplifier != 0
                            || Double.compare(horizontalRandomRadius, 16D) != 0
                            || verticalRandomBound != 8 || verticalRandomOffset != -4
                            || portalParticleCount != 128
                            || !"mob.endermen.portal".equals(portalSound)) {
                        throw new IllegalArgumentException("Invalid RANDOM_TELEPORT variant data");
                    }
                }
            }
        }
    }

    public record Rule(Identifier id, Identifier projectileId,
                       String launchSound, float launchVolume,
                       float launchPitchNumerator, float launchPitchRandomScale,
                       float launchPitchBase, boolean consumeOutsideCreative,
                       boolean serverAuthoritativeLaunch,
                       Map<Integer, Variant> variants,
                       boolean sourceSemanticsComplete, boolean runtimeRuleReady) {
        public Rule {
            if (id == null || projectileId == null
                    || !id.getNamespace().equals(projectileId.getNamespace())
                    || !"random.bow".equals(launchSound)
                    || Float.compare(launchVolume, 0.5F) != 0
                    || Float.compare(launchPitchNumerator, 0.4F) != 0
                    || Float.compare(launchPitchRandomScale, 0.4F) != 0
                    || Float.compare(launchPitchBase, 0.8F) != 0
                    || !consumeOutsideCreative || !serverAuthoritativeLaunch
                    || variants == null || variants.isEmpty()
                    || !sourceSemanticsComplete || !runtimeRuleReady) {
                throw new IllegalArgumentException("Invalid converted variant-snowball runtime rule");
            }
            variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
        }

        public Variant variant(int metadata) {
            return variants.get(metadata);
        }
    }

    private static final Map<Identifier, Rule> RULES = new ConcurrentHashMap<>();

    private LegacyVariantSnowballRuntimeRegistry() { }

    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyVariantSnowballRuntimePass.OUTPUT).orElse(null);
        if (path == null || !Files.isRegularFile(path)) return;

        int loaded = 0;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", 0) != LegacyVariantSnowballRuntimePass.SCHEMA
                    || !bool(root, "runtimeRuleRegistryWired")
                    || !bool(root, "preRegistrationRuleLoadWired")
                    || bool(root, "runtimeImplementationWired")) {
                return;
            }

            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                Rule rule = parse(element.getAsJsonObject());
                if (rule == null || !modId.equals(rule.id().getNamespace())) continue;
                register(rule);
                loaded++;
            }
            if (loaded > 0) {
                LegacyForgeBridge.LOGGER.info(
                        "Loaded converted variant-snowball pre-registration rules: mod={}, rules={}",
                        modId, loaded);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to load converted variant-snowball runtime rules for " + modId, exception);
        }
    }

    public static Rule rule(Identifier id) {
        return id == null ? null : RULES.get(id);
    }

    public static List<Rule> rules(String namespace) {
        return RULES.values().stream()
                .filter(rule -> namespace.equals(rule.id().getNamespace()))
                .sorted(java.util.Comparator.comparing(rule -> rule.id().toString()))
                .toList();
    }

    static Rule parseForTests(JsonObject value) {
        return parse(value);
    }

    static void registerForTests(Rule rule) {
        register(rule);
    }

    static void clearForTests() {
        RULES.clear();
    }

    private static void register(Rule rule) {
        Rule previous = RULES.putIfAbsent(rule.id(), rule);
        if (previous != null && !previous.equals(rule)) {
            throw new IllegalStateException(
                    "Conflicting converted variant-snowball runtime rule for " + rule.id());
        }
    }

    private static Rule parse(JsonObject value) {
        try {
            if (!bool(value, "runtimeRuleReady")
                    || !bool(value, "preRegistrationRuleLoadWired")
                    || bool(value, "runtimeImplementationWired")
                    || !"VARIANT_SNOWBALL".equals(string(value, "adapter", null))
                    || !"THROWN_ITEM".equals(string(value, "rendererAdapter", null))
                    || !bool(value, "sourceSemanticsComplete")) {
                return null;
            }

            Identifier id = Identifier.parse(string(value, "id", ""));
            Identifier projectileId = Identifier.parse(string(value, "projectileId", ""));
            JsonArray array = value.getAsJsonArray("variants");
            if (array == null || array.isEmpty()
                    || integer(value, "variantCount", -1) != array.size()) {
                return null;
            }

            Map<Integer, Variant> variants = new LinkedHashMap<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) return null;
                Variant variant = parseVariant(element.getAsJsonObject());
                if (variant == null || variants.putIfAbsent(variant.metadata(), variant) != null) {
                    return null;
                }
            }

            return new Rule(
                    id,
                    projectileId,
                    string(value, "launchSound", null),
                    decimal(value, "launchVolume", Float.NaN),
                    decimal(value, "launchPitchNumerator", Float.NaN),
                    decimal(value, "launchPitchRandomScale", Float.NaN),
                    decimal(value, "launchPitchBase", Float.NaN),
                    bool(value, "consumeOutsideCreative"),
                    bool(value, "serverAuthoritativeLaunch"),
                    variants,
                    bool(value, "sourceSemanticsComplete"),
                    bool(value, "runtimeRuleReady"));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Variant parseVariant(JsonObject value) {
        int metadata = integer(value, "metadata", -1);
        int baseDamage = integer(value, "baseDamage", -1);
        String effectName = string(value, "effect", null);
        if (effectName == null) return null;

        Effect effect;
        try {
            effect = Effect.valueOf(effectName);
        } catch (IllegalArgumentException unknown) {
            return null;
        }

        return switch (effect) {
            case NONE -> new Variant(
                    metadata, baseDamage, effect,
                    null, 0, 0,
                    0D, 0, 0, 0, null);
            case POTION -> new Variant(
                    metadata, baseDamage, effect,
                    string(value, "potion", null),
                    integer(value, "duration", -1),
                    integer(value, "amplifier", -1),
                    0D, 0, 0, 0, null);
            case RANDOM_TELEPORT -> new Variant(
                    metadata, baseDamage, effect,
                    null, 0, 0,
                    decimalDouble(value, "horizontalRandomRadius", Double.NaN),
                    integer(value, "verticalRandomBound", Integer.MIN_VALUE),
                    integer(value, "verticalRandomOffset", Integer.MIN_VALUE),
                    integer(value, "portalParticleCount", -1),
                    string(value, "portalSound", null));
        };
    }

    private static String string(JsonObject value, String key, String fallback) {
        JsonElement element = value == null ? null : value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int integer(JsonObject value, String key, int fallback) {
        JsonElement element = value == null ? null : value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static float decimal(JsonObject value, String key, float fallback) {
        JsonElement element = value == null ? null : value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static double decimalDouble(JsonObject value, String key, double fallback) {
        JsonElement element = value == null ? null : value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value == null ? null : value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
