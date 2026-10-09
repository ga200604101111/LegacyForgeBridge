package dev.yinghuang.legacyforgebridge.config.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Joins loaded converted Fabric mod identity to the unchanged old-mods JAR.
 * No original class is loaded. Checked-in profiles are bound to exact source SHA-256;
 * for any other mod a pre-existing standard Forge .cfg is safely reflected.
 */
public final class LegacyForgeConfigRegistry {
    private static final String CONVERSION_MANIFEST = "legacyforgebridge/conversion-manifest.json";
    private static final String RESOURCE_ROOT = "legacyforgebridge/legacy-config-profiles/";
    private LegacyForgeConfigRegistry() { }

    public static boolean hasConvertedManifest(ModContainer mod) {
        return mod.findPath(CONVERSION_MANIFEST).isPresent();
    }

    public record Profile(String name, Path source, String sha256,
                          Map<Path, List<LegacyForgeCfgFile.Property>> properties, int worldScopedProperties) { }

    public static Profile load(ModContainer converted) throws IOException {
        String fabricId = converted.getMetadata().getId();
        if (!hasConvertedManifest(converted)) throw new IOException("Not a converted Forge mod: " + fabricId);
        Path oldModsDir = FabricLoader.getInstance().getGameDir().resolve("old-mods").normalize();
        Path configDir = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(oldModsDir)) throw new IOException("Original old-mods directory unavailable: " + oldModsDir);
        List<Path> matches = new ArrayList<>();
        try (var jars = Files.list(oldModsDir)) {
            for (Path path : jars.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList()) {
                try {
                    if (LegacyModMetadata.read(path).fabricId().equals(fabricId)) matches.add(path);
                } catch (IOException | RuntimeException ignored) { /* corrupt/unrelated JAR: do not select */ }
            }
        }
        if (matches.size() != 1) {
            throw new IOException("Expected exactly one source JAR for " + fabricId + ", found " + matches.size());
        }
        Path original = matches.getFirst();
        LegacyModMetadata metadata = LegacyModMetadata.read(original);
        String sha = sha256(original);
        String name = metadata.primary().name();
        List<LegacyForgeCfgFile.Property> options = new ArrayList<>();
        int worldScoped = 0;
        try (InputStream resource = LegacyForgeConfigRegistry.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + sha + ".json")) {
            if (resource != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!sha.equals(root.get("sourceSha256").getAsString()) ||
                        !metadata.primary().modId().equalsIgnoreCase(root.get("modId").getAsString())) {
                    throw new IOException("Config profile source identity mismatch: " + name);
                }
                JsonArray array = root.getAsJsonArray("properties");
                if (array == null || array.size() > 1_000) throw new IOException("Invalid config profile size");
                for (JsonElement element : array) {
                    JsonObject field = element.getAsJsonObject();
                    String scope = field.has("scope") ? field.get("scope").getAsString() : "instance-config";
                    if (!scope.equals("instance-config")) {
                        // A world-save setting belongs to the original 1.7.10 world/server.
                        // Never redirect it to the local Fabric instance config directory.
                        worldScoped++;
                        continue;
                    }
                    String type = field.get("type").getAsString();
                    if (type.length() != 1) throw new IOException("Invalid config property type");
                    options.add(new LegacyForgeCfgFile.Property(
                            field.get("file").getAsString(),
                            field.get("category").getAsString(),
                            field.get("key").getAsString(),
                            type.charAt(0), field.get("default").getAsString(),
                            field.has("comment") ? field.get("comment").getAsString() : ""));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException badProfile) {
            throw new IOException("Invalid source-pinned config profile", badProfile);
        }
        Map<Path, List<LegacyForgeCfgFile.Property>> files = new LinkedHashMap<>();
        if (!options.isEmpty()) {
            for (LegacyForgeCfgFile.Property property : options) {
                Path target = checkedTarget(configDir, property.fileName());
                files.computeIfAbsent(target, k -> new ArrayList<>()).add(property);
            }
        } else {
            // Generic read-existing fallback; never associate an unrelated .cfg by fuzzy substring.
            String expected = metadata.primary().modId() + ".cfg";
            if (Files.isDirectory(configDir)) {
                try (var stream = Files.list(configDir)) {
                    for (Path path : stream.filter(Files::isRegularFile).toList()) {
                        if (!path.getFileName().toString().equalsIgnoreCase(expected)) continue;
                        Path target = checkedTarget(configDir, path.getFileName().toString());
                        List<LegacyForgeCfgFile.Property> existing = new LegacyForgeCfgFile(target).existingProperties();
                        if (!existing.isEmpty()) files.put(target, existing);
                    }
                }
            }
        }
        Map<Path,List<LegacyForgeCfgFile.Property>> immutable = new LinkedHashMap<>();
        for (var entry : files.entrySet()) immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        return new Profile(name, original, sha, java.util.Collections.unmodifiableMap(immutable), worldScoped);
    }

    private static Path checkedTarget(Path base, String filename) throws IOException {
        if (!filename.matches("[A-Za-z0-9_.-]+\\.cfg") || filename.contains("..")) {
            throw new IOException("Unsafe config filename");
        }
        Path path = base.resolve(filename).normalize();
        if (!path.getParent().equals(base) || Files.isSymbolicLink(path)) {
            throw new IOException("Config path is outside local config directory or is a symbolic link");
        }
        return path;
    }

    private static String sha256(Path source) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(source), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 unavailable", error);
        }
    }
}
