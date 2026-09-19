package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Materializes model-referenced legacy sprites inside each converted candidate. No global resource
 * patch or extra mod is required. Source PNG/animation files remain untouched. Alias names are
 * derived from the exact source path, so case-fold collisions cannot silently select another image.
 */
public final class LegacyTextureAtlasPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/texture-atlas-conversion.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-texture-atlas-conversion"; }

    @Override public void apply(ConversionContext context) throws IOException {
        Path root = context.stagingDir();
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        String namespace = context.metadata().fabricId();
        Map<String, List<Path>> index = new TreeMap<>();
        List<Path> models;
        try (Stream<Path> stream = Files.walk(assets)) {
            List<Path> all = stream.filter(Files::isRegularFile).sorted().toList();
            models = all.stream().filter(p -> {
                String s = assets.relativize(p).toString().replace('\\', '/');
                return s.contains("/models/") && s.endsWith(".json");
            }).toList();
            for (Path p : all) {
                String s = assets.relativize(p).toString().replace('\\', '/');
                int separator = s.indexOf("/textures/");
                if (separator < 1 || !s.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                String key = s.substring(0, separator) + ":" + s.substring(separator + 10, s.length() - 4);
                index.computeIfAbsent(key.toLowerCase(Locale.ROOT), unused -> new ArrayList<>()).add(p);
            }
        }
        Map<String, JsonObject> modelIndex = new HashMap<>();
        for (Path model : models) {
            String rel = assets.relativize(model).toString().replace('\\', '/');
            int at = rel.indexOf("/models/");
            JsonElement json = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8));
            if (json.isJsonObject()) modelIndex.put(rel.substring(0, at) + ":" + rel.substring(at + 8, rel.length() - 5), json.getAsJsonObject());
        }
        Map<String, String> aliases = new TreeMap<>();
        Map<String, String> copied = new TreeMap<>();
        Set<String> missing = new TreeSet<>();
        Set<String> ambiguous = new TreeSet<>();
        Set<String> blockSprites = new TreeSet<>(), itemSprites = new TreeSet<>();
        int updatedModels = 0;
        for (Path model : models) {
            JsonElement parsed = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) continue;
            JsonObject json = parsed.getAsJsonObject();
            JsonObject effective = effectiveTextures(json, modelIndex, new HashSet<>());
            if (effective.isEmpty()) continue;
            JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject();
            String modelPath = assets.relativize(model).toString().replace('\\', '/');
            boolean blockAtlas = modelPath.contains("/models/block/") || blockParent(json, modelIndex, new HashSet<>());
            for (JsonElement value : effective.asMap().values()) if (value.isJsonPrimitive()) {
                String ref = value.getAsString().toLowerCase(Locale.ROOT);
                int colon = ref.indexOf(':'); String part = colon < 0 ? ref : ref.substring(colon + 1);
                blockAtlas |= part.startsWith("block/") || part.startsWith("blocks/");
            }
            String targetKind = blockAtlas ? "block" : "item";
            boolean changed = false;
            for (Map.Entry<String, JsonElement> e : new ArrayList<>(effective.entrySet())) {
                if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString()) continue;
                String original = e.getValue().getAsString();
                if (original.startsWith("#")) continue;
                String requested = original.indexOf(':') < 0 ? "minecraft:" + original : original;
                List<Path> found = index.getOrDefault(requested.toLowerCase(Locale.ROOT), List.of());
                if (found.isEmpty()) {
                    // Vanilla/other-mod resources can be supplied by a lower resource pack. Report
                    // only references into source namespaces owned by this candidate.
                    String ns = requested.substring(0, requested.indexOf(':'));
                    if (!"minecraft".equals(ns) && Files.isDirectory(assets.resolve(ns))) missing.add(original);
                    continue;
                }
                // An exact case-sensitive path wins. Otherwise accept only one unique candidate.
                Path source = null;
                for (Path p : found) {
                    String s = assets.relativize(p).toString().replace('\\', '/');
                    int at = s.indexOf("/textures/");
                    String exact = s.substring(0, at) + ":" + s.substring(at + 10, s.length() - 4);
                    if (exact.equals(requested)) { source = p; break; }
                }
                if (source == null && found.size() == 1) source = found.getFirst();
                if (source == null) { ambiguous.add(original); continue; }
                String path = requested.substring(requested.indexOf(':') + 1);
                boolean oldPath = path.startsWith("blocks/") || path.startsWith("items/");
                boolean legal = requested.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
                if (!oldPath && legal && path.startsWith(targetKind + "/")) continue;
                String sourceRelative = assets.relativize(source).toString().replace('\\', '/');
                // Block-backed inventory models intentionally stay in the block atlas. Minecraft
                // 1.21.11 separates item and block atlases; never register a sprite in both.
                String kind = targetKind;
                String copyKey = kind + ":" + sourceRelative;
                String sprite = copied.get(copyKey);
                if (sprite == null) {
                    sprite = namespace + ":" + kind + "/lfb_legacy/" + digest(sourceRelative);
                    Path target = root.resolve("assets/" + namespace + "/textures/" + kind
                            + "/lfb_legacy/" + digest(sourceRelative) + ".png");
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    Path animation = source.resolveSibling(source.getFileName() + ".mcmeta");
                    if (Files.isRegularFile(animation)) Files.copy(animation,
                            target.resolveSibling(target.getFileName() + ".mcmeta"), StandardCopyOption.REPLACE_EXISTING);
                    copied.put(copyKey, sprite);
                }
                textures.addProperty(e.getKey(), sprite);
                aliases.put(original + "@" + kind, sprite);
                (kind.equals("item") ? itemSprites : blockSprites).add(sprite);
                changed = true;
            }
            if (changed) { json.add("textures", textures); write(model, json); updatedModels++; }
        }
        mergeAtlas(root.resolve("assets/minecraft/atlases/blocks.json"), blockSprites);
        mergeAtlas(root.resolve("assets/minecraft/atlases/items.json"), itemSprites);
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schemaVersion", 1);
        evidence.addProperty("sourceSha256", context.sourceHash());
        evidence.addProperty("modelFilesRewritten", updatedModels);
        evidence.addProperty("spritesMaterialized", copied.size());
        evidence.addProperty("sourceFilesPreserved", true);
        evidence.addProperty("requiresExternalRepairMod", false);
        evidence.add("aliases", JSON.toJsonTree(aliases));
        evidence.add("missingOwnedSprites", JSON.toJsonTree(missing));
        evidence.add("ambiguousSprites", JSON.toJsonTree(ambiguous));
        evidence.addProperty("referencedOwnedSpritesResolved", missing.isEmpty() && ambiguous.isEmpty());
        write(root.resolve(OUTPUT), evidence);
        context.diagnostics().info("LFB-CONVERT-ATLAS-0001", SupportLevel.ADAPTED,
                "Materialized candidate-owned legacy sprites=" + copied.size() + ", rewrittenModels=" + updatedModels
                        + "; no external texture-fix mod is required.");
        if (!missing.isEmpty() || !ambiguous.isEmpty()) context.diagnostics().warning(
                "LFB-CONVERT-ATLAS-0002", SupportLevel.RUNTIME_BRIDGE,
                "Unresolved candidate-owned model sprites: missing=" + missing.size() + ", ambiguous=" + ambiguous.size());
    }

    private static JsonObject effectiveTextures(JsonObject model, Map<String,JsonObject> index, Set<String> seen) throws IOException {
        JsonObject result = new JsonObject();
        if (model.has("parent") && model.get("parent").isJsonPrimitive()) {
            String parent = model.get("parent").getAsString(); if (parent.indexOf(':') < 0) parent = "minecraft:" + parent;
            if (!seen.add(parent)) throw new IOException("Cyclic model parent: " + parent);
            JsonObject inherited = index.get(parent);
            if (inherited != null) result = effectiveTextures(inherited, index, seen);
        }
        if (model.has("textures") && model.get("textures").isJsonObject())
            for (var entry : model.getAsJsonObject("textures").entrySet()) result.add(entry.getKey(),entry.getValue().deepCopy());
        return result;
    }
    private static boolean blockParent(JsonObject model, Map<String,JsonObject> index, Set<String> seen) {
        if (!model.has("parent") || !model.get("parent").isJsonPrimitive()) return false;
        String parent = model.get("parent").getAsString(); if (parent.indexOf(':') < 0) parent = "minecraft:" + parent;
        if (!seen.add(parent)) return false;
        if (parent.substring(parent.indexOf(':') + 1).startsWith("block/")) return true;
        return index.containsKey(parent) && blockParent(index.get(parent),index,seen);
    }
    private static void mergeAtlas(Path path, Set<String> sprites) throws IOException {
        if (sprites.isEmpty()) return;
        JsonObject root = Files.isRegularFile(path)
                ? JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        JsonArray sources = root.has("sources") ? root.getAsJsonArray("sources") : new JsonArray();
        Set<String> present = new HashSet<>();
        for (JsonElement source : sources) if (source.isJsonObject()) {
            JsonObject s = source.getAsJsonObject();
            if (s.has("type") && Set.of("single", "minecraft:single").contains(s.get("type").getAsString())
                    && s.has("resource")) present.add(s.get("resource").getAsString());
        }
        for (String sprite : sprites) if (present.add(sprite)) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:single"); source.addProperty("resource", sprite);
            sources.add(source);
        }
        root.add("sources", sources); write(path, root);
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }
}
