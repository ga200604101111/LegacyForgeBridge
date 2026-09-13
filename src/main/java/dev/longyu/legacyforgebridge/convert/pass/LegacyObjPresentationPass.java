package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generic post-profile normalization for LFB Wavefront special-item models.
 *
 * <p>Legacy item renderers commonly relied on fixed-function alpha blending. A modern special
 * model must choose an appropriate render type explicitly, so this pass inspects the actual PNG
 * referenced by every {@code legacyforgebridge:obj} model. Textures with fractional alpha are
 * marked translucent; binary-alpha or opaque textures stay on the cutout path.</p>
 */
public final class LegacyObjPresentationPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-obj-presentation-normalization";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        List<Path> itemDefinitions = new ArrayList<>();
        Path assets = context.stagingDir().resolve("assets");
        if (Files.isDirectory(assets)) {
            try (Stream<Path> stream = Files.walk(assets)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> containsSegment(path, "items"))
                        .sorted()
                        .forEach(itemDefinitions::add);
            }
        }

        int objModels = 0;
        int translucentModels = 0;
        for (Path itemDefinition : itemDefinitions) {
            JsonObject root = readObject(itemDefinition);
            JsonObject itemModel = object(root, "model");
            if (itemModel == null || !"minecraft:special".equals(string(itemModel, "type"))) {
                continue;
            }
            JsonObject special = object(itemModel, "model");
            if (special == null || !"legacyforgebridge:obj".equals(string(special, "type"))) {
                continue;
            }
            objModels++;

            String texture = string(special, "texture");
            Path texturePath = texture == null ? null : resolveResource(context.stagingDir(), texture);
            if (texturePath != null && Files.isRegularFile(texturePath) && hasFractionalAlpha(texturePath)) {
                special.addProperty("translucent", true);
                Files.writeString(itemDefinition, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
                translucentModels++;
            }
        }

        if (objModels > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-OBJ-0003",
                    SupportLevel.ADAPTED,
                    "Normalized " + objModels + " legacy OBJ item presentation definition(s); "
                            + translucentModels + " use fractional-alpha translucent rendering."
            );
        }
    }

    static boolean hasFractionalAlpha(Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null || !image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Path resolveResource(Path stagingDir, String identifier) {
        int separator = identifier.indexOf(':');
        if (separator <= 0 || separator == identifier.length() - 1) {
            return null;
        }
        String namespace = identifier.substring(0, separator);
        String path = identifier.substring(separator + 1);
        if (path.contains("..") || path.startsWith("/")) {
            return null;
        }
        return stagingDir.resolve("assets").resolve(namespace).resolve(path);
    }

    private static boolean containsSegment(Path path, String segment) {
        for (Path part : path) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject readObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
