package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hash-bound provenance for provisional models. A later specialized rewrite revokes ownership. */
public final class LegacyPresentationOwnership {
    public static final String PATH = "legacyforgebridge/provisional-models.json";
    private LegacyPresentationOwnership() { }

    public static void record(Path staging, Path model) throws IOException {
        Path manifest = staging.resolve(PATH);
        JsonObject root = Files.isRegularFile(manifest)
                ? JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        root.addProperty(relative(staging, model), digest(model));
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, root + "\n", StandardCharsets.UTF_8);
    }

    public static void revoke(Path staging, Path model) throws IOException {
        Path manifest = staging.resolve(PATH);
        if (!Files.isRegularFile(manifest)) return;
        JsonObject root;
        try { root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { return; }
        if (root.remove(relative(staging, model)) != null) {
            Files.writeString(manifest, root + "\n", StandardCharsets.UTF_8);
        }
    }

    public static boolean owns(Path staging, Path model) throws IOException {
        Path manifest = staging.resolve(PATH);
        if (!Files.isRegularFile(manifest) || !Files.isRegularFile(model)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
            var expected = root.get(relative(staging, model));
            return expected != null && expected.isJsonPrimitive() && expected.getAsString().equals(digest(model));
        } catch (RuntimeException invalid) { return false; }
    }

    private static String relative(Path staging, Path model) {
        Path root = staging.toAbsolutePath().normalize(), file = model.toAbsolutePath().normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Model is outside conversion staging");
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String digest(Path model) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(model))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
