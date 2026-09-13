package dev.longyu.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.function.Consumer;

/**
 * Generic special-model bridge for converted Forge 1.7 Wavefront item models.
 *
 * <p>Hand contexts retain the profile/extracted legacy scale. Contexts whose primary job is to
 * present an isolated item (GUI, ground and fixed displays) additionally use geometry bounds to
 * center and fit the mesh. This avoids requiring per-mod hardcoded inventory transforms while a
 * future bytecode pass can still emit exact IItemRenderer transforms when they are recoverable.</p>
 */
public final class ObjSpecialRenderer implements NoDataSpecialModelRenderer {
    private final LegacyObjModel mesh;
    private final Identifier texture;
    private final float scale;

    private ObjSpecialRenderer(LegacyObjModel mesh, Identifier texture, float scale) {
        this.mesh = mesh;
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public void submit(
            ItemDisplayContext type,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
    ) {
        LegacyObjModel.Bounds bounds = mesh.bounds();
        boolean autoFit = type == ItemDisplayContext.GUI
                || type == ItemDisplayContext.GROUND
                || type == ItemDisplayContext.FIXED;
        float originX = autoFit ? bounds.centerX() : 0.0F;
        float originY = autoFit ? bounds.centerY() : 0.0F;
        float originZ = autoFit ? bounds.centerZ() : 0.0F;
        float renderScale = scale;

        if (autoFit && bounds.maxExtent() > 1.0E-6F) {
            float targetExtent = switch (type) {
                case GUI -> 0.95F;
                case GROUND -> 0.55F;
                case FIXED -> 0.85F;
                default -> bounds.maxExtent() * scale;
            };
            renderScale = targetExtent / bounds.maxExtent();
        }

        float finalScale = renderScale;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutoutNoCull(texture),
                (pose, buffer) -> mesh.render(
                        pose,
                        buffer,
                        lightCoords,
                        overlayCoords,
                        finalScale,
                        originX,
                        originY,
                        originZ
                )
        );
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getExtents(Consumer output) {
        mesh.emitExtents(output, scale);
    }

    public record Unbaked(Identifier model, Identifier texture, float scale) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Unbaked::scale)
        ).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context) {
            try {
                return new ObjSpecialRenderer(LegacyObjModel.load(model), texture, scale);
            } catch (Exception exception) {
                LegacyForgeBridge.LOGGER.error("Failed to bake converted OBJ model {}", model, exception);
                return null;
            }
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
