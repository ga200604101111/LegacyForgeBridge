package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime lookup for source-proven legacy ItemFood semantics owned by a converted mod. */
public final class LegacyFoodItemRegistry {
    public static final String RULES_PATH = "legacyforgebridge/food-item-rules.json";
    private static final Map<Identifier,Rule> RULES = new ConcurrentHashMap<>();
    private LegacyFoodItemRegistry() { }

    public record Rule(Identifier id, int nutrition, float saturationModifier, boolean alwaysEdible) {
        public Rule {
            if (id == null || nutrition < 0 || !Float.isFinite(saturationModifier) || saturationModifier < 0F) {
                throw new IllegalArgumentException("Invalid converted food rule");
            }
        }
    }

    public static void loadMod(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(RULES_PATH).orElse(null);
        if (path == null || !Files.isRegularFile(path)) return;
        int loaded = 0;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            var rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            for (var element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!value.has("runtimeComplete") || !value.get("runtimeComplete").getAsBoolean()) continue;
                Identifier id = Identifier.parse(value.get("id").getAsString());
                if (!id.getNamespace().equals(modId)) continue;
                Rule rule = new Rule(id, value.get("nutrition").getAsInt(),
                        value.get("saturationModifier").getAsFloat(), value.get("alwaysEdible").getAsBoolean());
                register(rule);
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted legacy food rules: mod={}, items={}", modId, loaded);
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted legacy food rules for {}", modId, exception);
        }
    }

    public static Rule rule(Identifier id) { return id == null ? null : RULES.get(id); }

    public static FoodProperties foodProperties(Rule rule) {
        if (rule == null) throw new IllegalArgumentException("Missing converted food rule");
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(rule.nutrition())
                .saturationModifier(rule.saturationModifier());
        if (rule.alwaysEdible()) builder.alwaysEdible();
        return builder.build();
    }

    static void registerForTests(Rule rule) { register(rule); }
    static void clearForTests() { RULES.clear(); }

    private static void register(Rule rule) {
        Rule previous = RULES.putIfAbsent(rule.id(), rule);
        if (previous != null && !previous.equals(rule)) {
            throw new IllegalStateException("Conflicting converted food rule for " + rule.id());
        }
    }
}
