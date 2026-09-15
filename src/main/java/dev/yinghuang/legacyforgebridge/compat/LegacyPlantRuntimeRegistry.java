package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlantDropProofPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlantRuntimeProofPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlantSoilProofPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

/** Runtime registry for plant adapters that passed all source/lifecycle/presentation/drop/soil proof gates. */
public final class LegacyPlantRuntimeRegistry {
    public enum Family { CROPS, REED, BUSH }

    public sealed interface UnsupportedDropRule permits SelfBlockDrop, FixedItemDrop, VanillaCropsDrop { }

    public record SelfBlockDrop(int quantity, int legacyDamage) implements UnsupportedDropRule {
        public SelfBlockDrop {
            if (quantity <= 0 || legacyDamage < 0) throw new IllegalArgumentException("Invalid self-block plant drop");
        }
    }

    public record FixedItemDrop(Identifier itemId, int quantity, int legacyDamage) implements UnsupportedDropRule {
        public FixedItemDrop {
            if (itemId == null || quantity <= 0 || legacyDamage < 0)
                throw new IllegalArgumentException("Invalid fixed-item plant drop");
        }
    }

    public record VanillaCropsDrop(Identifier immatureItemId, Identifier matureItemId, int matureMetadata,
                                   Identifier bonusSeedItemId, int bonusSeedTrials, int bonusRandomBound,
                                   int quantity, int legacyDamage) implements UnsupportedDropRule {
        public VanillaCropsDrop {
            if (immatureItemId == null || matureItemId == null || bonusSeedItemId == null
                    || matureMetadata < 0 || matureMetadata > 15 || bonusSeedTrials < 0
                    || bonusRandomBound <= 0 || quantity <= 0 || legacyDamage < 0)
                throw new IllegalArgumentException("Invalid vanilla crops plant drop");
        }
    }

    public record Rule(Identifier id, Family family, Set<Identifier> survivalBlocks,
                       boolean adjacentWaterRequired, boolean cropFertilityComplete,
                       boolean reedSelfStacking, UnsupportedDropRule unsupportedDrop) {
        public Rule {
            if (id == null || family == null || survivalBlocks == null || survivalBlocks.isEmpty() || unsupportedDrop == null)
                throw new IllegalArgumentException("Invalid converted plant runtime rule");
            survivalBlocks = Set.copyOf(survivalBlocks);
            if (family != Family.CROPS && cropFertilityComplete)
                throw new IllegalArgumentException("Only crop rules may carry fertility proof");
            if (family != Family.REED && (adjacentWaterRequired || reedSelfStacking))
                throw new IllegalArgumentException("Only reed rules may carry reed survival flags");
            if (family == Family.CROPS && !(unsupportedDrop instanceof VanillaCropsDrop))
                throw new IllegalArgumentException("Crop runtime requires exact 1.7 crops drop proof");
            if (family == Family.REED && !(unsupportedDrop instanceof FixedItemDrop))
                throw new IllegalArgumentException("Reed runtime requires exact 1.7 fixed-item drop proof");
            if (family == Family.BUSH && !(unsupportedDrop instanceof SelfBlockDrop))
                throw new IllegalArgumentException("Bush runtime requires exact 1.7 self-block drop proof");
        }
    }

    public record SoilSample(Identifier blockId, int farmlandMoisture) {
        public SoilSample {
            if (blockId == null || farmlandMoisture < 0 || farmlandMoisture > 7)
                throw new IllegalArgumentException("Invalid crop soil sample");
        }
    }

    public record ReedTick(int metadata, boolean growAbove) {
        public ReedTick {
            if (metadata < 0 || metadata > 15) throw new IllegalArgumentException("Invalid reed metadata");
        }
    }

    public record DropStack(Identifier itemId, int count, int legacyDamage) {
        public DropStack {
            if (itemId == null || count <= 0 || legacyDamage < 0)
                throw new IllegalArgumentException("Invalid plant drop stack");
        }
    }

    private static final Identifier FARMLAND = Identifier.parse("minecraft:farmland");
    private static final Identifier SUGAR_CANE = Identifier.parse("minecraft:sugar_cane");
    private static final Identifier WHEAT_SEEDS = Identifier.parse("minecraft:wheat_seeds");
    private static final Identifier WHEAT = Identifier.parse("minecraft:wheat");
    private static final Map<Identifier,Rule> RULES = new ConcurrentHashMap<>();
    private LegacyPlantRuntimeRegistry() { }

