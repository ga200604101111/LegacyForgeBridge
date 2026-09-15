package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.GeneratedModEntrypointPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedContentRuntime;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client runtime for manifest-driven legacy equipment rendering. */
public final class ConvertedEquipmentRenderRuntime {
    private static final Map<Identifier, Definition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Identifier>> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> INITIALIZED_MODS = ConcurrentHashMap.newKeySet();

    private ConvertedEquipmentRenderRuntime() {
    }

    /** Compatibility scan for alpha.18-and-older resource-only candidates. */
    public static void initialize() {
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.findPath(ConvertedContentRuntime.MANIFEST_PATH).isEmpty()) {
                continue;
            }
            if (container.findPath(GeneratedModEntrypointPass.MARKER_PATH).isPresent()) {
                continue;
            }
            initializeContainer(container, "legacy-compat-scan");
        }
    }

    /** Called by the generated client entrypoint contained by a converted mod JAR. */
    public static void initializeMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) {
            LegacyForgeBridge.LOGGER.error("Converted client bootstrap could not resolve its Fabric container: {}", modId);
            return;
        }
        initializeContainer(container, "converted-mod-entrypoint");
    }

    private static void initializeContainer(ModContainer container, String source) {
        String modId = container.getMetadata().getId();
        if (!INITIALIZED_MODS.add(modId)) {
            return;
        }

        var manifestPath = container.findPath(ConvertedContentRuntime.MANIFEST_PATH);
        if (manifestPath.isEmpty()) {
            INITIALIZED_MODS.remove(modId);
            return;
        }

        List<Item> renderedItems = new ArrayList<>();
        int discoveredDefinitions = 0;
        try {
            JsonObject root = readJson(manifestPath.get());
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) {
                return;
            }
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject itemDefinition = element.getAsJsonObject();
                JsonObject render = object(itemDefinition, "equipmentRender");
                if (render == null) {
                    continue;
                }
                Identifier itemId = Identifier.parse(requiredString(itemDefinition, "id"));
                Definition definition = parse(render);
                if (definition.parts().isEmpty()) {
                    continue;
                }
                DEFINITIONS.put(itemId, definition);
                discoveredDefinitions++;
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    renderedItems.add((Item) BuiltInRegistries.ITEM.getValue(itemId));
                }
            }
        } catch (Exception exception) {
            INITIALIZED_MODS.remove(modId);
            LegacyForgeBridge.LOGGER.error(
                    "Failed to read converted equipment render metadata from {}",
                    modId,
                    exception
            );
            return;
        }

        TEXTURE_CACHE.clear();
        LegacyObjModel.clearCache();

        if (!renderedItems.isEmpty()) {
            ArmorRenderer.register(new ManifestArmorRenderer(), renderedItems.toArray(Item[]::new));
        }
        LegacyForgeBridge.LOGGER.info(
                "Converted mod registered its equipment presentation: mod={}, source={}, definitions={}, items={}",
                modId,
                source,
                discoveredDefinitions,
                renderedItems.size()
        );
    }

    static Definition definition(Identifier itemId) {
        return DEFINITIONS.get(itemId);
    }

    private static Definition parse(JsonObject object) {
        String anchor = string(object, "anchor", "root");
        boolean autoCenter = bool(object, "autoCenter", true);
        float fit = number(object, "fit", 1.0F);
        boolean translucent = bool(object, "translucent", false);
        List<Part> parts = new ArrayList<>();
        JsonArray partArray = object.getAsJsonArray("parts");
        if (partArray != null) {
            for (JsonElement element : partArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject partObject = element.getAsJsonObject();
                Identifier model = Identifier.parse(requiredString(partObject, "model"));
                List<Identifier> textures = new ArrayList<>();
                JsonArray textureArray = partObject.getAsJsonArray("textures");
                if (textureArray != null) {
                    for (JsonElement texture : textureArray) {
                        if (texture.isJsonPrimitive()) {
                            textures.add(Identifier.parse(texture.getAsString()));
                        }
                    }
                }
                if (partObject.has("texture") && partObject.get("texture").isJsonPrimitive()) {
                    textures.addFirst(Identifier.parse(partObject.get("texture").getAsString()));
                }
                parts.add(new Part(model, List.copyOf(textures)));
            }
        }
        return new Definition(anchor, autoCenter, fit, translucent, List.copyOf(parts));
    }

    private static final class ManifestArmorRenderer implements ArmorRenderer {
        @Override
        public void render(
                PoseStack matrices,
                SubmitNodeCollector queue,
                ItemStack stack,
                HumanoidRenderState entityState,
                EquipmentSlot slot,
                int light,
                HumanoidModel<HumanoidRenderState> contextModel
        ) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            Definition definition = DEFINITIONS.get(itemId);
            if (definition == null) {
                return;
            }

            List<LoadedPart> loaded = new ArrayList<>();
            LegacyObjModel.Bounds groupBounds = null;
            for (Part part : definition.parts()) {
                LegacyObjModel model = LegacyObjModel.loadCached(part.model());
                if (model == null) {
                    continue;
                }
                Optional<Identifier> texture = resolveTexture(itemId, part);
                if (texture.isEmpty()) {
                    continue;
                }
                loaded.add(new LoadedPart(model, texture.get()));
                groupBounds = groupBounds == null ? model.bounds() : groupBounds.include(model.bounds());
            }
            if (loaded.isEmpty() || groupBounds == null) {
                return;
            }

            float originX = definition.autoCenter() ? groupBounds.centerX() : 0.0F;
            float originY = definition.autoCenter() ? groupBounds.centerY() : 0.0F;
            float originZ = definition.autoCenter() ? groupBounds.centerZ() : 0.0F;
            float renderScale = 1.0F;
            if (groupBounds.maxExtent() > 1.0E-6F && definition.fit() > 0.0F) {
                renderScale = definition.fit() / groupBounds.maxExtent();
            }

            matrices.pushPose();
            applyAnchor(definition.anchor(), contextModel, matrices);
            for (LoadedPart part : loaded) {
                float finalScale = renderScale;
                queue.submitCustomGeometry(
                        matrices,
                        definition.translucent()
                                ? RenderTypes.entityTranslucent(part.texture())
                                : RenderTypes.entityCutoutNoCull(part.texture()),
                        (pose, buffer) -> part.model().render(
                                pose,
                                buffer,
                                light,
                                OverlayTexture.NO_OVERLAY,
                                finalScale,
                                originX,
                                originY,
                                originZ
                        )
                );
            }
            matrices.popPose();
        }
    }

    private static void applyAnchor(
            String anchor,
            HumanoidModel<HumanoidRenderState> model,
            PoseStack matrices
    ) {
        switch (anchor) {
            case "head" -> model.head.translateAndRotate(matrices);
            case "body" -> model.body.translateAndRotate(matrices);
            case "left_arm" -> model.leftArm.translateAndRotate(matrices);
            case "right_arm" -> model.rightArm.translateAndRotate(matrices);
            case "left_leg" -> model.leftLeg.translateAndRotate(matrices);
            case "right_leg" -> model.rightLeg.translateAndRotate(matrices);
            default -> {
            }
        }
    }

    private static Optional<Identifier> resolveTexture(Identifier itemId, Part part) {
        String key = itemId + "|" + part.model();
        return TEXTURE_CACHE.computeIfAbsent(key, ignored -> {
            for (Identifier candidate : part.textures()) {
                if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()) {
                    return Optional.of(candidate);
                }
            }
            LegacyForgeBridge.LOGGER.warn(
                    "No converted equipment texture candidate exists for item {} model {} candidates={}",
                    itemId,
                    part.model(),
                    part.textures()
            );
            return Optional.empty();
        });
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing converted render string: " + key);
        }
        return value.getAsString();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }

    record Definition(String anchor, boolean autoCenter, float fit, boolean translucent, List<Part> parts) {
    }

    record Part(Identifier model, List<Identifier> textures) {
    }

    private record LoadedPart(LegacyObjModel model, Identifier texture) {
    }
}
