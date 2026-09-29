package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCombatItemAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Restores source-proven sword constants and bow use presentation without executing legacy code.
 */
public final class LegacyCombatItemPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/combat-item-rules.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final float MODERN_SWORD_ATTACK_SPEED = -2.4F;

    @Override public String id() { return "legacy-combat-item-semantics"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path contentPath = context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if (!Files.isRegularFile(contentPath)) return;
        JsonObject content = read(contentPath);
        JsonArray items = content.getAsJsonArray("items");
        if (items == null) return;

        Map<String, JsonObject> byRegistration = new LinkedHashMap<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (item.has("sourceClass") && item.has("legacyRegistryName")) {
                byRegistration.put(registrationKey(
                        item.get("sourceClass").getAsString(),
                        item.get("legacyRegistryName").getAsString()), item);
            }
        }

        var analysis = new LegacyCombatItemAnalyzer().analyze(context.sourceJar());
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schemaVersion", 1);
        evidence.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int swords = 0, bows = 0;

        for (var rule : analysis.rules()) {
            JsonObject item = byRegistration.get(registrationKey(rule.sourceClass(), rule.registryName()));
            if (item == null || !item.has("id")) continue;
            String id = item.get("id").getAsString();
            item.addProperty("kind", rule.kind().name().toLowerCase());
            if (rule.durability() > 0) item.addProperty("durability", rule.durability());
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceClass", rule.sourceClass());
            value.addProperty("kind", rule.kind().name().toLowerCase());
            value.addProperty("durability", rule.durability());

            String[] split = id.split(":", 2);
            if (split.length != 2) continue;
            String namespace = split[0], path = split[1];
            if (rule.kind() == LegacyCombatItemAnalyzer.Kind.SWORD) {
                item.addProperty("attackDamage", rule.attackDamage());
                item.addProperty("attackSpeed", MODERN_SWORD_ATTACK_SPEED);
                rewriteItemModels(context.stagingDir(), namespace, path, "minecraft:item/handheld");
                value.addProperty("attackDamage", rule.attackDamage());
                value.addProperty("attackSpeed", MODERN_SWORD_ATTACK_SPEED);
                swords++;
            } else {
                item.addProperty("useDuration", rule.useDuration());
                item.addProperty("pullTexturePrefix", rule.pullTexturePrefix());
                item.addProperty("pullStages", rule.pullStages());
                rewriteBow(context.stagingDir(), namespace, path, rule);
                value.addProperty("useDuration", rule.useDuration());
                value.addProperty("pullTexturePrefix", rule.pullTexturePrefix());
                value.addProperty("pullStages", rule.pullStages());
                JsonArray pullStageMinTicks = new JsonArray();rule.pullStageMinTicks().forEach(pullStageMinTicks::add);
                value.add("pullStageMinTicks", pullStageMinTicks);
                bows++;
            }
            rules.add(value);
        }
        evidence.add("rules", rules);
        JsonArray skipped = new JsonArray();
        for (var item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", item.registryName());
            value.addProperty("sourceClass", item.sourceClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
        }
        evidence.add("skipped", skipped);
        JsonArray diagnostics = new JsonArray();
        analysis.diagnostics().forEach(diagnostics::add);
        evidence.add("diagnostics", diagnostics);
        write(contentPath, content);
        write(context.stagingDir().resolve(OUTPUT), evidence);
        context.diagnostics().info("LFB-CONVERT-COMBAT-0001", SupportLevel.ADAPTED,
                "Source-proven combat item semantics: swords=" + swords + ", bows=" + bows
                        + "; bow projectile behavior remains a separate runtime adapter.");
    }

    private static void rewriteItemModels(Path staging, String namespace, String path, String parent) throws IOException {
        Path root = staging.resolve("assets/" + namespace + "/models/item");
        if (!Files.isDirectory(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path model : walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().equals(path + ".json")
                            || file.getFileName().toString().startsWith(path + "_lfb_meta_"))
                    .toList()) {
                JsonObject value = read(model);
                if (!value.has("textures")) continue;
                value.addProperty("parent", parent);
                write(model, value);
            }
        }
    }

    private static void rewriteBow(Path staging, String namespace, String path,
                                   LegacyCombatItemAnalyzer.Rule rule) throws IOException {
        rewriteItemModels(staging, namespace, path, "minecraft:item/bow");
        String prefix = modernTexture(staging, rule.pullTexturePrefix());
        for (int stage = 0; stage < rule.pullStages(); stage++) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/bow");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", prefix + stage);
            model.add("textures", textures);
            write(staging.resolve("assets/" + namespace + "/models/item/" + path + "_pulling_" + stage + ".json"), model);
        }
        Path defaultDefinition = staging.resolve("assets/" + namespace + "/items/" + path + ".json");
        rewriteBowDefinition(defaultDefinition, namespace, path, rule);
        Path aliases = staging.resolve("assets/" + namespace + "/items/lfb_meta/" + path);
        if (Files.isDirectory(aliases)) {
            try (var walk = Files.walk(aliases)) {
                for (Path definition : walk.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".json")).toList()) {
                    rewriteBowDefinition(definition, namespace, path, rule);
                }
            }
        }
    }

    private static void rewriteBowDefinition(Path path, String namespace, String itemPath,
                                             LegacyCombatItemAnalyzer.Rule rule) throws IOException {
        if (!Files.isRegularFile(path)) return;
        JsonObject root = read(path);
        JsonElement ordinary = root.get("model");
        if (ordinary == null || !ordinary.isJsonObject()) return;

        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:use_duration");
        dispatch.addProperty("scale", 0.05F);
        JsonArray entries = new JsonArray();
        for (int stage = 0; stage < rule.pullStages(); stage++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("threshold", rule.pullStageMinTicks().get(stage) * 0.05F);
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", namespace + ":item/" + itemPath + "_pulling_" + stage);
            entry.add("model", model);
            entries.add(entry);
        }
        dispatch.add("entries", entries);
        dispatch.add("fallback", ordinary.deepCopy());

        JsonObject condition = new JsonObject();
        condition.addProperty("type", "minecraft:condition");
        condition.addProperty("property", "minecraft:using_item");
        condition.add("on_true", dispatch);
        condition.add("on_false", ordinary.deepCopy());
        root.add("model", condition);
        write(path, root);
    }

    private static String modernTexture(Path staging, String legacyPrefix) throws IOException {
        int split = legacyPrefix.indexOf(':');
        String namespace = split < 0 ? "minecraft" : legacyPrefix.substring(0, split);
        String path = split < 0 ? legacyPrefix : legacyPrefix.substring(split + 1);
        path = path.replace('\\', '/').replaceFirst("^textures/", "").replaceFirst("\\.png$", "");
        String itemPath = path.replaceFirst("^items?/", "");
        String[] candidates = path.contains("/")
                ? new String[] { path, "items/" + itemPath, "item/" + itemPath }
                : new String[] { "items/" + itemPath, "item/" + itemPath, itemPath };
        for (String candidate : candidates) {
            if (Files.isRegularFile(staging.resolve("assets/" + namespace + "/textures/" + candidate + "0.png"))) {
                return namespace + ":" + candidate;
            }
        }
        // Legacy 1.7.x item textures normally live under textures/items. Prefer that path when
        // the source prefix is directory-less so generated bow pulling models do not reference a
        // modernized textures/item location that was never emitted by the resource-copy passes.
        return namespace + ":" + (path.contains("/") ? path : "items/" + itemPath);
    }

    private static String registrationKey(String sourceClass, String registryName) {
        return sourceClass + "\n" + registryName;
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }
}
