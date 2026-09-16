package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySeatBedPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatBedBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime rules for source-proven two-part legacy beds with a transient failed-sleep seat fallback. */
public final class LegacySeatBedRegistry {
    private static final Map<Identifier, Rule> BY_BLOCK = new ConcurrentHashMap<>();
    private static final Map<Identifier, Rule> BY_ITEM = new ConcurrentHashMap<>();
    private static final Map<Identifier, BlockEntityType<ConvertedLegacySeatBedBlockEntity>> TYPES = new ConcurrentHashMap<>();

    private LegacySeatBedRegistry() { }

    public record Rule(Identifier id, Identifier placementItemId, float blockHeight,
                       boolean twoPartPlacementProven, boolean footOnlyTileProven,
                       boolean sleepFallbackToSeatProven, boolean transientOccupancyProven,
                       boolean seatLifecycleProven, boolean timeAccelerationSourceProven,
                       boolean specialPresentationRequired,
                       boolean twoPartPlacementRuntimeComplete, boolean sleepSeatRuntimeComplete,
                       boolean seatEntityRuntimeComplete, boolean timeAccelerationRuntimeComplete,
                       boolean presentationRuntimeComplete) {
        public Rule {
            if (id == null || placementItemId == null || id.equals(placementItemId)
                    || !(blockHeight > 0F && blockHeight <= 1F)
                    || !twoPartPlacementProven || !footOnlyTileProven || !sleepFallbackToSeatProven
                    || !transientOccupancyProven || !seatLifecycleProven
                    || !twoPartPlacementRuntimeComplete || !sleepSeatRuntimeComplete || !seatEntityRuntimeComplete) {
                throw new IllegalArgumentException("Invalid converted seat-bed core rule");
            }
            if (timeAccelerationRuntimeComplete || presentationRuntimeComplete) {
                throw new IllegalArgumentException("Seat-bed time/presentation runtime is not admitted by schema 2");
            }
        }

        public boolean coreRuntimeComplete() {
            return twoPartPlacementRuntimeComplete && sleepSeatRuntimeComplete && seatEntityRuntimeComplete;
        }
    }

    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacySeatBedPass.OUTPUT);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", 0) != 2) return;
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!bool(value, "coreRuntimeComplete")) continue;
                Rule rule = parse(value);
                if (rule == null || !modId.equals(rule.id().getNamespace())
                        || !modId.equals(rule.placementItemId().getNamespace())) continue;
                Rule previousBlock = BY_BLOCK.putIfAbsent(rule.id(), rule);
                if (previousBlock != null && !previousBlock.equals(rule)) {
                    throw new IllegalStateException("Conflicting converted seat-bed rule for " + rule.id());
                }
                Rule previousItem = BY_ITEM.putIfAbsent(rule.placementItemId(), rule);
                if (previousItem != null && !previousItem.equals(rule)) {
                    throw new IllegalStateException("Conflicting converted seat-bed placement item for " + rule.placementItemId());
                }
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted seat-bed core rules: mod={}, rules={}", modId, loaded);
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted seat-bed rules for {}", modId, exception);
        }
    }

    public static boolean hasBlockRule(Identifier id) { return id != null && BY_BLOCK.containsKey(id); }
    public static Rule blockRule(Identifier id) { return id == null ? null : BY_BLOCK.get(id); }
    public static Rule placementItemRule(Identifier id) { return id == null ? null : BY_ITEM.get(id); }

    public static Rule requireRule(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        Rule rule = BY_BLOCK.get(id);
        if (rule == null) throw new IllegalStateException("No converted seat-bed rule registered for block " + id);
        return rule;
    }

    public static synchronized void registerType(Identifier id, Block block) {
        Rule rule = BY_BLOCK.get(id);
        if (rule == null || TYPES.containsKey(id)) return;
        if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
            @SuppressWarnings("unchecked")
            BlockEntityType<ConvertedLegacySeatBedBlockEntity> existing =
                    (BlockEntityType<ConvertedLegacySeatBedBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id, existing);
            return;
        }
        BlockEntityType<ConvertedLegacySeatBedBlockEntity> type =
                FabricBlockEntityTypeBuilder.create(ConvertedLegacySeatBedBlockEntity::new, block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
        TYPES.put(id, type);
    }

    public static BlockEntityType<ConvertedLegacySeatBedBlockEntity> requireType(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        BlockEntityType<ConvertedLegacySeatBedBlockEntity> type = TYPES.get(id);
        if (type == null) throw new IllegalStateException("No converted seat-bed BlockEntityType registered for " + id);
        return type;
    }

    static synchronized void clearForTests() {
        BY_BLOCK.clear(); BY_ITEM.clear(); TYPES.clear();
    }

    static Rule parseForTests(JsonObject value) { return parse(value); }

    private static Rule parse(JsonObject value) {
        try {
            String id = string(value, "id"), item = string(value, "placementItemId");
            if (id == null || item == null) return null;
            return new Rule(Identifier.parse(id), Identifier.parse(item), decimal(value, "blockHeight", 0F),
                    bool(value, "twoPartPlacementProven"), bool(value, "footOnlyTileProven"),
                    bool(value, "sleepFallbackToSeatProven"), bool(value, "transientOccupancyProven"),
                    bool(value, "seatLifecycleProven"), bool(value, "timeAccelerationSourceProven"),
                    bool(value, "specialPresentationRequired"),
                    bool(value, "twoPartPlacementRuntimeComplete"), bool(value, "sleepSeatRuntimeComplete"),
                    bool(value, "seatEntityRuntimeComplete"), bool(value, "timeAccelerationRuntimeComplete"),
                    bool(value, "presentationRuntimeComplete"));
        } catch (RuntimeException invalid) { return null; }
    }

    private static String string(JsonObject object, String key) { JsonElement v=object.get(key); return v!=null&&v.isJsonPrimitive()?v.getAsString():null; }
    private static int integer(JsonObject object, String key, int fallback) { JsonElement v=object.get(key); return v!=null&&v.isJsonPrimitive()?v.getAsInt():fallback; }
    private static float decimal(JsonObject object, String key, float fallback) { JsonElement v=object.get(key); return v!=null&&v.isJsonPrimitive()?v.getAsFloat():fallback; }
    private static boolean bool(JsonObject object, String key) { JsonElement v=object.get(key); return v!=null&&v.isJsonPrimitive()&&v.getAsBoolean(); }
}
