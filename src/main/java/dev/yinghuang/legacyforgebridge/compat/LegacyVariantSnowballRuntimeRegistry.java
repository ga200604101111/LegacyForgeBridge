package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimePass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVariantSnowballProjectile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-registration runtime registry for source-proven metadata-indexed legacy snowballs.
 *
 * Rules are loaded before generated item construction. This revision also materializes the dormant
 * modern projectile EntityType from source-proven legacy registration metadata. No item path can
 * launch the projectile yet, and impact/presentation runtimes remain closed.
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
                       String legacyProjectileRegistryName, int legacyProjectileNumericId,
                       int legacyTrackingRangeBlocks, int modernClientTrackingRangeChunks,
                       int updateFrequency, boolean velocityUpdates,
                       float width, float height,
                       String launchSound, float launchVolume,
                       float launchPitchNumerator, float launchPitchRandomScale,
                       float launchPitchBase, boolean consumeOutsideCreative,
                       boolean serverAuthoritativeLaunch,
                       Map<Integer, Variant> variants,
                       boolean sourceSemanticsComplete, boolean runtimeRuleReady,
                       boolean projectileEntityTypeRegistrationWired) {
        public Rule {
            if (id == null || projectileId == null
                    || !id.getNamespace().equals(projectileId.getNamespace())
                    || legacyProjectileRegistryName == null || legacyProjectileRegistryName.isBlank()
                    || legacyProjectileNumericId < 0
                    || legacyTrackingRangeBlocks <= 0
                    || modernClientTrackingRangeChunks != trackingChunks(legacyTrackingRangeBlocks)
                    || updateFrequency <= 0 || !velocityUpdates
                    || Float.compare(width, LegacyVariantSnowballRuntimePass.VANILLA_SNOWBALL_WIDTH) != 0
                    || Float.compare(height, LegacyVariantSnowballRuntimePass.VANILLA_SNOWBALL_HEIGHT) != 0
                    || !"random.bow".equals(launchSound)
                    || Float.compare(launchVolume, 0.5F) != 0
                    || Float.compare(launchPitchNumerator, 0.4F) != 0
                    || Float.compare(launchPitchRandomScale, 0.4F) != 0
                    || Float.compare(launchPitchBase, 0.8F) != 0
                    || !consumeOutsideCreative || !serverAuthoritativeLaunch
                    || variants == null || variants.isEmpty()
                    || !sourceSemanticsComplete || !runtimeRuleReady
                    || !projectileEntityTypeRegistrationWired) {
                throw new IllegalArgumentException("Invalid converted variant-snowball runtime rule");
            }
            variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
        }

        public Variant variant(int metadata) {
            return variants.get(metadata);
        }
    }

    private static final Map<Identifier, Rule> RULES = new ConcurrentHashMap<>();
    private static final Map<Identifier, EntityType<ConvertedLegacyVariantSnowballProjectile>> TYPES =
            new ConcurrentHashMap<>();

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
                    || !bool(root, "projectileEntityTypeRegistrationWired")
                    || bool(root, "itemRuntimeWired")
                    || bool(root, "projectileRuntimeWired")
                    || bool(root, "projectileImpactRuntimeWired")
                    || bool(root, "rendererRuntimeWired")
                    || bool(root, "runtimeImplementationWired")) {
                return;
            }

            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                Rule rule = parse(element.getAsJsonObject());
                if (rule == null || !modId.equals(rule.id().getNamespace())) continue;
                install(rule);
                loaded++;
            }
            if (loaded > 0) {
                LegacyForgeBridge.LOGGER.info(
                        "Loaded converted variant-snowball runtime rules and projectile EntityTypes: "
                                + "mod={}, rules={}", modId, loaded);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to load converted variant-snowball runtime rules for " + modId, exception);
        }
    }

    public static Rule rule(Identifier id) {
        return id == null ? null : RULES.get(id);
    }

    public static EntityType<ConvertedLegacyVariantSnowballProjectile> projectileType(Identifier projectileId) {
        return projectileId == null ? null : TYPES.get(projectileId);
    }

    public static EntityType<ConvertedLegacyVariantSnowballProjectile> projectileTypeForItem(Identifier itemId) {
        Rule rule = rule(itemId);
        return rule == null ? null : projectileType(rule.projectileId());
    }

    public static List<Rule> rules(String namespace) {
        return RULES.values().stream()
                .filter(rule -> namespace.equals(rule.id().getNamespace()))
                .sorted(java.util.Comparator.comparing(rule -> rule.id().toString()))
                .toList();
    }

    static int trackingChunks(int blocks) {
        if (blocks <= 0) throw new IllegalArgumentException("blocks");
        return blocks / 16 + (blocks % 16 == 0 ? 0 : 1);
    }

    static Rule parseForTests(JsonObject value) {
        return parse(value);
    }

    static void registerForTests(Rule rule) {
        registerRule(rule);
    }

    static void clearForTests() {
        RULES.clear();
        TYPES.clear();
    }

    private static synchronized void install(Rule rule) {
        registerRule(rule);
        EntityType<ConvertedLegacyVariantSnowballProjectile> existing = TYPES.get(rule.projectileId());
        if (existing != null) return;
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(rule.projectileId())) {
            throw new IllegalStateException(
                    "Converted variant-snowball projectile id is already registered: " + rule.projectileId());
        }

        EntityType.EntityFactory<ConvertedLegacyVariantSnowballProjectile> factory =
                (type, level) -> new ConvertedLegacyVariantSnowballProjectile(type, level, rule);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rule.projectileId());
        EntityType<ConvertedLegacyVariantSnowballProjectile> created =
                EntityType.Builder.<ConvertedLegacyVariantSnowballProjectile>of(factory, MobCategory.MISC)
                        .sized(rule.width(), rule.height())
                        .clientTrackingRange(rule.modernClientTrackingRangeChunks())
                        .updateInterval(rule.updateFrequency())
                        .build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, created);
        TYPES.put(rule.projectileId(), created);
    }

    private static void registerRule(Rule rule) {
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
                    || !bool(value, "legacyProjectileRegistrationProven")
                    || !bool(value, "projectileEntityTypeRegistrationWired")
                    || bool(value, "itemRuntimeWired")
                    || bool(value, "projectileRuntimeWired")
                    || bool(value, "projectileImpactRuntimeWired")
                    || bool(value, "rendererRuntimeWired")
                    || bool(value, "runtimeImplementationWired")
                    || !"VARIANT_SNOWBALL".equals(string(value, "adapter", null))
                    || !"THROWN_ITEM".equals(string(value, "rendererAdapter", null))
                    || !"MISC".equals(string(value, "mobCategory", null))
                    || !bool(value, "inheritedVanillaSnowballDimensions")
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
                    string(value, "legacyProjectileRegistryName", null),
                    integer(value, "legacyProjectileNumericId", -1),
                    integer(value, "legacyTrackingRangeBlocks", 0),
                    integer(value, "modernClientTrackingRangeChunks", 0),
                    integer(value, "updateFrequency", 0),
                    bool(value, "velocityUpdates"),
                    decimal(value, "width", Float.NaN),
                    decimal(value, "height", Float.NaN),
                    string(value, "launchSound", null),
                    decimal(value, "launchVolume", Float.NaN),
                    decimal(value, "launchPitchNumerator", Float.NaN),
                    decimal(value, "launchPitchRandomScale", Float.NaN),
                    decimal(value, "launchPitchBase", Float.NaN),
                    bool(value, "consumeOutsideCreative"),
                    bool(value, "serverAuthoritativeLaunch"),
                    variants,
                    bool(value, "sourceSemanticsComplete"),
                    bool(value, "runtimeRuleReady"),
                    bool(value, "projectileEntityTypeRegistrationWired"));
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
