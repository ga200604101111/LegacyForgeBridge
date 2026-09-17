package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Modern client adapter for legacy renderers whose effective source doRender callback was proven
 * to be an exact no-op. It deliberately suppresses every EntityRenderer-level presentation path.
 */
public final class ConvertedLegacyNoOpEntityRenderer extends EntityRenderer<Entity, EntityRenderState> {
    public ConvertedLegacyNoOpEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void extractRenderState(Entity entity, EntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        suppressVisualEffects(state);
    }

    @Override
    protected void finalizeRenderState(Entity entity, EntityRenderState state) {
        suppressVisualEffects(state);
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitter,
                       CameraRenderState cameraState) {
        // Source doRender was proven to contain no executable behavior beyond RETURN.
    }

    static void suppressVisualEffects(EntityRenderState state) {
        state.displayFireAnimation = false;
        state.nameTag = null;
        state.nameTagAttachment = null;
        state.leashStates = null;
        state.shadowRadius = 0F;
        state.shadowPieces.clear();
    }
}
