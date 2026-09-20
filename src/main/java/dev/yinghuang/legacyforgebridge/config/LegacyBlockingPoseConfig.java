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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Client-only visual correction applied after Via/vanilla has selected a BLOCK use animation.
 *
 * <p>The file is loaded once during client startup. Cloth Config writes a complete validated
 * snapshot and installs it immediately; there is deliberately no tick-time file polling.</p>
 */
public final class LegacyBlockingPoseConfig {
    public static final String FILE_NAME = "legacyforgebridge-client.json";
    public static final float TRANSLATION_MIN = -2.0F;
    public static final float TRANSLATION_MAX = 2.0F;
    public static final float ROTATION_MIN = -180.0F;
    public static final float ROTATION_MAX = 180.0F;

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 2;

    private static volatile Settings current = Settings.defaults();
    private static Path path;

    private LegacyBlockingPoseConfig() { }

    public static synchronized void initialize() {
        if (path != null) return;
        path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                writeDocument(Settings.defaults());
            }
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            Settings parsed = parse(root);
            current = parsed;
            if (integer(root, "schemaVersion") != SCHEMA_VERSION) {
                writeDocument(parsed); // one-time .144 schema migration; scale is intentionally discarded
            }
            LegacyForgeBridge.LOGGER.info("Blocking-pose config loaded once; Cloth Config owns runtime updates: {}", path);
        } catch (IOException | RuntimeException failure) {
            current = Settings.defaults();
            LegacyForgeBridge.LOGGER.warn("Could not initialize blocking-pose config {}; built-in defaults remain active.", path, failure);
        }
    }

    public static Settings current() {
        return current;
    }

    public static boolean enabled() {
        return current.enabled();
    }

    /** Save from the Cloth Config screen and install the same immutable snapshot immediately. */
    public static synchronized void save(Settings settings) {
        if (settings == null) throw new IllegalArgumentException("Blocking-pose settings are required");
        if (path == null) initialize();
        try {
            writeDocument(settings);
            current = settings;
            LegacyForgeBridge.LOGGER.info("Saved and applied blocking-pose config: {}", path);
        } catch (IOException | RuntimeException failure) {
            LegacyForgeBridge.LOGGER.error("Could not save blocking-pose config {}; keeping the previous values.", path, failure);
        }
    }

    public static void applyFirstPerson(PoseStack matrices, boolean leftHand) {
        Settings snapshot = current;
        if (snapshot.enabled()) {
            snapshot.firstPerson().applyScreenSpace(matrices, leftHand && snapshot.mirrorLeftHand());
        }
    }

    public static void applyThirdPerson(PoseStack matrices, boolean leftHand) {
        Settings snapshot = current;
        if (snapshot.enabled()) {
            snapshot.thirdPerson().applyArmLocalSpace(matrices, leftHand && snapshot.mirrorLeftHand());
        }
    }

    static Settings parse(JsonObject root) {
        requireOnly(root, "schemaVersion", "swordBlockingPose");
        int schema = integer(root, "schemaVersion");
        if (schema != 1 && schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported blocking pose schemaVersion=" + schema);
        }
        JsonObject section = object(root, "swordBlockingPose");
        requireOnly(section, "enabled", "mirrorLeftHand", "firstPerson", "thirdPerson");
        return new Settings(
                bool(section, "enabled"),
                bool(section, "mirrorLeftHand"),
                transform(object(section, "firstPerson"), "firstPerson", schema),
                transform(object(section, "thirdPerson"), "thirdPerson", schema)
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

    static void installForTests(Settings settings) {
        current = settings;
    }

    private static void writeDocument(Settings settings) throws IOException {
        JsonObject output = document(settings);
        // Re-parse before publication so the UI cannot persist a value outside runtime policy.
        parse(output);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(output) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Transform transform(JsonObject value, String name, int schema) {
        if (schema == 1) {
            allowOnly(value, "translation", "rotationDegrees", "scale");
            requireKeys(value, "translation", "rotationDegrees");
            if (value.has("scale")) vector(value, "scale", 0.05F, 4.0F); // validate, then discard
        } else {
            requireOnly(value, "translation", "rotationDegrees");
        }
        return new Transform(
                vector(value, "translation", TRANSLATION_MIN, TRANSLATION_MAX),
                vector(value, "rotationDegrees", ROTATION_MIN, ROTATION_MAX)
        );
    }

    private static JsonObject transformDocument(Transform transform) {
        JsonObject value = new JsonObject();
        value.add("translation", array(transform.translation()));
        value.add("rotationDegrees", array(transform.rotationDegrees()));
        return value;
    }

    private static JsonArray array(Vec3 vector) {
        JsonArray result = new JsonArray();
        result.add(vector.x());
        result.add(vector.y());
        result.add(vector.z());
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
        allowOnly(object, keys);
        requireKeys(object, keys);
    }

    private static void allowOnly(JsonObject object, String... keys) {
        Set<String> allowed = Set.of(keys);
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown blocking pose config key: " + key);
        }
    }

    private static void requireKeys(JsonObject object, String... keys) {
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
            return new Settings(
                    true,
                    true,
                    new Transform(Vec3.ZERO, new Vec3(0.0F, 20.0F, 0.0F)),
                    new Transform(new Vec3(-0.15F, -0.17F, 0.0F), new Vec3(-30.0F, 40.0F, 40.0F))
            );
        }
    }

    public record Transform(Vec3 translation, Vec3 rotationDegrees) {
        public Transform {
            if (translation == null || rotationDegrees == null) {
                throw new IllegalArgumentException("Transform vectors are required");
            }
            translation.validate("translation", TRANSLATION_MIN, TRANSLATION_MAX);
            rotationDegrees.validate("rotationDegrees", ROTATION_MIN, ROTATION_MAX);
        }

        public Transform mirrored() {
            return new Transform(
                    new Vec3(-translation.x(), translation.y(), translation.z()),
                    new Vec3(rotationDegrees.x(), -rotationDegrees.y(), -rotationDegrees.z())
            );
        }

        /** First person keeps its offset in screen/parent axes, independent of the sword rotation. */
        public void applyScreenSpace(PoseStack matrices, boolean mirror) {
            Transform value = mirror ? mirrored() : this;
            matrices.last().pose().translateLocal(
                    value.translation.x(), value.translation.y(), value.translation.z());
            applyRotations(matrices, value.rotationDegrees());
        }

        /** Third person follows the already-established player arm transform and turns with it. */
        public void applyArmLocalSpace(PoseStack matrices, boolean mirror) {
            Transform value = mirror ? mirrored() : this;
            matrices.translate(value.translation.x(), value.translation.y(), value.translation.z());
            applyRotations(matrices, value.rotationDegrees());
        }

        /** Compatibility alias for earlier internal callers; retains first-person screen-space behavior. */
        public void apply(PoseStack matrices, boolean mirror) {
            applyScreenSpace(matrices, mirror);
        }

        private static void applyRotations(PoseStack matrices, Vec3 rotation) {
            if (rotation.x() != 0) matrices.mulPose(Axis.XP.rotationDegrees(rotation.x()));
            if (rotation.y() != 0) matrices.mulPose(Axis.YP.rotationDegrees(rotation.y()));
            if (rotation.z() != 0) matrices.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
        }
    }

    public record Vec3(float x, float y, float z) {
        private static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);

        private void validate(String name, float minimum, float maximum) {
            for (float value : new float[]{x, y, z}) {
                if (!Float.isFinite(value) || value < minimum || value > maximum) {
                    throw new IllegalArgumentException(name + " value outside " + minimum + ".." + maximum + ": " + value);
                }
            }
        }
    }
}
