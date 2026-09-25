package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockInventoryRenderModeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyFmlModAnnotationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Generic GameRegistry identity extraction for ordinary Forge 1.7.x content. */
public final class GenericContentPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "generic-game-registry-content";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        LegacyRegistryAnalyzer analyzer = new LegacyRegistryAnalyzer();
        LegacyRegistryAnalyzer.Analysis analysis = analyzer.analyze(context.sourceJar());
        // No GameRegistry content is a valid result for resource-only or wrapper-only legacy JARs.
        // Do not downgrade such candidates merely because the generic content pass had nothing to emit.
        if (analysis.registrations().isEmpty()) return;
        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning("LFB-CONVERT-REGISTRY-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }
        Map<String, LegacyBlockInventoryRenderModeAnalyzer.Mode> blockInventoryModes = new LinkedHashMap<>();
        for (var rule : new LegacyBlockInventoryRenderModeAnalyzer().analyze(context.sourceJar()).rules()) {
            blockInventoryModes.putIfAbsent(rule.registryName(), rule.mode());
        }

        String namespace = context.metadata().fabricId();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("namespace", namespace);
        LegacyFmlModAnnotationAnalyzer.Analysis fmlIdentity =
                new LegacyFmlModAnnotationAnalyzer().analyze(context.sourceJar());
        for (String diagnostic : fmlIdentity.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-FML-IDENTITY-0002",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic
            );
        }

        JsonArray legacyMods = new JsonArray();
        Set<String> emittedModIds = new LinkedHashSet<>();
        int annotationVersions = 0;
        int mismatchedMetadataVersions = 0;
        for (var mod : context.metadata().mods()) {
            var annotation = fmlIdentity.find(mod.modId()).orElse(null);
            String networkVersion = annotation != null && !annotation.version().isBlank()
                    ? annotation.version()
                    : mod.version();
            JsonObject value = new JsonObject();
            value.addProperty("modid", mod.modId());
            value.addProperty("version", mod.version());
            value.addProperty("networkVersion", networkVersion);
            value.addProperty("networkVersionSource",
                    annotation == null ? context.metadata().metadataSource() : "forge-mod-annotation");
            if (annotation != null) {
                annotationVersions++;
                value.addProperty("sourceModClass", annotation.sourceClass());
                value.addProperty("acceptableRemoteVersions", annotation.acceptableRemoteVersions());
                value.addProperty("acceptedMinecraftVersions", annotation.acceptedMinecraftVersions());
                if (!networkVersion.equals(mod.version())) mismatchedMetadataVersions++;
            }
            legacyMods.add(value);
            emittedModIds.add(mod.modId().toLowerCase(Locale.ROOT));
        }
        for (var annotation : fmlIdentity.mods()) {
            if (!emittedModIds.add(annotation.modId().toLowerCase(Locale.ROOT))) continue;
            JsonObject value = new JsonObject();
            value.addProperty("modid", annotation.modId());
            value.addProperty("version", annotation.version());
            value.addProperty("networkVersion", annotation.version());
            value.addProperty("networkVersionSource", "forge-mod-annotation");
            value.addProperty("sourceModClass", annotation.sourceClass());
            value.addProperty("acceptableRemoteVersions", annotation.acceptableRemoteVersions());
            value.addProperty("acceptedMinecraftVersions", annotation.acceptedMinecraftVersions());
            legacyMods.add(value);
            annotationVersions++;
        }
        root.add("legacyMods", legacyMods);
        if (annotationVersions > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-FML-IDENTITY-0001",
                    SupportLevel.ADAPTED,
                    "Recovered exact Forge @Mod network identities: mods=" + annotationVersions
                            + ", metadataVersionDifferences=" + mismatchedMetadataVersions
                            + ". FML handshake advertisement uses the annotation version exactly."
            );
        }

        Set<String> itemPaths = new LinkedHashSet<>();
        Set<String> blockPaths = new LinkedHashSet<>();
        JsonArray blocks = new JsonArray();
        for (LegacyRegistryAnalyzer.Registration registration : analysis.blocks()) {
            String path = modernPath(registration.registryName());
            if (!blockPaths.add(path)) {
                context.diagnostics().warning("LFB-CONVERT-REGISTRY-0003", SupportLevel.MANUAL_REQUIRED,
                        "Multiple legacy blocks normalize to modern path " + path + "; identity is ambiguous.");
                continue;
            }
            String id = namespace + ":" + path;
            JsonObject block = new JsonObject();
            block.addProperty("id", id);
            block.addProperty("descriptionKey", legacyTranslation(context, "tile." + registration.registryName() + ".name"));
            if (registration.implementationClass() != null) block.addProperty("sourceClass", registration.implementationClass());
            if (registration.itemBlockClass() != null) block.addProperty("sourceItemBlockClass", registration.itemBlockClass());
            block.addProperty("legacyRegistryName", registration.registryName());
            blocks.add(block);
            recordIdentity(context, "blocks", registration, id);
            recordIdentity(context, "items", registration, id); // GameRegistry.registerBlock also creates an ItemBlock identity.
            writeFallbackBlockModels(context, id, registration.registryName(), blockInventoryModes.get(registration.registryName()));
        }
        root.add("blocks", blocks);

        JsonArray items = new JsonArray();
        for (LegacyRegistryAnalyzer.Registration registration : analysis.items()) {
            String path = modernPath(registration.registryName());
            if (!itemPaths.add(path)) {
                context.diagnostics().warning("LFB-CONVERT-REGISTRY-0004", SupportLevel.MANUAL_REQUIRED,
                        "Multiple legacy items normalize to modern path " + path + "; identity is ambiguous.");
                continue;
            }
            String id = namespace + ":" + path;
            String kind = analyzer.classifyItem(registration.implementationClass());
            JsonObject item = new JsonObject();
            item.addProperty("id", id);
            item.addProperty("kind", kind);
            item.addProperty("descriptionKey", legacyTranslation(context, "item." + registration.registryName() + ".name"));
            item.addProperty("legacyRegistryName", registration.registryName());
            if (registration.implementationClass() != null) item.addProperty("sourceClass", registration.implementationClass());
            if (registration.constructorDescriptor() != null) {
                item.addProperty("sourceConstructor", registration.constructorDescriptor());
                JsonArray constructorArgs=new JsonArray();
                for(var argument:registration.constructorArguments()){
                    Object value=argument.value();
                    if(value==null)constructorArgs.add(com.google.gson.JsonNull.INSTANCE);
                    else if(value instanceof Number number)constructorArgs.add(number);
                    else if(value instanceof Boolean bool)constructorArgs.add(bool);
                    else constructorArgs.add(String.valueOf(value));
                }
                item.add("sourceConstructorArgs",constructorArgs);
            }
            items.add(item);
            recordIdentity(context, "items", registration, id);
            writeFallbackItemModels(context, id, registration.registryName(), kind);
        }
        root.add("items", items);

        Path manifest = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info("LFB-CONVERT-REGISTRY-0001", SupportLevel.ADAPTED,
                "Proved generic GameRegistry content identities from lifecycle-reachable bytecode: items="
                        + items.size() + ", blocks=" + blocks.size() + ". No mod-name content table was used.");
        if (blocks.size() > 0) {
            context.diagnostics().warning("LFB-CONVERT-BLOCK-0001", SupportLevel.RUNTIME_BRIDGE,
                    "Block registry identities are generated, but custom legacy block state/behavior/render semantics still require the common Block/BlockEntity conversion stages.");
        }
    }

    private static void recordIdentity(ConversionContext context, String category,
                                       LegacyRegistryAnalyzer.Registration registration, String modernId) {
        String namespace = registration.legacyNamespace();
        if (namespace == null || namespace.isBlank()) namespace = context.metadata().primary().modId();
        String legacy = namespace + ":" + registration.registryName();
        context.recordRegistryIdentity(category, legacy, modernId);
        String lower = legacy.toLowerCase(Locale.ROOT);
        if (!lower.equals(legacy)) context.recordRegistryIdentity(category, lower, modernId);
    }

    private static String modernPath(String legacyName) {
        String path = legacyName == null ? "" : legacyName.trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_")
                .replaceAll("_+", "_");
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isBlank()) path = "legacy_content";
        return path;
    }

    private static String legacyTranslation(ConversionContext context, String legacyKey) {
        return "lfb.converted." + context.metadata().fabricId() + "." + legacyKey;
    }

    private static void writeFallbackItemModels(ConversionContext context, String id, String legacyName, String kind) throws IOException {
        String namespace = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        Texture texture = findTexture(context.stagingDir(), "items", legacyName);
        if (texture == null) return;
        Path model = context.stagingDir().resolve("assets/" + namespace + "/models/item/" + path + ".json");
        Path item = context.stagingDir().resolve("assets/" + namespace + "/items/" + path + ".json");
        Files.createDirectories(model.getParent()); Files.createDirectories(item.getParent());
        Files.writeString(model, "{\n  \"parent\": \"" + fallbackItemParent(kind) + "\",\n  \"textures\": {\"layer0\": \"" + texture.resource() + "\"}\n}\n", StandardCharsets.UTF_8);
        LegacyPresentationOwnership.record(context.stagingDir(), model);
        Files.writeString(item, "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"" + namespace + ":item/" + path + "\"}\n}\n", StandardCharsets.UTF_8);
    }

    private static void writeFallbackBlockModels(ConversionContext context, String id, String legacyName,
                                                 LegacyBlockInventoryRenderModeAnalyzer.Mode inventoryMode) throws IOException {
        String namespace = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        Texture texture = findTexture(context.stagingDir(), "blocks", legacyName);
        if (texture == null) return;
        Path model = context.stagingDir().resolve("assets/" + namespace + "/models/block/" + path + ".json");
        Path state = context.stagingDir().resolve("assets/" + namespace + "/blockstates/" + path + ".json");
        Path itemModel = context.stagingDir().resolve("assets/" + namespace + "/models/item/" + path + ".json");
        Path item = context.stagingDir().resolve("assets/" + namespace + "/items/" + path + ".json");
        Files.createDirectories(model.getParent()); Files.createDirectories(state.getParent()); Files.createDirectories(itemModel.getParent()); Files.createDirectories(item.getParent());
        Files.writeString(model, "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\"all\": \"" + texture.resource() + "\"}\n}\n", StandardCharsets.UTF_8);
        LegacyPresentationOwnership.record(context.stagingDir(), model);
        Files.writeString(state, "{\n  \"variants\": {\"\": {\"model\": \"" + namespace + ":block/" + path + "\"}}\n}\n", StandardCharsets.UTF_8);
        Texture itemTexture = findTexture(context.stagingDir(), "items", legacyName);
        // A same-named items/ texture is not evidence that a BlockItem was flat in 1.7.10.
        // Use it only when vanilla RenderBlocks or the registered Forge handler proves 2D inventory.
        if (inventoryMode == LegacyBlockInventoryRenderModeAnalyzer.Mode.FLAT_2D && itemTexture != null) {
            Files.writeString(itemModel, "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"" + itemTexture.resource() + "\"}\n}\n", StandardCharsets.UTF_8);
            LegacyPresentationOwnership.record(context.stagingDir(), itemModel);
        } else {
            Files.writeString(itemModel, "{\n  \"parent\": \"" + namespace + ":block/" + path + "\"\n}\n", StandardCharsets.UTF_8);
        }
        Files.writeString(item, "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"" + namespace + ":item/" + path + "\"}\n}\n", StandardCharsets.UTF_8);
    }

    private static String fallbackItemParent(String kind) {
        return switch (kind) {
            case "sword", "hoe", "pickaxe", "axe", "shovel", "tool" -> "minecraft:item/handheld";
            case "bow" -> "minecraft:item/bow";
            default -> "minecraft:item/generated";
        };
    }

    private static Texture findTexture(Path staging, String directory, String legacyName) throws IOException {
        Path assetsRoot = staging.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) return null;
        String target = legacyName.toLowerCase(Locale.ROOT) + ".png";
        try (Stream<Path> stream = Files.walk(assetsRoot)) {
            List<Path> exact = stream.filter(Files::isRegularFile).filter(path -> {
                String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                return normalized.contains("/textures/" + directory + "/") && path.getFileName().toString().toLowerCase(Locale.ROOT).equals(target);
            }).toList();
            if (exact.size() != 1) return null;
            Path texture = exact.getFirst();
            Path relative = assetsRoot.relativize(texture);
            if (relative.getNameCount() < 4) return null;
            String assetNamespace = relative.getName(0).toString().toLowerCase(Locale.ROOT);
            String resourcePath = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
            resourcePath = resourcePath.substring(0, resourcePath.length() - 4);
            return new Texture(assetNamespace + ":" + resourcePath);
        }
    }

    private record Texture(String resource) { }
}
