package dev.longyu.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.LegacyStoragePresentationAnalyzer;
import dev.longyu.legacyforgebridge.convert.pass.LegacyStorageBlockPass;
import dev.longyu.legacyforgebridge.convert.runtime.ConvertedLegacyStorageBlockEntity;
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

/** Runtime rules for source-proven six-row 1.7 BlockContainer/IInventory storage blocks. */
public final class LegacyStorageBlockRegistry {
    private static final Map<Identifier, Rule> RULES = new ConcurrentHashMap<>();
    private static final Map<Identifier, BlockEntityType<ConvertedLegacyStorageBlockEntity>> TYPES = new ConcurrentHashMap<>();

    private LegacyStorageBlockRegistry() { }

    public record Rule(Identifier id, int slots, int rows, int stackLimit, String title,
                       double interactionDistanceSq, boolean sneakingPass,
                       boolean dropContents, boolean comparator,
                       boolean presentationComplete, String orientation) {
        public Rule {
            if (id == null || slots != 54 || rows != 6 || stackLimit < 1 || stackLimit > 64
                    || title == null || title.isBlank() || interactionDistanceSq <= 0.0
                    || interactionDistanceSq > 4096.0) {
                throw new IllegalArgumentException("Invalid converted storage rule");
            }
            orientation = orientation == null ? "" : orientation;
            if (presentationComplete && !orientation.equals(
                    LegacyStoragePresentationAnalyzer.ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT)) {
                throw new IllegalArgumentException("Unsupported converted storage orientation: " + orientation);
            }
        }
    }

    /** Must run before generated blocks are registered because registerBlock chooses the block class from this table. */
    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyStorageBlockPass.OUTPUT);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                Rule rule = parse(element.getAsJsonObject());
                if (rule == null || !rule.id().getNamespace().equals(modId)) continue;
                Rule previous = RULES.putIfAbsent(rule.id(), rule);
                if (previous != null && !previous.equals(rule)) {
                    throw new IllegalStateException("Conflicting converted storage rule for " + rule.id());
                }
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info("Loaded converted storage rules: mod={}, rules={}", modId, loaded);
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted storage rules for {}", modId, exception);
        }
    }

    public static boolean hasRule(Identifier id) {
        return id != null && RULES.containsKey(id);
    }

    public static Rule rule(Identifier id) {
        return id == null ? null : RULES.get(id);
    }

    public static Rule requireRule(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        Rule rule = RULES.get(id);
        if (rule == null) throw new IllegalStateException("No converted storage rule registered for block " + id);
        return rule;
    }

    public static synchronized void registerType(Identifier id, Block block) {
        Rule rule = RULES.get(id);
        if (rule == null || TYPES.containsKey(id)) return;
        if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
            @SuppressWarnings("unchecked")
            BlockEntityType<ConvertedLegacyStorageBlockEntity> existing =
                    (BlockEntityType<ConvertedLegacyStorageBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id, existing);
            return;
        }
        BlockEntityType<ConvertedLegacyStorageBlockEntity> type =
                FabricBlockEntityTypeBuilder.create(ConvertedLegacyStorageBlockEntity::new, block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
        TYPES.put(id, type);
    }

    public static BlockEntityType<ConvertedLegacyStorageBlockEntity> requireType(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        BlockEntityType<ConvertedLegacyStorageBlockEntity> type = TYPES.get(id);
        if (type == null) throw new IllegalStateException("No converted storage BlockEntityType registered for " + id);
        return type;
    }

    static synchronized void clearForTests() {
        RULES.clear();
        TYPES.clear();
    }

    private static Rule parse(JsonObject value) {
        try {
            String idValue = string(value, "id");
            String title = string(value, "title");
            if (idValue == null || title == null) return null;
            return new Rule(
                    Identifier.parse(idValue),
                    integer(value, "slots", 0),
                    integer(value, "rows", 0),
                    integer(value, "stackLimit", 0),
                    title,
                    decimal(value, "interactionDistanceSq", 0.0),
                    bool(value, "sneakingPass"),
                    bool(value, "dropContents"),
                    bool(value, "comparator"),
                    bool(value, "presentationComplete"),
                    string(value, "orientation")
            );
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static double decimal(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
