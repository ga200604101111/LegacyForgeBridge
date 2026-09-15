package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Low-level rendering adapter for presentation definitions compiled into generated mod bytecode. */
public final class GeneratedEquipmentSupport {
    private static final Map<Identifier, Definition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Identifier>> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Item>> MOD_ITEMS = new ConcurrentHashMap<>();
    private static final Set<String> FINISHED_MODS = ConcurrentHashMap.newKeySet();

    private GeneratedEquipmentSupport() { }

    /** A resource pack can replace a texture or repair a previously missing texture. */
    public static void clearResourceCache() { TEXTURE_CACHE.clear(); }

    public static void register(String itemValue, String anchor, boolean autoCenter, float fit, boolean translucent, String[] models, String[] textureSets) {
        Identifier itemId = Identifier.parse(itemValue);
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < models.length; i++) {
            List<Identifier> textures = new ArrayList<>();
            if (i < textureSets.length && !textureSets[i].isBlank()) {
                for (String value : textureSets[i].split("\\|")) {
                    if (!value.isBlank()) textures.add(Identifier.parse(value));
                }
            }
            parts.add(new Part(Identifier.parse(models[i]), List.copyOf(textures)));
        }
        if (parts.isEmpty()) return;
        DEFINITIONS.put(itemId, new Definition(anchor, autoCenter, fit, translucent, List.copyOf(parts)));
        if (BuiltInRegistries.ITEM.containsKey(itemId)) {
            Item item = (Item) BuiltInRegistries.ITEM.getValue(itemId);
            MOD_ITEMS.computeIfAbsent(itemId.getNamespace(), ignored -> new ArrayList<>()).add(item);
        }
    }

    public static void finishMod(String modId) {
        if (!FINISHED_MODS.add(modId)) return;
        clearResourceCache(); LegacyObjModel.clearCache();
        List<Item> items = MOD_ITEMS.getOrDefault(modId, List.of());
        if (!items.isEmpty()) ArmorRenderer.register(new GeneratedArmorRenderer(), items.toArray(Item[]::new));
        LegacyForgeBridge.LOGGER.info("Generated converted equipment presentation initialized: mod={}, definitions={}, items={}", modId,
                DEFINITIONS.keySet().stream().filter(id -> id.getNamespace().equals(modId)).count(), items.size());
    }

    private static final class GeneratedArmorRenderer implements ArmorRenderer {
        @Override public void render(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, HumanoidRenderState entityState, EquipmentSlot slot, int light, HumanoidModel<HumanoidRenderState> contextModel) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()); Definition d = DEFINITIONS.get(itemId); if (d == null) return;
            List<LoadedPart> loaded = new ArrayList<>(); LegacyObjModel.Bounds bounds = null;
            for (Part part : d.parts()) { LegacyObjModel model = LegacyObjModel.loadCached(part.model()); if (model == null) continue; Optional<Identifier> texture = resolveTexture(itemId, part); if (texture.isEmpty()) continue; loaded.add(new LoadedPart(model, texture.get())); bounds = bounds == null ? model.bounds() : bounds.include(model.bounds()); }
            if (loaded.isEmpty() || bounds == null) return;
            float ox=d.autoCenter()?bounds.centerX():0, oy=d.autoCenter()?bounds.centerY():0, oz=d.autoCenter()?bounds.centerZ():0;
            float scale=bounds.maxExtent()>1.0E-6F&&d.fit()>0?d.fit()/bounds.maxExtent():1;
            matrices.pushPose(); applyAnchor(d.anchor(), contextModel, matrices);
            for (LoadedPart part : loaded) { float fs=scale; queue.submitCustomGeometry(matrices, d.translucent()?RenderTypes.entityTranslucent(part.texture()):RenderTypes.entityCutoutNoCull(part.texture()), (pose,buffer)->part.model().render(pose,buffer,light,OverlayTexture.NO_OVERLAY,fs,ox,oy,oz)); }
            matrices.popPose();
        }
    }

    private static void applyAnchor(String anchor, HumanoidModel<HumanoidRenderState> model, PoseStack matrices) {
        switch (anchor) { case "head" -> model.head.translateAndRotate(matrices); case "body" -> model.body.translateAndRotate(matrices); case "left_arm" -> model.leftArm.translateAndRotate(matrices); case "right_arm" -> model.rightArm.translateAndRotate(matrices); case "left_leg" -> model.leftLeg.translateAndRotate(matrices); case "right_leg" -> model.rightLeg.translateAndRotate(matrices); default -> { } }
    }

    private static Optional<Identifier> resolveTexture(Identifier itemId, Part part) {
        String key=itemId+"|"+part.model(); return TEXTURE_CACHE.computeIfAbsent(key, ignored -> { for (Identifier texture : part.textures()) if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) return Optional.of(texture); LegacyForgeBridge.LOGGER.warn("No generated equipment texture exists for item {} model {} candidates={}",itemId,part.model(),part.textures()); return Optional.empty(); });
    }
    private record Definition(String anchor, boolean autoCenter, float fit, boolean translucent, List<Part> parts) { }
    private record Part(Identifier model, List<Identifier> textures) { }
    private record LoadedPart(LegacyObjModel model, Identifier texture) { }
}