    public static void loadMod(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var runtimePath = container.findPath(LegacyPlantRuntimeProofPass.OUTPUT).orElse(null);
        var soilPath = container.findPath(LegacyPlantSoilProofPass.OUTPUT).orElse(null);
        var dropPath = container.findPath(LegacyPlantDropProofPass.OUTPUT).orElse(null);
        if (runtimePath == null || soilPath == null || dropPath == null
                || !Files.isRegularFile(runtimePath) || !Files.isRegularFile(soilPath) || !Files.isRegularFile(dropPath)) return;
        try (Reader runtimeReader = Files.newBufferedReader(runtimePath, StandardCharsets.UTF_8);
             Reader soilReader = Files.newBufferedReader(soilPath, StandardCharsets.UTF_8);
             Reader dropReader = Files.newBufferedReader(dropPath, StandardCharsets.UTF_8)) {
            JsonObject runtime = JsonParser.parseReader(runtimeReader).getAsJsonObject();
            JsonObject soil = JsonParser.parseReader(soilReader).getAsJsonObject();
            JsonObject drop = JsonParser.parseReader(dropReader).getAsJsonObject();
            List<Rule> parsed = parseRules(modId, runtime, soil, drop);
            parsed.forEach(LegacyPlantRuntimeRegistry::register);
            if (!parsed.isEmpty()) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted Forge-compatible plant runtime rules: mod={}, blocks={}", modId, parsed.size());
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted plant runtime rules for {}", modId, exception);
        }
    }

    public static boolean hasRule(Identifier id) { return id != null && RULES.containsKey(id); }
    public static Rule rule(Identifier id) { return id == null ? null : RULES.get(id); }

    /** Exact Forge-default plant support once source canSustainPlant overrides have been excluded. */
    public static boolean canSurvive(Rule rule, Identifier belowId, boolean adjacentWater, boolean belowSamePlant) {
        if (rule == null || belowId == null) return false;
        if (rule.family() == Family.REED && belowSamePlant && rule.reedSelfStacking()) return true;
        if (!rule.survivalBlocks().contains(belowId)) return false;
        return !rule.adjacentWaterRequired() || adjacentWater;
    }

    /** Forge 1.7 Block#isFertile default: hydrated farmland only. */
    public static boolean isFertile(Rule rule, Identifier belowId, int farmlandMoisture) {
        return rule != null && rule.family() == Family.CROPS && rule.cropFertilityComplete()
                && FARMLAND.equals(belowId) && farmlandMoisture > 0;
    }

    /**
     * Forge-patched 1.7 BlockCrops growth-rate formula. Samples are row-major 3x3 soil blocks with
     * index 4 directly below the crop. The adjacency booleans describe same-crop blocks at crop Y.
     */
    public static float cropGrowthRate(Rule rule, List<SoilSample> soils,
                                       boolean horizontalSame, boolean verticalSame, boolean diagonalSame) {
        if (rule == null || rule.family() != Family.CROPS || soils == null || soils.size() != 9)
            throw new IllegalArgumentException("Crop growth rate requires one crop rule and 3x3 soil samples");
        float result = 1.0F;
        for (int index = 0; index < 9; index++) {
            SoilSample sample = soils.get(index);
            float contribution = 0.0F;
            if (canSurvive(rule, sample.blockId(), false, false)) {
                contribution = isFertile(rule, sample.blockId(), sample.farmlandMoisture()) ? 3.0F : 1.0F;
            }
            if (index != 4) contribution /= 4.0F;
            result += contribution;
        }
        if (diagonalSame || horizontalSame && verticalSame) result /= 2.0F;
        return result;
    }

    /** Exact 1.7 BlockReed metadata timer transition for one random tick. */
    public static ReedTick reedRandomTick(int metadata, int currentHeight, boolean aboveAir) {
        if (metadata < 0 || metadata > 15 || currentHeight < 1)
            throw new IllegalArgumentException("Invalid reed tick input");
        if (!aboveAir || currentHeight >= 3) return new ReedTick(metadata, false);
        return metadata == 15 ? new ReedTick(0, true) : new ReedTick(metadata + 1, false);
    }

