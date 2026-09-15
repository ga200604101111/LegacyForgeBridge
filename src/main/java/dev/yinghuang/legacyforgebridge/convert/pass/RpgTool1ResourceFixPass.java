package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Normalizes pre-1.13 item texture paths after RPGTool semantic model generation. */
public final class RpgTool1ResourceFixPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LEGACY_TEXTURE_ID = "rpgtool1:items/";
    private static final String MODERN_TEXTURE_ID = "rpgtool1:item/";

    @Override
    public String id() {
        return "profile:rpgtool1-modern-item-resources";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        ResourceFixResult result = fix(context.stagingDir());
        context.diagnostics().info(
                "LFB-RPGTOOL-RESOURCE-0001",
                SupportLevel.AUTO,
                "Normalized RPGTool item textures for modern resource lookup: movedFiles="
                        + result.movedFiles() + ", rewrittenJson=" + result.rewrittenJson()
        );
    }

    static ResourceFixResult fix(Path stagingDir) throws IOException {
        Path assets = stagingDir.resolve("assets/rpgtool1");
        int moved = moveDirectoryContents(
                assets.resolve("textures/items"),
                assets.resolve("textures/item")
        );

        int rewritten = 0;
        rewritten += rewriteJsonTree(assets.resolve("models/item"));
        rewritten += rewriteJsonTree(assets.resolve("items"));
        return new ResourceFixResult(moved, rewritten);
    }

    private static int moveDirectoryContents(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return 0;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.walk(source)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }

        for (Path file : files) {
            Path destination = target.resolve(source.relativize(file).toString());
            Files.createDirectories(destination.getParent());
            Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        try (Stream<Path> stream = Files.walk(source)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new ResourceDeleteFailure(exception);
                }
            });
        } catch (ResourceDeleteFailure failure) {
            throw failure.cause;
        }
        return files.size();
    }

    private static int rewriteJsonTree(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }

        List<Path> jsonFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            jsonFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }

        int rewritten = 0;
        for (Path jsonFile : jsonFiles) {
            JsonElement original;
            try (Reader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
                original = JsonParser.parseReader(reader);
            }
            JsonElement normalized = rewriteElement(original);
            if (!normalized.equals(original)) {
                Files.writeString(jsonFile, GSON.toJson(normalized) + "\n", StandardCharsets.UTF_8);
                rewritten++;
            }
        }
        return rewritten;
    }

    private static JsonElement rewriteElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isString()) {
                return primitive.deepCopy();
            }
            return new JsonPrimitive(primitive.getAsString().replace(LEGACY_TEXTURE_ID, MODERN_TEXTURE_ID));
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                array.add(rewriteElement(child));
            }
            return array;
        }

        JsonObject object = new JsonObject();
        for (String key : element.getAsJsonObject().keySet()) {
            object.add(key, rewriteElement(element.getAsJsonObject().get(key)));
        }
        return object;
    }

    record ResourceFixResult(int movedFiles, int rewrittenJson) {
    }

    private static final class ResourceDeleteFailure extends RuntimeException {
        private final IOException cause;

        private ResourceDeleteFailure(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
