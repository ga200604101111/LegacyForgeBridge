package dev.longyu.legacyforgebridge.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.runtime.ConvertedContentRuntime;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client runtime for manifest-driven legacy equipment rendering.
 *
 * <p>No legacy mod ID is referenced here. Any converted candidate can attach an
 * {@code equipmentRender} object to an item in {@code converted-content.json}; the same renderer
 * then resolves the declared OBJ parts, textures, anchor and bounds-based fit.</p>
 */
public final class ConvertedEquipmentRenderRuntime {
    private static volatile Map<Identifier, Definition> definitions = Map.of();
    private static final Map<String, Optional<Identifier>> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private ConvertedEquipmentRenderRuntime() {
    }

    public static void initialize() {
        Map<Identifier, Definition> discovered = new LinkedHashMap<>();
        List<Item> renderedItems = new ArrayList<>();

        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            var manifestPath = container.findPath(ConvertedContentRuntime.MANIFEST_PATH);
            if (manifestPath.isEmpty()) {
                continue;
            }
            try {
                JsonObject root = readJson(manifestPath.get());
                JsonArray items = root.getAsJsonArray("items");
                if (items == null) {
                    continue;
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
                    discovered.put(itemId, definition);
                    if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                        renderedItems.add((Item) BuiltInRegistries.ITEM.getValue(itemId));
                    }
                }
            } catch (Exception exception) {
                LegacyForgeBridge.LOGGER.error(
                        "Failed to read converted equipment render metadata from {}",
                        container.getMetadata().getId(),
                        exception
                );
            }
        }

        definitions = Map.copyOf(discovered);
        TEXTURE_CACHE.clear();
        LegacyObjModel.clearCache();

        if (!renderedItems.isEmpty()) {
            ArmorRenderer.register(new ManifestArmorRenderer(), renderedItems.toArray(Item[]::new));
        }
        if (!definitions.isEmpty()) {
            LegacyForgeBridge.LOGGER.info(
                    "Registered generic converted equipment renderers: definitions={}, items={}",
                    definitions.size(),
                    renderedItems.size()
            );
        }
    }

    static Definition definition(Identifier itemId) {
        return definitions.get(itemId);
    }

    private static Definition parse(JsonObject object) {
        String anchor = string(object, "anchor", "root");
        boolean autoCenter = bool(object, "autoCenter", true);
        float fit = number(object, "fit", 1.0F);
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
        return new Definition(anchor, autoCenter, fit, List.copyOf(parts));
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
            Definition definition = definitions.get(itemId);
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
                        RenderTypes.entityCutoutNoCull(part.texture()),
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
                // root/entity-local coordinates
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

    record Definition(String anchor, boolean autoCenter, float fit, List<Part> parts) {
    }

    record Part(Identifier model, List<Identifier> textures) {
    }

    private record LoadedPart(LegacyObjModel model, Identifier texture) {
    }
}
