package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlantPlacementProofPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Runtime registry for source-proven inherited ItemSeeds / ItemSeedFood placement. */
public final class LegacyPlantPlacementRegistry {
    public enum Adapter { SEEDS, SEED_FOOD, REED }

    public record Rule(Identifier itemId, Adapter adapter, Identifier targetBlockId, Identifier soilBlockId,
                       Integer nutrition, Float saturationModifier) {
        public Rule {
            if (itemId == null || adapter == null || targetBlockId == null)
                throw new IllegalArgumentException("Invalid plant placement runtime rule");
            if (adapter != Adapter.REED && soilBlockId == null)
                throw new IllegalArgumentException("Seed placement requires a proven soil block");
            if (adapter == Adapter.REED && soilBlockId != null)
                throw new IllegalArgumentException("ItemReed placement must not invent a constructor soil block");
            if (adapter == Adapter.SEED_FOOD) {
                if (nutrition == null || nutrition < 0 || saturationModifier == null
                        || !Float.isFinite(saturationModifier) || saturationModifier < 0F)
                    throw new IllegalArgumentException("ItemSeedFood requires proven food properties");
            } else if (nutrition != null || saturationModifier != null) {
                throw new IllegalArgumentException("Only ItemSeedFood may carry food properties");
            }
        }
    }

    private static final Map<Identifier,Rule> RULES = new ConcurrentHashMap<>();
    private static final Set<Identifier> CROP_TARGETS = ConcurrentHashMap.newKeySet();

    private LegacyPlantPlacementRegistry() { }

    public static void loadMod(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyPlantPlacementProofPass.OUTPUT).orElse(null);
        if (path == null || !Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Rule> parsed = parseRules(modId, root, LegacyPlantRuntimeRegistry::rule);
            parsed.forEach(LegacyPlantPlacementRegistry::register);
            if (!parsed.isEmpty()) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted plant placement runtime: mod={}, seedItems={}, seedFoodItems={}, cropTargets={}",
                    modId,
                    parsed.stream().filter(rule -> rule.adapter() == Adapter.SEEDS).count(),
                    parsed.stream().filter(rule -> rule.adapter() == Adapter.SEED_FOOD).count(),
                    parsed.stream().map(Rule::targetBlockId).distinct().count());
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted plant placement runtime for {}", modId, exception);
        }
    }

    public static Rule rule(Identifier itemId) { return itemId == null ? null : RULES.get(itemId); }
    public static boolean hasSeedRuntimeRule(Identifier itemId) { return rule(itemId) != null; }
    public static boolean cropTargetRuntimeReady(Identifier blockId) { return blockId != null && CROP_TARGETS.contains(blockId); }

    public static FoodProperties foodProperties(Rule rule) {
        if (rule == null || rule.adapter() != Adapter.SEED_FOOD)
            throw new IllegalArgumentException("Missing ItemSeedFood runtime rule");
        return new FoodProperties.Builder().nutrition(rule.nutrition())
                .saturationModifier(rule.saturationModifier()).build();
    }

    /** Pure exact ItemSeeds/ItemSeedFood precondition evaluator. */
    public static boolean canPlantSeed(Rule rule, Direction clickedFace, Identifier clickedBlockId,
                                       boolean aboveAir, boolean canEditClicked, boolean canEditAbove) {
        return rule != null && isSeedAdapter(rule.adapter())
                && clickedFace == Direction.UP
                && rule.soilBlockId().equals(clickedBlockId)
                && aboveAir && canEditClicked && canEditAbove;
    }

    static List<Rule> parseRules(String modId, JsonObject root,
                                 Function<Identifier,LegacyPlantRuntimeRegistry.Rule> targetResolver) {
        if (modId == null || root == null || targetResolver == null) return List.of();
        if (intValue(root, "schemaVersion") != 2 || !bool(root, "sourceProofsAligned")) return List.of();
        JsonArray rules = root.getAsJsonArray("rules");
        if (rules == null) return List.of();
        List<Rule> result = new ArrayList<>();
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            if (!bool(value, "runtimeComplete") || !bool(value, "placementProofComplete")
                    || !bool(value, "topologyProofComplete") || !bool(value, "inheritedVanillaPlacement")
                    || !bool(value, "targetProofComplete") || !bool(value, "targetPlacementCallbacksInherited")
                    || !bool(value, "soilPlacementProofComplete")) continue;
            String itemValue = string(value, "id");
            String targetValue = string(value, "targetBlockId");
            String family = string(value, "family");
            String adapterValue = string(value, "placementAdapter");
            if (itemValue == null || targetValue == null || family == null || adapterValue == null) continue;
            Identifier itemId, targetId, soilId;
            Adapter adapter;
            Integer nutrition = null;
            Float saturation = null;
            try {
                itemId = Identifier.parse(itemValue);
                targetId = Identifier.parse(targetValue);
                adapter = parseAdapter(adapterValue);
                if (adapter == Adapter.REED) continue; // .35 deliberately does not materialize ItemReed.
                String soilValue = string(value, "soilBlockId");
                if (soilValue == null) continue;
                soilId = Identifier.parse(soilValue);
                if (adapter == Adapter.SEED_FOOD) {
                    if (!bool(value, "seedFoodPropertiesProofComplete")) continue;
                    nutrition = integer(value, "nutrition");
                    saturation = floating(value, "saturationModifier");
                }
            } catch (RuntimeException invalid) { continue; }
            if (!modId.equals(itemId.getNamespace()) || !familyMatches(family, adapter)) continue;
            LegacyPlantRuntimeRegistry.Rule plant = targetResolver.apply(targetId);
            if (plant == null || plant.family() != LegacyPlantRuntimeRegistry.Family.CROPS) continue;
            try { result.add(new Rule(itemId, adapter, targetId, soilId, nutrition, saturation)); }
            catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(result);
    }

    static void registerForTests(Rule rule) { register(rule); }
    static void clearForTests() { RULES.clear(); CROP_TARGETS.clear(); }

    private static void register(Rule rule) {
        Rule previous = RULES.putIfAbsent(rule.itemId(), rule);
        if (previous != null && !previous.equals(rule))
            throw new IllegalStateException("Conflicting converted plant placement rule for " + rule.itemId());
        CROP_TARGETS.add(rule.targetBlockId());
    }

    private static Adapter parseAdapter(String value) {
        return switch (value) {
            case "item_seeds_1_7_10" -> Adapter.SEEDS;
            case "item_seed_food_1_7_10" -> Adapter.SEED_FOOD;
            case "item_reed_1_7_10" -> Adapter.REED;
            default -> throw new IllegalArgumentException("Unknown plant placement adapter " + value);
        };
    }
    private static boolean familyMatches(String family, Adapter adapter) {
        return switch (adapter) {
            case SEEDS -> "seeds".equals(family);
            case SEED_FOOD -> "seed_food".equals(family);
            case REED -> "reed".equals(family);
        };
    }
    private static boolean isSeedAdapter(Adapter adapter) { return adapter == Adapter.SEEDS || adapter == Adapter.SEED_FOOD; }
    private static int intValue(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : Integer.MIN_VALUE;
    }
    private static Integer integer(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : null;
    }
    private static Float floating(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : null;
    }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
