package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native Fabric ArmorRenderer adapter. The converted JAR owns the animation code and bindings. */
public final class SourceEquipmentSupport {
    private static final Map<Identifier, Definition> DEFINITIONS = new LinkedHashMap<>();
    private SourceEquipmentSupport() { }

    public static void register(String idValue, int legacySlot, LegacyEquipmentProgram program) {
        Identifier id = Identifier.parse(idValue);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Missing equipment item " + id);
        EquipmentSlot slot = switch (legacySlot) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            default -> throw new IllegalArgumentException("Invalid legacy armor slot " + legacySlot);
        };
        if (DEFINITIONS.containsKey(id)) return;
        Definition definition = new Definition(slot, program);
        DEFINITIONS.put(id, definition);
        ArmorRenderer.register(new Renderer(definition), (Item)BuiltInRegistries.ITEM.getValue(id));
        LegacyForgeBridge.LOGGER.debug("Registered source-compiled equipment {} slot={}", id, slot);
    }

    private record Definition(EquipmentSlot slot, LegacyEquipmentProgram program) { }
    private record Renderer(Definition definition) implements ArmorRenderer {
        @Override
        public void render(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack,
                           HumanoidRenderState state, EquipmentSlot slot, int light,
                           HumanoidModel<HumanoidRenderState> contextModel) {
            if (slot != definition.slot()) return;
            // ModelBiped#render started in the living model root, NOT the rotated body ModelPart.
            // Preserve authored OBJ pivots, scale and the source's feet/shoulder translations.
            float[] inputs = {state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks,
                    state.yRot, state.xRot, 0.0625F};
            DrawingSink sink = new DrawingSink(matrices, queue, light);
            matrices.pushPose();
            try {
                definition.program().render(sink, inputs, state.isCrouching);
            } finally {
                sink.unwind();
                matrices.popPose();
            }
        }
    }

    private static final class DrawingSink implements LegacyEquipmentProgram.Sink {
        private final PoseStack matrices;
        private final SubmitNodeCollector queue;
        private final int light;
        private int depth;
        private DrawingSink(PoseStack matrices, SubmitNodeCollector queue, int light) {
            this.matrices = matrices; this.queue = queue; this.light = light;
        }
        @Override public void push() { matrices.pushPose(); depth++; }
        @Override public void pop() {
            if (depth == 0) throw new IllegalStateException("Equipment pose stack underflow");
            matrices.popPose(); depth--;
        }
        void unwind() { while (depth > 0) pop(); }
        @Override public void translate(float x, float y, float z) { finite(x,y,z); matrices.translate(x,y,z); }
        @Override public void scale(float x, float y, float z) { finite(x,y,z); matrices.scale(x,y,z); }
        @Override public void rotate(float degrees, float x, float y, float z) {
            finite(degrees,x,y,z);
            Vector3f axis = new Vector3f(x,y,z);
            if (axis.lengthSquared() < 1.0E-12F) throw new IllegalArgumentException("Zero equipment rotation axis");
            matrices.mulPose(new Quaternionf().rotationAxis((float)Math.toRadians(degrees), axis.normalize()));
        }
        @Override public void draw(String model, String texture, boolean lighting, boolean cull, boolean translucent) {
            LegacyObjModel mesh = LegacyObjModel.loadCached(Identifier.parse(model));
            if (mesh == null) return;
            Identifier material = Identifier.parse(texture);
            // Keep the world's lightmap. glDisable(GL_LIGHTING) alone is NOT proof of fullbright.
            // Its old fixed-function diffuse-lighting distinction is retained in the audit report.
            var layer = translucent ? RenderTypes.entityTranslucent(material)
                    : cull ? RenderTypes.entityCutout(material) : RenderTypes.entityCutoutNoCull(material);
            queue.submitCustomGeometry(matrices, layer,
                    (pose, buffer) -> mesh.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1, 0, 0, 0));
        }
        private static void finite(float... values) {
            for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite equipment pose");
        }
    }
}
