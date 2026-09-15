package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockPlacementCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyPureIntFunctionCompiler;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockPlacementPass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime evaluator for source-proven 1.7 ItemBlock -> Block placement metadata pipelines. */
public final class LegacyBlockPlacementRegistry {
    private static final Map<Identifier, Rule> RULES = new ConcurrentHashMap<>();
    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();

    private LegacyBlockPlacementRegistry() { }

    public record Rule(
            LegacyPureIntFunctionCompiler.Program itemMetadata,
            LegacyBlockPlacementCompiler.Program blockPlacement
    ) { }

    /**
     * Returns null when no converted source rule exists, allowing ordinary modern placement to
     * continue. Source VM failures also fail closed to the base modern state rather than crashing
     * the placement path.
     */
    public static Integer placementMeta(Identifier blockId, BlockPlaceContext context) {
        if (blockId == null || context == null) return null;
        Rule rule = RULES.get(blockId);
        if (rule == null) return null;
        try {
            int rawMeta = LegacyStackComponents.get(context.getItemInHand());
            int itemMeta = rule.itemMetadata().evaluate(rawMeta);
            if (itemMeta < 0 || itemMeta > 15) {
                throw new IllegalStateException("ItemBlock metadata outside 1.7 range: " + itemMeta);
            }

            Direction clickedFace = context.getClickedFace();
            int side = legacySide(clickedFace);
            BlockPos placementPos = context.getClickedPos();
            BlockPos clickedPos = context.replacingClickedOnBlock()
                    ? placementPos
                    : placementPos.relative(clickedFace.getOpposite());
            Vec3 hit = context.getClickLocation();
            float hitX = (float) (hit.x - clickedPos.getX());
            float hitY = (float) (hit.y - clickedPos.getY());
            float hitZ = (float) (hit.z - clickedPos.getZ());
            return rule.blockPlacement().evaluate(side, hitX, hitY, hitZ, itemMeta);
        } catch (RuntimeException exception) {
            if (FAILED.add(blockId)) {
                LegacyForgeBridge.LOGGER.error("Converted legacy block placement rule failed closed for {}", blockId, exception);
            }
            return null;
        }
    }

    /** Called by a converted mod after its generated blocks/items have been registered. */
    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyBlockPlacementPass.RULES_PATH);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String idValue = string(object, "id");
                JsonObject itemMetadata = object.has("itemMetadata") && object.get("itemMetadata").isJsonObject()
                        ? object.getAsJsonObject("itemMetadata") : null;
                JsonObject blockPlacement = object.has("blockPlacement") && object.get("blockPlacement").isJsonObject()
                        ? object.getAsJsonObject("blockPlacement") : null;
                if (idValue == null || itemMetadata == null || blockPlacement == null) continue;
                Identifier id = Identifier.parse(idValue);
                if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                    LegacyForgeBridge.LOGGER.warn("Ignoring converted placement rule for unresolved block {}", id);
                    continue;
                }
                Rule rule = new Rule(parseIntProgram(itemMetadata), parsePlacementProgram(idValue, blockPlacement));
                RULES.put(id, rule);
                FAILED.remove(id);
                loaded++;
            }
            if (loaded > 0) {
                LegacyForgeBridge.LOGGER.info("Loaded converted legacy block placement rules: mod={}, rules={}", modId, loaded);
            }
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted legacy block placement rules for {}", modId, exception);
        }
    }

    static synchronized void installForTests(Identifier id, Rule rule) {
        RULES.put(id, rule);
        FAILED.remove(id);
    }

    static synchronized void clearForTests() {
        RULES.clear();
        FAILED.clear();
    }

    static int legacySide(Direction direction) {
        return switch (direction) {
            case DOWN -> 0;
            case UP -> 1;
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case EAST -> 5;
        };
    }

    private static LegacyPureIntFunctionCompiler.Program parseIntProgram(JsonObject object) {
        int inputLocal = integer(object, "inputLocal", 1);
        List<LegacyPureIntFunctionCompiler.Instruction> instructions = new ArrayList<>();
        JsonArray values = object.getAsJsonArray("instructions");
        if (values == null) throw new IllegalArgumentException("Missing ItemBlock metadata instructions");
        for (JsonElement element : values) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Malformed ItemBlock metadata instruction");
            JsonObject value = element.getAsJsonObject();
            LegacyPureIntFunctionCompiler.Op op = LegacyPureIntFunctionCompiler.Op.valueOf(required(value, "op"));
            instructions.add(new LegacyPureIntFunctionCompiler.Instruction(
                    op,
                    integer(value, "operand", 0),
                    (int) number(value, "number", 0),
                    integer(value, "target", -1),
                    integers(value.getAsJsonArray("keys")),
                    integers(value.getAsJsonArray("targets"))));
        }
        return new LegacyPureIntFunctionCompiler.Program(inputLocal, instructions);
    }

    private static LegacyBlockPlacementCompiler.Program parsePlacementProgram(String idValue, JsonObject object) {
        List<LegacyBlockPlacementCompiler.Instruction> instructions = new ArrayList<>();
        JsonArray values = object.getAsJsonArray("instructions");
        if (values == null) throw new IllegalArgumentException("Missing block placement instructions");
        for (JsonElement element : values) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Malformed block placement instruction");
            JsonObject value = element.getAsJsonObject();
            LegacyBlockPlacementCompiler.Op op = LegacyBlockPlacementCompiler.Op.valueOf(required(value, "op"));
            instructions.add(new LegacyBlockPlacementCompiler.Instruction(
                    op,
                    integer(value, "operand", 0),
                    number(value, "number", 0),
                    integer(value, "target", -1),
                    integers(value.getAsJsonArray("keys")),
                    integers(value.getAsJsonArray("targets"))));
        }
        return new LegacyBlockPlacementCompiler.Program(idValue, "runtime", "runtime", "runtime", "runtime", "runtime", instructions);
    }

    private static String required(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }

    private static List<Integer> integers(JsonArray array) {
        if (array == null) return List.of();
        List<Integer> output = new ArrayList<>();
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Non-integer branch target");
            output.add(value.getAsInt());
        }
        return List.copyOf(output);
    }
}
