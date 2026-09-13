package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import dev.longyu.legacyforgebridge.convert.profile.RpgTool1Profile;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Corpus-backed semantic conversion for RPGTool1 1.7.10. */
public final class RpgTool1ContentPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NAMESPACE = "rpgtool1";

    private static final List<Sword> SWORDS = List.of(
            new Sword("dark_sword", 3600, 21, "dark_sword", 0.4F),
            new Sword("hot_sword", 2100, 25, "hot_sword", 0.4F),
            new Sword("king_sword", 2300, 29, "king_sword", 0.5F),
            new Sword("water_sword", 2155, 18, "water_sword", 0.7F),
            new Sword("green_sword", 2888, 19, "green_sword", 0.7F),
            new Sword("killer_sword", 3000, 21, "killer_sword", 0.7F),
            new Sword("purple_sword", 1800, 30, "purple_sword", 0.7F),
            new Sword("pink_flow", 1900, 25, "pink_flow", 0.6F),
            new Sword("redflower_sword", 2455, 26, "redflower_sword", 0.6F),
            new Sword("evil_sword", 3111, 21, "evil_sword", 0.7F),
            new Sword("cy_sword", 2698, 18, "cy_sword", 0.6F),
            new Sword("ocean_sword", 1445, 31, "ocean_sword", 0.5F),
            new Sword("water_flow", 1665, 26, "water_flow", 0.7F),
            new Sword("hot_evil_sword", 1380, 24, "hot_evil_sword", 0.6F),
            new Sword("pink_heart_sword", 2566, 26, "pink_heart_sword", 0.6F),
            new Sword("big_1", 2888, 36, "big1", 0.7F),
            new Sword("big_2", 2888, 35, "big2", 0.7F),
            new Sword("big_3", 2888, 33, "big3", 0.7F),
            new Sword("big_4", 2888, 38, "big4", 0.7F),
            new Sword("big_5", 2888, 40, "big5", 0.7F)
    );

    private static final List<String> WINGS = List.of(
            "wing01", "wing02", "wing03", "wing04", "wing05", "wing06", "wing07", "wing08"
    );

    private static final List<String> CIRCLES = List.of(
            "buff1_1", "buff1_2", "buff1_3", "buff1_4", "buff1_5",
            "buff2_1", "buff2_2", "buff2_3", "buff2_4", "buff2_5",
            "buff3_1", "buff3_2", "buff3_3", "buff3_4", "buff3_5"
    );

    private static final List<String> MATERIALS = List.of(
            "attack1", "attack2", "attack3", "attack4",
            "lifesteal1", "lifesteal2", "lifesteal3", "lifesteal4",
            "defense1", "defense2", "defense3", "defense4",
            "dig", "cleangp", "blue_gem", "red_gem", "yellow_gem", "purple_gem", "orange_gem",
            "night_vision", "fire_range", "one_shot", "range_attack", "under_water",
            "bad_gem", "high_kick", "right_range", "light_range"
    );

    @Override
    public String id() {
        return "rpgtool1-content-runtime-bridge";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        if (!RpgTool1Profile.CORPUS_SHA256.equalsIgnoreCase(context.sourceHash())) {
            context.diagnostics().warning(
                    "LFB-RPGTOOL-CONTENT-0000",
                    SupportLevel.MANUAL_REQUIRED,
                    "RPGTool1 semantic conversion is enabled only for the verified corpus SHA; this variant remains an inspection candidate."
            );
            return;
        }

        int removedClasses = removeLegacyClasses(context.stagingDir());
        Files.deleteIfExists(context.stagingDir().resolve("mcmod.info"));
        int normalizedObjResources = normalizeObjResourceDirectory(context.stagingDir());

        JsonObject content = new JsonObject();
        content.addProperty("schemaVersion", 1);
        content.addProperty("sourceSha256", context.sourceHash());
        content.addProperty("namespace", NAMESPACE);

        JsonArray legacyMods = new JsonArray();
        context.metadata().mods().forEach(mod -> {
            JsonObject value = new JsonObject();
            value.addProperty("modid", mod.modId());
            value.addProperty("version", mod.version());
            legacyMods.add(value);
        });
        content.add("legacyMods", legacyMods);

        JsonArray items = new JsonArray();
        for (Sword sword : SWORDS) {
            items.add(swordDefinition(sword));
            writeObjItemModels(context, sword);
            context.recordRegistryIdentity("items", NAMESPACE + ":" + sword.id(), NAMESPACE + ":" + sword.id());
        }
        for (String wing : WINGS) {
            items.add(equippableDefinition(wing, "wing", 3000, 10.0F));
            writeGeneratedItemModels(context, wing);
            context.recordRegistryIdentity("items", NAMESPACE + ":" + wing, NAMESPACE + ":" + wing);
        }
        for (String circle : CIRCLES) {
            items.add(equippableDefinition(circle, "circle", 3000, 4.0F));
            writeGeneratedItemModels(context, circle);
            context.recordRegistryIdentity("items", NAMESPACE + ":" + circle, NAMESPACE + ":" + circle);
        }
        for (String material : MATERIALS) {
            items.add(simpleDefinition(material));
            writeGeneratedItemModels(context, material);
            context.recordRegistryIdentity("items", NAMESPACE + ":" + material, NAMESPACE + ":" + material);
        }
        content.add("items", items);

        Path manifest = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, GSON.toJson(content) + "\n", StandardCharsets.UTF_8);

        int promotedTranslations = promoteItemTranslations(context);

        context.diagnostics().info(
                "LFB-RPGTOOL-CONTENT-0001",
                SupportLevel.ADAPTED,
                "Converted exact RPGTool1 corpus into a loader-safe content candidate: removed "
                        + removedClasses + " legacy classes and emitted " + items.size() + " modern item definitions."
        );
        context.diagnostics().info(
                "LFB-RPGTOOL-OBJ-0001",
                SupportLevel.ADAPTED,
                "Mapped all 20 RPGTool weapon OBJ resources to the LegacyForgeBridge 1.21.11 special-model renderer; normalized "
                        + normalizedObjResources + " legacy items3D resources to lowercase items3d identifiers."
        );
        context.diagnostics().info(
                "LFB-RPGTOOL-LANG-0001",
                SupportLevel.ADAPTED,
                "Promoted " + promotedTranslations + " RPGTool legacy item-name translations to modern item.<namespace>.<path> keys for source locale zh_cn only; no zh_tw fallback is synthesized."
        );
        context.diagnostics().warning(
                "LFB-RPGTOOL-BEHAVIOR-0001",
                SupportLevel.RUNTIME_BRIDGE,
                "RPGTool gem socketing, skill-gem combat effects, wing jump/fall behavior, equipped wing/circle OBJ rendering, and legacy recipes are not yet semantically ported in this test slice. Registry identities, item properties, names, icons, and weapon OBJ rendering are testable."
        );
    }

    private static JsonObject swordDefinition(Sword sword) {
        JsonObject item = baseDefinition(sword.id(), "sword", "combat");
        item.addProperty("durability", sword.durability());
        item.addProperty("attackDamage", sword.damage());
        item.addProperty("attackSpeed", -2.4F);
        return item;
    }

    private static JsonObject equippableDefinition(String id, String kind, int durability, float armor) {
        JsonObject item = baseDefinition(id, kind, "combat");
        item.addProperty("durability", durability);
        item.addProperty("armor", armor);
        return item;
    }

    private static JsonObject simpleDefinition(String id) {
        return baseDefinition(id, "item", "ingredients");
    }

    private static JsonObject baseDefinition(String id, String kind, String tab) {
        JsonObject item = new JsonObject();
        item.addProperty("id", NAMESPACE + ":" + id);
        item.addProperty("kind", kind);
        item.addProperty("tab", tab);
        item.addProperty("descriptionKey", "item." + NAMESPACE + "." + id);
        return item;
    }

    private static int removeLegacyClasses(Path stagingDir) throws IOException {
        List<Path> classes = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(stagingDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .forEach(classes::add);
        }
        for (Path path : classes) {
            Files.deleteIfExists(path);
        }
        pruneEmptyDirectories(stagingDir);
        return classes.size();
    }

    /**
     * The original artifact uses the mixed-case directory {@code textures/items3D}. Modern
     * resource identifiers reject uppercase characters, so normalize it before item-model JSON is
     * emitted. The temporary two-step copy also works on case-insensitive Windows filesystems.
     */
    private static int normalizeObjResourceDirectory(Path stagingDir) throws IOException {
        Path source = stagingDir.resolve("assets/" + NAMESPACE + "/textures/items3D");
        if (!Files.isDirectory(source)) {
            return 0;
        }

        Path temporary = stagingDir.resolve(".lfb-rpgtool-items3d");
        deleteTree(temporary);
        Files.createDirectories(temporary);
        int copied = 0;
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted().toList()) {
                Path relative = source.relativize(path);
                Path target = temporary.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        }

        deleteTree(source);
        Path target = stagingDir.resolve("assets/" + NAMESPACE + "/textures/items3d");
        deleteTree(target);
        Files.createDirectories(target.getParent());
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return copied;
    }

    private static void pruneEmptyDirectories(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(root) || !Files.isDirectory(path)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(path)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void writeGeneratedItemModels(ConversionContext context, String id) throws IOException {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", NAMESPACE + ":items/" + id);
        model.add("textures", textures);
        writeJson(context.stagingDir().resolve("assets/" + NAMESPACE + "/models/item/" + id + ".json"), model);

        JsonObject itemFile = new JsonObject();
        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("type", "minecraft:model");
        itemModel.addProperty("model", NAMESPACE + ":item/" + id);
        itemFile.add("model", itemModel);
        writeJson(context.stagingDir().resolve("assets/" + NAMESPACE + "/items/" + id + ".json"), itemFile);
    }

    private static void writeObjItemModels(ConversionContext context, Sword sword) throws IOException {
        JsonObject base = new JsonObject();
        base.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", NAMESPACE + ":items/" + sword.id());
        base.add("textures", textures);
        writeJson(context.stagingDir().resolve("assets/" + NAMESPACE + "/models/item/" + sword.id() + "_base.json"), base);

        JsonObject special = new JsonObject();
        special.addProperty("type", "legacyforgebridge:obj");
        special.addProperty("model", NAMESPACE + ":textures/items3d/" + sword.objName() + ".obj");
        special.addProperty("texture", NAMESPACE + ":textures/items3d/" + sword.objName() + ".png");
        special.addProperty("scale", sword.scale());

        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("type", "minecraft:special");
        itemModel.addProperty("base", NAMESPACE + ":item/" + sword.id() + "_base");
        itemModel.add("model", special);

        JsonObject itemFile = new JsonObject();
        itemFile.add("model", itemModel);
        writeJson(context.stagingDir().resolve("assets/" + NAMESPACE + "/items/" + sword.id() + ".json"), itemFile);
    }

    private static int promoteItemTranslations(ConversionContext context) throws IOException {
        Path legacy = context.stagingDir().resolve("assets/" + NAMESPACE + "/lang/zh_CN.lang");
        if (!Files.isRegularFile(legacy)) {
            return 0;
        }

        Map<String, String> source = new LinkedHashMap<>();
        for (String line : Files.readAllLines(legacy, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator <= 0 || line.startsWith("#")) {
                continue;
            }
            source.put(line.substring(0, separator), line.substring(separator + 1));
        }

        int count = 0;
        Map<String, String> modern = new LinkedHashMap<>();
        for (String id : allItemIds()) {
            String value = source.get("item." + id + ".name");
            if (value != null) {
                modern.put("item." + NAMESPACE + "." + id, value);
                context.recordRegistryIdentity(
                        "translations",
                        "item." + id + ".name",
                        "item." + NAMESPACE + "." + id
                );
                count++;
            }
        }

        Path output = context.stagingDir().resolve("assets/" + NAMESPACE + "/lang/zh_cn.json");
        JsonObject translations = readObject(output);
        modern.forEach(translations::addProperty);
        writeJson(output, translations);
        return count;
    }

    private static List<String> allItemIds() {
        List<String> ids = new ArrayList<>();
        SWORDS.forEach(value -> ids.add(value.id()));
        ids.addAll(WINGS);
        ids.addAll(CIRCLES);
        ids.addAll(MATERIALS);
        return ids;
    }

    private static JsonObject readObject(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
    }

    private static void writeJson(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    private record Sword(String id, int durability, int damage, String objName, float scale) {
    }
}
