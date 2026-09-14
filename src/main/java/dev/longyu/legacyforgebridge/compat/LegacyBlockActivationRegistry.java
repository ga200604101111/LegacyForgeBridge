package dev.longyu.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.LegacyBlockActivationCompiler;
import dev.longyu.legacyforgebridge.convert.pass.LegacyBlockActivationPass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime evaluator for bounded source-proven Minecraft 1.7 block activation decisions. */
public final class LegacyBlockActivationRegistry {
    private static final Map<Identifier, LegacyBlockActivationCompiler.Program> RULES = new ConcurrentHashMap<>();
    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();

    private LegacyBlockActivationRegistry() { }

    /**
     * Returns null when no converted rule exists or when the bounded source program fails. A
     * non-null boolean is the exact legacy onBlockActivated consume/pass decision for the admitted
     * pure subset.
     */
    public static Boolean handled(Identifier blockId, BlockHitResult hitResult) {
        if (blockId == null || hitResult == null) return null;
        LegacyBlockActivationCompiler.Program program = RULES.get(blockId);
        if (program == null) return null;
        try {
            return program.evaluate(LegacyBlockPlacementRegistry.legacySide(hitResult.getDirection()));
        } catch (RuntimeException exception) {
            if (FAILED.add(blockId)) {
                LegacyForgeBridge.LOGGER.error("Converted legacy block activation rule failed closed for {}", blockId, exception);
            }
            return null;
        }
    }

    /** Called by a converted mod after its generated blocks have been registered. */
    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyBlockActivationPass.RULES_PATH);
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
                JsonObject programObject = object.has("program") && object.get("program").isJsonObject()
                        ? object.getAsJsonObject("program") : null;
                if (idValue == null || programObject == null) continue;
                Identifier id = Identifier.parse(idValue);
                if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                    LegacyForgeBridge.LOGGER.warn("Ignoring converted activation rule for unresolved block {}", id);
                    continue;
                }
                RULES.put(id, parseProgram(idValue, programObject));
                FAILED.remove(id);
                loaded++;
            }
            if (loaded > 0) {
                LegacyForgeBridge.LOGGER.info("Loaded converted legacy block activation rules: mod={}, rules={}", modId, loaded);
            }
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted legacy block activation rules for {}", modId, exception);
        }
    }

    static synchronized void installForTests(Identifier id, LegacyBlockActivationCompiler.Program program) {
        RULES.put(id, program);
        FAILED.remove(id);
    }

    static synchronized void clearForTests() {
        RULES.clear();
        FAILED.clear();
    }

    private static LegacyBlockActivationCompiler.Program parseProgram(String idValue, JsonObject object) {
        JsonArray values = object.getAsJsonArray("instructions");
        if (values == null) throw new IllegalArgumentException("Missing block activation instructions");
        List<LegacyBlockActivationCompiler.Instruction> instructions = new ArrayList<>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Malformed block activation instruction");
            JsonObject value = element.getAsJsonObject();
            LegacyBlockActivationCompiler.Op op = LegacyBlockActivationCompiler.Op.valueOf(required(value, "op"));
            instructions.add(new LegacyBlockActivationCompiler.Instruction(
                    op,
                    integer(value, "operand", 0),
                    integer(value, "target", -1),
                    integers(value.getAsJsonArray("keys")),
                    integers(value.getAsJsonArray("targets"))));
        }
        return new LegacyBlockActivationCompiler.Program(
                idValue, "runtime", "runtime", "runtime", "runtime", "runtime", instructions);
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
