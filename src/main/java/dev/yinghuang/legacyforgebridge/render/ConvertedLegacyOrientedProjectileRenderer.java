package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRemoteProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Vanilla arrow presentation for source-proven remote arrow-family carriers.
 *
 * <p>The vanilla renderer owns the arrow mesh, model axes, transforms and render submission.
 * A render-state delegate, rather than a second Arrow entity, lets us reuse that implementation
 * without running modern projectile physics, impact, damage or pickup logic on the client.
 * Source entity textures are used directly; inventory item models/atlases are not arrow meshes.</p>
 *
 * <p>The class name and v1 ORIENTED_ITEM sidecar value are retained for existing candidates.
 * Only their arrow-family presentation changes. Throwable item billboards use their own renderer.</p>
 */
public final class ConvertedLegacyOrientedProjectileRenderer
        extends EntityRenderer<ConvertedLegacyRemoteProjectile, ConvertedLegacyOrientedProjectileRenderer.State> {
    static final Identifier VANILLA_ARROW_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    private final VanillaArrowDelegate vanillaArrow;

    public ConvertedLegacyOrientedProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0F;
        vanillaArrow = new VanillaArrowDelegate(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ConvertedLegacyRemoteProjectile entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ConvertedLegacyNoOpEntityRenderer.suppressVisualEffects(state);
        // These rotations are updated by the server's spawn/move/look packets. The spawn velocity
        // can be stale after the arrow bends down or lands; do not replace packet rotations with it.
        copyPose(state, entity.yRotO, entity.getYRot(), entity.xRotO, entity.getXRot(), partialTick);
        state.texture = textureOrDefault(entity.projectileRule().fixedTexture());
    }

    static void copyPose(State state, float previousYaw, float yaw, float previousPitch, float pitch,
                         float partialTick) {
        // Vanilla's angular interpolation avoids the long 358-degree turn across the +/-180 seam.
        state.yRot = Mth.rotLerp(partialTick, previousYaw, yaw);
        state.xRot = Mth.rotLerp(partialTick, previousPitch, pitch);
        // No impact/shake counter is supplied by the admitted FML rule. Do not invent one.
        state.shake = 0F;
    }

    static Identifier textureOrDefault(Identifier sourceTexture) {
        return sourceTexture == null ? VANILLA_ARROW_TEXTURE : sourceTexture;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector queue, CameraRenderState camera) {
        vanillaArrow.submit(state, pose, queue, camera);
    }

    public static final class State extends ArrowRenderState {
        Identifier texture = VANILLA_ARROW_TEXTURE;
    }

    /** Only submit is called; no AbstractArrow instance is created, ticked or added to the level. */
    static final class VanillaArrowDelegate extends ArrowRenderer<AbstractArrow, State> {
        VanillaArrowDelegate(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        protected Identifier getTextureLocation(State state) {
            return textureOrDefault(state.texture);
        }
    }
}
