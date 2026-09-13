package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Binding;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Context;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Draw;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Operation;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cross-mod source renderer -> ordinary modern item-model context selection and OBJ transforms. */
public final class LegacyItemRenderPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @Override public String id() { return "legacy-item-render-semantics"; }

    @Override public void apply(ConversionContext context) throws IOException {
        var analysis = new LegacyItemRenderAnalyzer().analyze(context.sourceJar());
        for (String message : analysis.diagnostics()) context.diagnostics().warning(
                "LFB-CONVERT-ITEM-RENDER-0002", SupportLevel.MANUAL_REQUIRED, message);
        int replaced = 0;
        Path assets = context.stagingDir().resolve("assets");
        if (Files.isDirectory(assets)) {
            List<Path> files;
            try (var walk = Files.walk(assets)) {
                files = walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> { Path r = assets.relativize(p); return r.getNameCount() >= 3 && r.getName(1).toString().equals("items"); })
                        .sorted().toList();
            }
            for (Path file : files) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject old = object(root, "model"), special = old == null ? null : object(old, "model");
                if (old == null || !"minecraft:special".equals(string(old, "type")) || special == null
                        || !"legacyforgebridge:obj".equals(string(special, "type"))) continue;
                List<Binding> candidates = analysis.bindings().stream().filter(b -> matches(b, special)).toList();
                if (candidates.isEmpty()) continue;
                Binding source = candidates.getFirst();
                if (candidates.stream().anyMatch(b -> !b.contexts().equals(source.contexts()))) {
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0003", SupportLevel.MANUAL_REQUIRED,
                            "Ambiguous source renderer binding for " + assets.relativize(file) + "; existing model retained.");
                    continue;
                }
                String base = string(old, "base");
                if (base == null || !supported(source)) {
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0004", SupportLevel.MANUAL_REQUIRED,
                            "Incomplete/context-dependent helper semantics for " + source.rendererClass() + "; existing model retained.");
                    continue;
                }
                boolean resourcesExist = source.contexts().values().stream().flatMap(c -> c.draws().stream())
                        .allMatch(d -> exists(context.stagingDir(), canonical(d.model())) && exists(context.stagingDir(), canonical(d.texture())));
                if (!resourcesExist) {
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0005", SupportLevel.MANUAL_REQUIRED,
                            "Source renderer has missing modern resources: " + source.rendererClass()); continue;
                }
                JsonObject nativeIcon = new JsonObject(); nativeIcon.addProperty("type", "minecraft:model"); nativeIcon.addProperty("model", base);
                JsonObject select = new JsonObject(); select.addProperty("type", "minecraft:select"); select.addProperty("property", "minecraft:display_context");
                JsonArray cases = new JsonArray();
                addCase(cases, List.of("gui"), source.contexts().get("INVENTORY"), "INVENTORY", old, nativeIcon);
                addCase(cases, List.of("ground", "fixed"), source.contexts().get("ENTITY"), "ENTITY", old, nativeIcon);
                addCase(cases, List.of("firstperson_righthand", "firstperson_lefthand"), source.contexts().get("EQUIPPED_FIRST_PERSON"), "EQUIPPED_FIRST_PERSON", old, nativeIcon);
                addCase(cases, List.of("thirdperson_righthand", "thirdperson_lefthand"), source.contexts().get("EQUIPPED"), "EQUIPPED", old, nativeIcon);
                select.add("cases", cases); select.add("fallback", nativeIcon.deepCopy()); root.add("model", select);
                Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8); replaced++;
            }
        }
        Path report = context.stagingDir().resolve("legacyforgebridge/item-render-analysis.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, GSON.toJson(analysis) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-ITEM-RENDER-0001", SupportLevel.ADAPTED,
                "Source IItemRenderer bindings=" + analysis.bindings().size() + ", modern context-select models=" + replaced
                        + ". Source operations are preserved in order; unknown runtime behavior is not invented.");
    }

    private static boolean supported(Binding b) {
        for (String key : List.of("INVENTORY", "ENTITY", "EQUIPPED", "EQUIPPED_FIRST_PERSON")) {
            Context c = b.contexts().get(key); if (c == null) return false;
            if (key.equals("INVENTORY") && c.custom()) return false;
            if (key.equals("ENTITY") && c.custom() && (c.helpers().getOrDefault("BLOCK_3D", false)
                    || !c.helpers().getOrDefault("ENTITY_BOBBING", false)
                    || !c.helpers().getOrDefault("ENTITY_ROTATION", false))) return false;
        }
        return true;
    }

    private static boolean matches(Binding binding, JsonObject special) {
        String model = string(special, "model"), texture = string(special, "texture");
        return binding.contexts().values().stream().flatMap(c -> c.draws().stream())
                .anyMatch(d -> canonical(d.model()).equals(model) && canonical(d.texture()).equals(texture));
    }
    private static void addCase(JsonArray cases, List<String> names, Context context, String legacyType,
                                JsonObject template, JsonObject nativeIcon) {
        JsonObject entry = new JsonObject(); JsonArray when = new JsonArray(); names.forEach(when::add); entry.add("when", when);
        if (!context.custom()) entry.add("model", nativeIcon.deepCopy());
        else {
            JsonArray models = new JsonArray();
            for (Draw draw : context.draws()) {
                JsonObject outer = template.deepCopy(); JsonObject special = outer.getAsJsonObject("model");
                special.addProperty("model", canonical(draw.model())); special.addProperty("texture", canonical(draw.texture()));
                special.addProperty("scale", 1.0F); special.addProperty("centered", true);
                List<Operation> operations = helperOperations(legacyType, context);
                operations.addAll(draw.operations()); special.add("transforms", GSON.toJsonTree(operations)); models.add(outer);
            }
            if (models.size() == 1) entry.add("model", models.get(0));
            else { JsonObject composite = new JsonObject(); composite.addProperty("type", "minecraft:composite"); composite.add("models", models); entry.add("model", composite); }
        }
        cases.add(entry);
    }

    /** Prefixes from Forge 1.7.10 ForgeHooksClient, not corpus-specific visual tuning. */
    private static List<Operation> helperOperations(String type, Context context) {
        List<Operation> result = new ArrayList<>();
        if (type.equals("ENTITY")) result.add(Operation.of("scale", 0.5F, 0.5F, 0.5F));
        else if (type.startsWith("EQUIPPED")) {
            if (context.helpers().getOrDefault("EQUIPPED_BLOCK", false)) result.add(Operation.of("translate", -0.5F, -0.5F, -0.5F));
            else {
                result.add(Operation.of("translate", 0, -0.3F, 0));
                result.add(Operation.of("scale", 1.5F, 1.5F, 1.5F));
                result.add(Operation.of("rotate", 50, 0, 1, 0));
                result.add(Operation.of("rotate", 335, 0, 0, 1));
                result.add(Operation.of("translate", -0.9375F, -0.0625F, 0));
            }
        }
        return result;
    }
    private static String canonical(String id) { return id.toLowerCase(Locale.ROOT); }
    private static boolean exists(Path staging, String id) {
        int split = id.indexOf(':'); if (split <= 0 || id.contains("..") || id.indexOf('\\') >= 0) return false;
        Path assets = staging.resolve("assets").toAbsolutePath().normalize();
        Path resource = assets.resolve(id.substring(0, split)).resolve(id.substring(split + 1)).normalize();
        return resource.startsWith(assets) && Files.isRegularFile(resource);
    }
    private static String string(JsonObject object, String key) {
        JsonElement e = object.get(key); return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
    private static JsonObject object(JsonObject parent, String key) {
        JsonElement e = parent.get(key); return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
