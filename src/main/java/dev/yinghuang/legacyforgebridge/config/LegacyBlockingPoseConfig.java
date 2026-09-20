package dev.yinghuang.legacyforgebridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Global, client-only visual correction applied after Via/vanilla has selected the blocking pose.
 *
 * <p>This class never changes item use, packets, attack blocking, animation selection or damage.
 * It only appends an affine transform immediately before an actively used converted item is
 * submitted to the renderer.</p>
 */
public final class LegacyBlockingPoseConfig {
    public static final String FILE_NAME = "legacyforgebridge-client.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 1;
    private static final int RELOAD_INTERVAL_TICKS = 20;

    private static volatile Settings current = Settings.defaults();
    private static Path path;
    private static FileTime lastModified;
    private static int reloadTicks;

    private LegacyBlockingPoseConfig() { }

    public static synchronized void initialize() {
        if (path != null) return;
        path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                Files.writeString(path, JSON.toJson(defaultDocument()) + "\n", StandardCharsets.UTF_8);
            }
            reload(true);
            LegacyForgeBridge.LOGGER.info("Global converted-weapon blocking pose config: {}", path);
        } catch (IOException | RuntimeException failure) {
            current = Settings.defaults();
            LegacyForgeBridge.LOGGER.warn("Could not initialize blocking pose config {}; zero-offset defaults remain active.", path, failure);
        }
    }

    /** Cheap client-tick polling permits angle tuning without restarting Minecraft. */
    public static void tick() {
        if (path == null) initialize();
        if (++reloadTicks < RELOAD_INTERVAL_TICKS) return;
        reloadTicks = 0;
        synchronized (LegacyBlockingPoseConfig.class) {
            try {
                FileTime modified = Files.getLastModifiedTime(path);
                if (!modified.equals(lastModified)) reload(false);
            } catch (IOException | RuntimeException failure) {
                LegacyForgeBridge.LOGGER.warn("Could not reload blocking pose config {}; keeping the last valid values.", path, failure);
            }
        }
    }

    public static boolean enabled() {
        return current.enabled();
    }

    public static void applyFirstPerson(PoseStack matrices, boolean leftHand) {
        Settings snapshot = current;
        if (snapshot.enabled()) snapshot.firstPerson().apply(matrices, leftHand && snapshot.mirrorLeftHand());
    }

    public static void applyThirdPerson(PoseStack matrices, boolean leftHand) {
        Settings snapshot = current;
        if (snapshot.enabled()) snapshot.thirdPerson().apply(matrices, leftHand && snapshot.mirrorLeftHand());
    }

    static Settings parse(JsonObject root) {
        requireOnly(root, "schemaVersion", "swordBlockingPose");
        int schema = integer(root, "schemaVersion");
        if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported blocking pose schemaVersion=" + schema);
        JsonObject section = object(root, "swordBlockingPose");
        requireOnly(section, "enabled", "mirrorLeftHand", "firstPerson", "thirdPerson");
        return new Settings(
                bool(section, "enabled"),
                bool(section, "mirrorLeftHand"),
                transform(object(section, "firstPerson"), "firstPerson"),
                transform(object(section, "thirdPerson"), "thirdPerson")
        );
    }

    static JsonObject document(Settings settings) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject section = new JsonObject();
        section.addProperty("enabled", settings.enabled());
        section.addProperty("mirrorLeftHand", settings.mirrorLeftHand());
        section.add("firstPerson", transformDocument(settings.firstPerson()));
        section.add("thirdPerson", transformDocument(settings.thirdPerson()));
        root.add("swordBlockingPose", section);
        return root;
    }

    static Settings settingsForTests() {
        return current;
    }

    static void installForTests(Settings settings) {
        current = settings;
    }

    private static synchronized void reload(boolean initial) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        Settings parsed = parse(root);
        current = parsed;
        lastModified = Files.getLastModifiedTime(path);
        if (!initial) LegacyForgeBridge.LOGGER.info("Reloaded global converted-weapon blocking pose config: {}", path);
    }

    private static JsonObject defaultDocument() {
        return document(Settings.defaults());
    }

    private static Transform transform(JsonObject value, String name) {
        requireOnly(value, "translation", "rotationDegrees", "scale");
        Vec3 translation = vector(value, "translation", -4.0F, 4.0F);
        Vec3 rotation = vector(value, "rotationDegrees", -360.0F, 360.0F);
        Vec3 scale = vector(value, "scale", 0.05F, 4.0F);
        if (scale.x() == 0 || scale.y() == 0 || scale.z() == 0) {
            throw new IllegalArgumentException(name + ".scale cannot contain zero");
        }
        return new Transform(translation, rotation, scale);
    }

    private static JsonObject transformDocument(Transform transform) {
        JsonObject value = new JsonObject();
        value.add("translation", array(transform.translation()));
        value.add("rotationDegrees", array(transform.rotationDegrees()));
        value.add("scale", array(transform.scale()));
        return value;
    }

    private static JsonArray array(Vec3 vector) {
        JsonArray result = new JsonArray();
        result.add(vector.x()); result.add(vector.y()); result.add(vector.z());
        return result;
    }

    private static Vec3 vector(JsonObject object, String key, float minimum, float maximum) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(key + " must be an array of exactly three numbers");
        }
        JsonArray array = element.getAsJsonArray();
        float[] values = new float[3];
        for (int index = 0; index < values.length; index++) {
            JsonElement coordinate = array.get(index);
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " contains a non-number");
            }
            float number = coordinate.getAsFloat();
            if (!Float.isFinite(number) || number < minimum || number > maximum) {
                throw new IllegalArgumentException(key + " value outside " + minimum + ".." + maximum + ": " + number);
            }
            values[index] = number;
        }
        return new Vec3(values[0], values[1], values[2]);
    }

    private static void requireOnly(JsonObject object, String... keys) {
        java.util.Set<String> allowed = java.util.Set.of(keys);
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown blocking pose config key: " + key);
        }
        for (String key : keys) {
            if (!object.has(key)) throw new IllegalArgumentException("Missing blocking pose config key: " + key);
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return value.getAsJsonObject();
    }

    private static int integer(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        double number = value.getAsDouble();
        int result = value.getAsInt();
        if (!Double.isFinite(number) || number != result) throw new IllegalArgumentException(key + " must be an integer");
        return result;
    }

    private static boolean bool(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return value.getAsBoolean();
    }

    public record Settings(boolean enabled, boolean mirrorLeftHand, Transform firstPerson, Transform thirdPerson) {
        public Settings {
            if (firstPerson == null || thirdPerson == null) throw new IllegalArgumentException("Blocking pose transforms are required");
        }
        public static Settings defaults() {
            return new Settings(true, true, Transform.identity(), Transform.identity());
        }
    }

    public record Transform(Vec3 translation, Vec3 rotationDegrees, Vec3 scale) {
        public Transform {
            if (translation == null || rotationDegrees == null || scale == null) {
                throw new IllegalArgumentException("Transform vectors are required");
            }
        }
        public static Transform identity() {
            return new Transform(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);
        }
        public Transform mirrored() {
            return new Transform(
                    new Vec3(-translation.x(), translation.y(), translation.z()),
                    new Vec3(rotationDegrees.x(), -rotationDegrees.y(), -rotationDegrees.z()),
                    scale
            );
        }
        public void apply(PoseStack matrices, boolean mirror) {
            Transform value = mirror ? mirrored() : this;
            matrices.translate(value.translation.x(), value.translation.y(), value.translation.z());
            if (value.rotationDegrees.x() != 0) matrices.mulPose(Axis.XP.rotationDegrees(value.rotationDegrees.x()));
            if (value.rotationDegrees.y() != 0) matrices.mulPose(Axis.YP.rotationDegrees(value.rotationDegrees.y()));
            if (value.rotationDegrees.z() != 0) matrices.mulPose(Axis.ZP.rotationDegrees(value.rotationDegrees.z()));
            matrices.scale(value.scale.x(), value.scale.y(), value.scale.z());
        }
    }

    public record Vec3(float x, float y, float z) {
        private static final Vec3 ZERO = new Vec3(0, 0, 0);
        private static final Vec3 ONE = new Vec3(1, 1, 1);
    }
}