    /** 1.7 BlockCrops IGrowable target check is metadata != 7, including corrupted/overshoot states. */
    public static boolean isCropBonemealTarget(Rule rule, int metadata) {
        if (rule == null || metadata < 0 || metadata > 15)
            throw new IllegalArgumentException("Invalid crop bonemeal target input");
        return rule.family() == Family.CROPS && metadata != 7;
    }

    /** Exact 1.7 BlockCrops bonemeal mutation: metadata + random[2,5], clamped down to 7. */
    public static int cropBonemealMetadata(Rule rule, int metadata, IntUnaryOperator nextInt) {
        if (rule == null || rule.family() != Family.CROPS || metadata < 0 || metadata > 15 || nextInt == null)
            throw new IllegalArgumentException("Invalid crop bonemeal input");
        int roll = nextInt.applyAsInt(4);
        if (roll < 0 || roll >= 4) throw new IllegalArgumentException("Random source returned value outside requested bound");
        return Math.min(7, metadata + 2 + roll);
    }

    /** Exact fortune-0 drop sequence used by 1.7 plant checkAndDropBlock/support-loss removal. */
    public static List<DropStack> unsupportedRemovalDrops(Rule rule, int legacyMetadata, IntUnaryOperator nextInt) {
        if (rule == null || legacyMetadata < 0 || legacyMetadata > 15 || nextInt == null)
            throw new IllegalArgumentException("Invalid plant unsupported-removal drop input");
        UnsupportedDropRule drop = rule.unsupportedDrop();
        if (drop instanceof SelfBlockDrop self) {
            return List.of(new DropStack(rule.id(), self.quantity(), self.legacyDamage()));
        }
        if (drop instanceof FixedItemDrop fixed) {
            return List.of(new DropStack(fixed.itemId(), fixed.quantity(), fixed.legacyDamage()));
        }
        if (drop instanceof VanillaCropsDrop crops) {
            List<DropStack> result = new ArrayList<>();
            Identifier base = legacyMetadata == crops.matureMetadata() ? crops.matureItemId() : crops.immatureItemId();
            result.add(new DropStack(base, crops.quantity(), crops.legacyDamage()));
            if (legacyMetadata >= crops.matureMetadata()) {
                for (int trial = 0; trial < crops.bonusSeedTrials(); trial++) {
                    int roll = nextInt.applyAsInt(crops.bonusRandomBound());
                    if (roll < 0 || roll >= crops.bonusRandomBound())
                        throw new IllegalArgumentException("Random source returned value outside requested bound");
                    if (roll <= legacyMetadata) result.add(new DropStack(crops.bonusSeedItemId(), 1, 0));
                }
            }
            return List.copyOf(result);
        }
        throw new IllegalStateException("Unsupported converted plant drop rule: " + drop.getClass().getName());
    }

