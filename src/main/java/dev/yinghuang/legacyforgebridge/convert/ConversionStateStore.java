package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Human-readable persistent conversion state. This is intentionally separate from the compact
 * source fingerprint cache so users can inspect progress, staging, update, and restart state.
 */
public final class ConversionStateStore {
    public static final String FILE_NAME = "conversion-state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final JsonObject root;
    private final JsonObject entries;

    private ConversionStateStore(Path file, JsonObject root) {
        this.file = file;
        this.root = root;
        JsonElement existingEntries = root.get("entries");
        if (existingEntries != null && existingEntries.isJsonObject()) {
            this.entries = existingEntries.getAsJsonObject();
        } else {
            this.entries = new JsonObject();
            root.add("entries", this.entries);
        }
        root.addProperty("schemaVersion", 1);
    }

    public static ConversionStateStore open(Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        Path file = cacheDir.resolve(FILE_NAME);
        JsonObject root = new JsonObject();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    root = parsed.getAsJsonObject();
                }
            } catch (RuntimeException malformedState) {
                root = new JsonObject();
            }
        }
        return new ConversionStateStore(file, root);
    }

    public Path file() {
        return file;
    }

    public void beginLaunch(String converterVersion) {
        root.addProperty("schemaVersion", 1);
        root.addProperty("converterVersion", converterVersion);
        root.addProperty("launchStartedAt", Instant.now().toString());
        root.addProperty("updatedAt", Instant.now().toString());
        root.addProperty("restartRequired", false);
    }

    public Set<String> sourceFiles() {
        return Set.copyOf(new LinkedHashSet<>(entries.keySet()));
    }

    public Snapshot snapshot(String sourceFile) {
        JsonElement element = entries.get(sourceFile);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        return new Snapshot(
                string(object, "cacheFingerprint"),
                string(object, "sourceSha256"),
                longValue(object, "sourceSize"),
                longValue(object, "sourceModifiedMillis"),
                string(object, "profile"),
                string(object, "status"),
                string(object, "fabricId"),
                string(object, "candidatePath"),
                string(object, "candidateSha256"),
                string(object, "managedJarPath"),
                string(object, "managedSha256"),
                booleanValue(object, "loaderSafe"),
                booleanValue(object, "loadedThisLaunch"),
                booleanValue(object, "pendingSwap"),
                booleanValue(object, "restartRequired"),
                string(object, "phase"),
                integer(object, "percent"),
                string(object, "message")
        );
    }

    public void progress(String sourceFile, int percent, String phase, String message) throws IOException {
        JsonObject entry = entry(sourceFile);
        entry.addProperty("sourceFile", sourceFile);
        entry.addProperty("percent", Math.max(0, Math.min(100, percent)));
        entry.addProperty("phase", phase);
        entry.addProperty("message", message);
        entry.addProperty("updatedAt", Instant.now().toString());
        touch();
        flush();
    }

    public void complete(String sourceFile, Completion completion) throws IOException {
        JsonObject entry = entry(sourceFile);
        entry.addProperty("sourceFile", sourceFile);
        entry.addProperty("cacheFingerprint", completion.cacheFingerprint());
        entry.addProperty("sourceSha256", completion.sourceSha256());
        entry.addProperty("sourceSize", completion.sourceSize());
        entry.addProperty("sourceModifiedMillis", completion.sourceModifiedMillis());
        entry.addProperty("profile", completion.profile());
        entry.addProperty("status", completion.status());
        entry.addProperty("fabricId", completion.fabricId());
        entry.addProperty("candidatePath", completion.candidatePath());
        entry.addProperty("candidateSha256", completion.candidateSha256());
        entry.addProperty("managedJarPath", completion.managedJarPath());
        entry.addProperty("managedSha256", completion.managedSha256());
        entry.addProperty("loaderSafe", completion.loaderSafe());
        entry.addProperty("loadedThisLaunch", completion.loadedThisLaunch());
        entry.addProperty("pendingSwap", completion.pendingSwap());
        entry.addProperty("restartRequired", completion.restartRequired());
        entry.addProperty("percent", 100);
        entry.addProperty("phase", completion.phase());
        entry.addProperty("message", completion.message());
        entry.addProperty("updatedAt", Instant.now().toString());
        touch();
        flush();
    }

    public void remove(String sourceFile) throws IOException {
        entries.remove(sourceFile);
        touch();
        flush();
    }

    public void setRestartRequired(boolean restartRequired) throws IOException {
        root.addProperty("restartRequired", restartRequired);
        touch();
        flush();
    }

    public boolean restartRequired() {
        JsonElement value = root.get("restartRequired");
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    public void flush() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonObject entry(String sourceFile) {
        JsonElement existing = entries.get(sourceFile);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        entries.add(sourceFile, created);
        return created;
    }

    private void touch() {
        root.addProperty("updatedAt", Instant.now().toString());
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static long longValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0L;
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
    }

    private static boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    public record Snapshot(
            String cacheFingerprint,
            String sourceSha256,
            long sourceSize,
            long sourceModifiedMillis,
            String profile,
            String status,
            String fabricId,
            String candidatePath,
            String candidateSha256,
            String managedJarPath,
            String managedSha256,
            boolean loaderSafe,
            boolean loadedThisLaunch,
            boolean pendingSwap,
            boolean restartRequired,
            String phase,
            int percent,
            String message
    ) {
    }

    public record Completion(
            String cacheFingerprint,
            String sourceSha256,
            long sourceSize,
            long sourceModifiedMillis,
            String profile,
            String status,
            String fabricId,
            String candidatePath,
            String candidateSha256,
            String managedJarPath,
            String managedSha256,
            boolean loaderSafe,
            boolean loadedThisLaunch,
            boolean pendingSwap,
            boolean restartRequired,
            String phase,
            String message
    ) {
    }
}
