package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
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

/** Runtime registry for plant adapters that passed all source/lifecycle/presentation/soil proof gates. */
public final class LegacyPlantRuntimeRegistry {
    public enum Family { CROPS, REED, BUSH }

    public record Rule(Identifier id, Family family, Set<Identifier> survivalBlocks,
                       boolean adjacentWaterRequired, boolean cropFertilityComplete,
                       boolean reedSelfStacking) {
        public Rule {
            if (id == null || family == null || survivalBlocks == null || survivalBlocks.isEmpty())
                throw new IllegalArgumentException("Invalid converted plant runtime rule");
            survivalBlocks = Set.copyOf(survivalBlocks);
            if (family != Family.CROPS && cropFertilityComplete)
                throw new IllegalArgumentException("Only crop rules may carry fertility proof");
            if (family != Family.REED && (adjacentWaterRequired || reedSelfStacking))
                throw new IllegalArgumentException("Only reed rules may carry reed survival flags");
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

    private static final Map<Identifier,Rule> RULES = new ConcurrentHashMap<>();
    private LegacyPlantRuntimeRegistry() { }

    public static void loadMod(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var runtimePath = container.findPath(LegacyPlantRuntimeProofPass.OUTPUT).orElse(null);
        var soilPath = container.findPath(LegacyPlantSoilProofPass.OUTPUT).orElse(null);
        if (runtimePath == null || soilPath == null || !Files.isRegularFile(runtimePath) || !Files.isRegularFile(soilPath)) return;
        try (Reader runtimeReader = Files.newBufferedReader(runtimePath, StandardCharsets.UTF_8);
             Reader soilReader = Files.newBufferedReader(soilPath, StandardCharsets.UTF_8)) {
            JsonObject runtime = JsonParser.parseReader(runtimeReader).getAsJsonObject();
            JsonObject soil = JsonParser.parseReader(soilReader).getAsJsonObject();
            List<Rule> parsed = parseRules(modId, runtime, soil);
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
                && Identifier.parse("minecraft:farmland").equals(belowId) && farmlandMoisture > 0;
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

    static List<Rule> parseRules(String modId, JsonObject runtimeRoot, JsonObject soilRoot) {
        if (modId == null || runtimeRoot == null || soilRoot == null) return List.of();
        String runtimeHash = string(runtimeRoot, "sourceSha256");
        String soilHash = string(soilRoot, "sourceSha256");
        if (runtimeHash == null || !runtimeHash.equals(soilHash)) return List.of();
        Map<String,JsonObject> soils = indexByModernId(soilRoot.getAsJsonArray("proofs"));
        JsonArray runtimeProofs = runtimeRoot.getAsJsonArray("proofs");
        if (runtimeProofs == null) return List.of();
        List<Rule> result = new ArrayList<>();
        for (JsonElement element : runtimeProofs) {
            if (!element.isJsonObject()) continue;
            JsonObject runtime = element.getAsJsonObject();
            if (!bool(runtime, "runtimeProofComplete")) continue;
            String idValue = string(runtime, "modernId");
            String familyValue = string(runtime, "family");
            if (idValue == null || familyValue == null) continue;
            Identifier id;
            Family family;
            try { id = Identifier.parse(idValue); family = Family.valueOf(familyValue.toUpperCase()); }
            catch (RuntimeException invalid) { continue; }
            if (!id.getNamespace().equals(modId)) continue;
            JsonObject soil = soils.get(idValue);
            if (soil == null || !bool(soil, "plantRuntimeProofComplete") || !bool(soil, "survivalSoilProofComplete")) continue;
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
            boolean adjacentWater = bool(soil, "adjacentWaterRequired");
            boolean reedSelf = bool(soil, "convertedReedSelfStackingByVanillaIdentity");
            try { result.add(new Rule(id, family, survivalIds, adjacentWater,
                    family == Family.CROPS && bool(soil, "cropFertilityProofComplete"), reedSelf)); }
            catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(result);
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

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
