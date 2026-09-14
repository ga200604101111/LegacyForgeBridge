package dev.longyu.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.pass.LegacyFuelHandlerPass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ordered stack-aware fuel rules reconstructed from Forge 1.7 IFuelHandler callbacks. */
public final class LegacyFuelRegistry {
    public static final int ANY = -1;
    private static final Map<Identifier, List<Rule>> RULES = new ConcurrentHashMap<>();

    private LegacyFuelRegistry() { }

    public record Rule(int legacyMeta, int damage, int burnTicks) {
        public Rule {
            if (legacyMeta < ANY || damage < ANY || burnTicks <= 0) throw new IllegalArgumentException();
        }
    }

    public static synchronized void register(String idValue, int legacyMeta, int damage, int burnTicks) {
        Identifier id = Identifier.parse(idValue);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            LegacyForgeBridge.LOGGER.warn("Ignoring converted fuel rule for unresolved item {}", id);
            return;
        }
        RULES.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Rule(legacyMeta, damage, burnTicks));
    }

    /** Returns null when no legacy handler matched, allowing vanilla/Fabric fuel lookup to continue. */
    public static Integer burnDuration(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<Rule> rules = RULES.get(id);
        if (rules == null || rules.isEmpty()) return null;
        int legacyMeta = LegacyStackComponents.get(stack);
        Integer damageComponent = stack.get(DataComponents.DAMAGE);
        int damage = damageComponent == null ? 0 : damageComponent;
        for (Rule rule : rules) {
            if (rule.legacyMeta() != ANY && rule.legacyMeta() != legacyMeta) continue;
            if (rule.damage() != ANY && rule.damage() != damage) continue;
            return rule.burnTicks();
        }
        return null;
    }

    /** Called by a converted mod after its generated items/blocks have been registered. */
    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyFuelHandlerPass.RULES_PATH);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject rule = element.getAsJsonObject();
                String id = string(rule, "id");
                if (id == null) continue;
                int legacyMeta = integer(rule, "legacyMeta", ANY);
                int damage = integer(rule, "damage", ANY);
                int burnTicks = integer(rule, "burnTicks", 0);
                if (burnTicks <= 0) continue;
                register(id, legacyMeta, damage, burnTicks);
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info("Loaded converted legacy fuel rules: mod={}, rules={}", modId, loaded);
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted fuel rules for {}", modId, exception);
        }
    }

    static synchronized void clearForTests() {
        RULES.clear();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
}