    static List<Rule> parseRules(String modId, JsonObject runtimeRoot, JsonObject soilRoot, JsonObject dropRoot) {
        if (modId == null || runtimeRoot == null || soilRoot == null || dropRoot == null) return List.of();
        if (intValue(runtimeRoot, "schemaVersion") != 3 || intValue(soilRoot, "schemaVersion") != 2
                || intValue(dropRoot, "schemaVersion") != 1) return List.of();
        String runtimeHash = string(runtimeRoot, "sourceSha256");
        String soilHash = string(soilRoot, "sourceSha256");
        String dropHash = string(dropRoot, "sourceSha256");
        if (runtimeHash == null || !runtimeHash.equals(soilHash) || !runtimeHash.equals(dropHash)) return List.of();
        Map<String,JsonObject> soils = indexByModernId(soilRoot.getAsJsonArray("proofs"));
        Map<String,JsonObject> drops = indexByModernId(dropRoot.getAsJsonArray("rules"));
        JsonArray runtimeProofs = runtimeRoot.getAsJsonArray("proofs");
        if (runtimeProofs == null) return List.of();
        List<Rule> result = new ArrayList<>();
        for (JsonElement element : runtimeProofs) {
            if (!element.isJsonObject()) continue;
            JsonObject runtime = element.getAsJsonObject();
            if (!bool(runtime, "runtimeProofComplete") || !bool(runtime, "unsupportedRemovalDropProofComplete")) continue;
            String idValue = string(runtime, "modernId");
            String familyValue = string(runtime, "family");
            if (idValue == null || familyValue == null) continue;
            Identifier id;
            Family family;
            try { id = Identifier.parse(idValue); family = Family.valueOf(familyValue.toUpperCase()); }
            catch (RuntimeException invalid) { continue; }
            if (!id.getNamespace().equals(modId)) continue;
            JsonObject soil = soils.get(idValue);
            JsonObject dropProof = drops.get(idValue);
            if (soil == null || dropProof == null || !bool(dropProof, "unsupportedRemovalDropProofComplete")) continue;
            if (!bool(soil, "plantRuntimeProofComplete") || !bool(soil, "survivalSoilProofComplete")) continue;
            if (family == Family.CROPS && !bool(soil, "cropFertilityProofComplete")) continue;
            JsonArray survival = soil.getAsJsonArray("forgeDefaultSurvivalBlocks");
            if (survival == null) continue;
            Set<Identifier> survivalIds = new LinkedHashSet<>();
            boolean invalid = false;
            for (JsonElement block : survival) {
                try { survivalIds.add(Identifier.parse(block.getAsString())); }
                catch (RuntimeException exception) { invalid = true; break; }
            }
            if (invalid || survivalIds.isEmpty()) continue;
            UnsupportedDropRule dropRule = parseDropRule(id, family, dropProof.getAsJsonObject("unsupportedRemovalDrop"));
            if (dropRule == null) continue;
            boolean adjacentWater = bool(soil, "adjacentWaterRequired");
            boolean reedSelf = bool(soil, "convertedReedSelfStackingByVanillaIdentity");
            try { result.add(new Rule(id, family, survivalIds, adjacentWater,
                    family == Family.CROPS && bool(soil, "cropFertilityProofComplete"), reedSelf, dropRule)); }
            catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(result);
    }

    private static UnsupportedDropRule parseDropRule(Identifier plantId, Family family, JsonObject drop) {
        if (drop == null) return null;
        String kind = string(drop, "kind");
        int quantity = intValue(drop, "quantity");
        int damage = intValue(drop, "legacyDamage");
        if (quantity != 1 || damage != 0) return null;
        try {
            return switch (family) {
                case BUSH -> {
                    Identifier item = parseIdentifier(drop, "itemId");
                    if (!"self_block_1_7_10".equals(kind) || !plantId.equals(item)) yield null;
                    yield new SelfBlockDrop(quantity, damage);
                }
                case REED -> {
                    Identifier item = parseIdentifier(drop, "itemId");
                    if (!"fixed_item_1_7_10".equals(kind) || !SUGAR_CANE.equals(item)) yield null;
                    yield new FixedItemDrop(item, quantity, damage);
                }
                case CROPS -> {
                    Identifier immature = parseIdentifier(drop, "immatureItemId");
                    Identifier mature = parseIdentifier(drop, "matureItemId");
                    Identifier bonus = parseIdentifier(drop, "bonusSeedItemId");
                    int matureMeta = intValue(drop, "matureMetadata");
                    int trials = intValue(drop, "bonusSeedTrials");
                    int bound = intValue(drop, "bonusRandomBound");
                    int fortune = intValue(drop, "fortune");
                    if (!"vanilla_crops_1_7_10".equals(kind) || !WHEAT_SEEDS.equals(immature)
                            || !WHEAT.equals(mature) || !WHEAT_SEEDS.equals(bonus)
                            || matureMeta != 7 || trials != 3 || bound != 15 || fortune != 0) yield null;
                    yield new VanillaCropsDrop(immature, mature, matureMeta, bonus, trials, bound, quantity, damage);
                }
            };
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Map<String,JsonObject> indexByModernId(JsonArray values) {
        if (values == null) return Map.of();
        Map<String,JsonObject> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String id = string(value, "modernId");
            if (id == null || ambiguous.contains(id)) continue;
            if (result.putIfAbsent(id, value) != null) { result.remove(id); ambiguous.add(id); }
        }
        return result;
    }

    static void registerForTests(Rule rule) { register(rule); }
    static void clearForTests() { RULES.clear(); }

    private static void register(Rule rule) {
        Rule previous = RULES.putIfAbsent(rule.id(), rule);
        if (previous != null && !previous.equals(rule))
            throw new IllegalStateException("Conflicting converted plant runtime rule for " + rule.id());
    }

    private static Identifier parseIdentifier(JsonObject object, String key) {
        String value = string(object, key);
        return value == null ? null : Identifier.parse(value);
    }

    private static int intValue(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return Integer.MIN_VALUE;
        return value.getAsInt();
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
