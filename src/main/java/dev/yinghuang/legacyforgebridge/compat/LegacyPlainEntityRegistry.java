package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityRuntimeCandidatePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityRuntimePass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime registry for proof-complete generated plain legacy Entity subclasses. */
public final class LegacyPlainEntityRegistry {
    private static final Map<Identifier,Rule> RULES = new ConcurrentHashMap<>();
    private static final Map<Identifier,EntityType<? extends Entity>> TYPES = new ConcurrentHashMap<>();
    private static final Map<Identifier,Constructor<? extends Entity>> CONSTRUCTORS = new ConcurrentHashMap<>();
    private static final Map<RemoteKey,Rule> REMOTE_RULES = new ConcurrentHashMap<>();

    private LegacyPlainEntityRegistry() { }

    private record RemoteKey(String legacyModId, int legacyModEntityTypeId) {
        private RemoteKey {
            if (legacyModId == null || legacyModId.isBlank() || legacyModEntityTypeId < 0)
                throw new IllegalArgumentException("Invalid legacy remote Entity identity");
        }
    }

    public record Rule(Identifier id, String generatedClass,
                       String legacyModId, int legacyModEntityTypeId,
                       float width, float height,
                       int legacyTrackingRangeBlocks, int modernClientTrackingRangeChunks,
                       int updateFrequency, boolean velocityUpdates, String presentationAdapter,
                       boolean entityTypeRegistrationWired, boolean clientRendererRegistrationWired,
                       boolean legacyWatcherBridgeWired, boolean remoteEntitySpawnRuntimeComplete,
                       boolean runtimeImplementationWired, boolean runtimeComplete) {
        public Rule {
            if (id == null || generatedClass == null || generatedClass.isBlank()
                    || !(width > 0F) || !(height > 0F) || !Float.isFinite(width) || !Float.isFinite(height)
                    || legacyTrackingRangeBlocks <= 0 || modernClientTrackingRangeChunks <= 0
                    || updateFrequency <= 0 || !velocityUpdates
                    || !LegacyPlainEntityRuntimeCandidatePass.PRESENTATION_ADAPTER_NOOP.equals(presentationAdapter)
                    || !entityTypeRegistrationWired || !clientRendererRegistrationWired
                    || !runtimeImplementationWired || !runtimeComplete) {
                throw new IllegalArgumentException("Invalid plain Entity runtime rule");
            }
            if (remoteEntitySpawnRuntimeComplete
                    && (!legacyWatcherBridgeWired || legacyModId == null || legacyModId.isBlank()
                    || legacyModEntityTypeId < 0)) {
                throw new IllegalArgumentException("Invalid plain Entity remote spawn rule");
            }
        }
    }

    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyPlainEntityRuntimePass.OUTPUT);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", 0) != 1
                    || !bool(root, "entityTypeRegistrationWired")
                    || !bool(root, "clientRendererRegistrationWired")
                    || !bool(root, "runtimeImplementationWired")) return;
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                Rule rule = parse(element.getAsJsonObject());
                if (rule == null || !modId.equals(rule.id().getNamespace())) continue;
                install(rule);
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted plain Entity runtime rules: mod={}, rules={}", modId, loaded);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load converted plain Entity runtime for " + modId, exception);
        }
    }

    public static List<Rule> rules(String namespace) {
        List<Rule> output = new ArrayList<>();
        for (Rule rule : RULES.values()) if (namespace.equals(rule.id().getNamespace())) output.add(rule);
        output.sort(java.util.Comparator.comparing(value -> value.id().toString()));
        return List.copyOf(output);
    }

    public static EntityType<? extends Entity> type(Identifier id) {
        return id == null ? null : TYPES.get(id);
    }

    public static Entity create(Identifier id, Level level) {
        if (id == null || level == null) return null;
        Constructor<? extends Entity> constructor = CONSTRUCTORS.get(id);
        EntityType<? extends Entity> type = TYPES.get(id);
        return constructor == null || type == null ? null : instantiate(constructor, type, level);
    }

    public static Rule remoteSpawnRule(String legacyModId, int legacyModEntityTypeId) {
        if (legacyModId == null || legacyModId.isBlank() || legacyModEntityTypeId < 0) return null;
        return REMOTE_RULES.get(new RemoteKey(legacyModId, legacyModEntityTypeId));
    }

    private static synchronized void install(Rule rule) throws Exception {
        Rule previous = RULES.putIfAbsent(rule.id(), rule);
        if (previous != null && !previous.equals(rule))
            throw new IllegalStateException("Conflicting converted plain Entity rule for " + rule.id());
        if (TYPES.containsKey(rule.id())) {
            installRemote(rule);
            return;
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(rule.id()))
            throw new IllegalStateException("Converted plain Entity id is already registered: " + rule.id());

        Class<?> raw = Class.forName(rule.generatedClass(), true, LegacyPlainEntityRegistry.class.getClassLoader());
        Class<? extends Entity> generated = raw.asSubclass(Entity.class);
        Constructor<? extends Entity> constructor = generated.getConstructor(EntityType.class, Level.class);
        EntityType.EntityFactory<Entity> factory = (type, level) -> instantiate(constructor, type, level);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rule.id());
        EntityType<Entity> created = EntityType.Builder.<Entity>of(factory, MobCategory.MISC)
                .sized(rule.width(), rule.height())
                .clientTrackingRange(rule.modernClientTrackingRangeChunks())
                .updateInterval(rule.updateFrequency())
                .build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, created);
        TYPES.put(rule.id(), created);
        CONSTRUCTORS.put(rule.id(), constructor);
        installRemote(rule);
    }

    private static void installRemote(Rule rule) {
        if (!rule.remoteEntitySpawnRuntimeComplete()) return;
        RemoteKey key = new RemoteKey(rule.legacyModId(), rule.legacyModEntityTypeId());
        Rule previous = REMOTE_RULES.putIfAbsent(key, rule);
        if (previous != null && !previous.equals(rule))
            throw new IllegalStateException("Conflicting converted legacy remote Entity identity: "
                    + rule.legacyModId() + ":" + rule.legacyModEntityTypeId());
    }

    private static Entity instantiate(Constructor<? extends Entity> constructor, EntityType<?> type, Level level) {
        try {
            return constructor.newInstance(type, level);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not instantiate generated converted Entity "
                    + constructor.getDeclaringClass().getName(), exception);
        }
    }

    static Rule parseForTests(JsonObject value) { return parse(value); }
    static synchronized void clearForTests() {
        RULES.clear(); TYPES.clear(); CONSTRUCTORS.clear(); REMOTE_RULES.clear();
    }

    private static Rule parse(JsonObject value) {
        try {
            String idValue = string(value, "id");
            String generatedClass = string(value, "generatedClass");
            String presentationAdapter = string(value, "presentationAdapter");
            if (idValue == null || generatedClass == null || presentationAdapter == null) return null;
            return new Rule(Identifier.parse(idValue), generatedClass,
                    string(value, "legacyModId"), integer(value, "legacyModEntityTypeId", -1),
                    decimal(value, "width", -1F), decimal(value, "height", -1F),
                    integer(value, "legacyTrackingRangeBlocks", 0),
                    integer(value, "modernClientTrackingRangeChunks", 0),
                    integer(value, "updateFrequency", 0), bool(value, "velocityUpdates"), presentationAdapter,
                    bool(value, "entityTypeRegistrationWired"), bool(value, "clientRendererRegistrationWired"),
                    bool(value, "legacyWatcherBridgeWired"), bool(value, "remoteEntitySpawnRuntimeComplete"),
                    bool(value, "runtimeImplementationWired"), bool(value, "runtimeComplete"));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
    private static int integer(JsonObject value, String key, int fallback) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }
    private static float decimal(JsonObject value, String key, float fallback) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }
    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
