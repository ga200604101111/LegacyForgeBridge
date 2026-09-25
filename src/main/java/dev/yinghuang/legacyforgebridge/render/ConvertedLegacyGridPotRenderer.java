package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyGridPotBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Adapted 1.21.11 renderer for source-proven legacy GridPot stored contents.
 *
 * <p>The 3x3 cell positions and vertical anchor are source-proven. The legacy renderer used a
 * vanilla flower-pot block icon for each enabled cell, while the BlockItem itself was explicitly
 * flat in inventory. World cell carriers therefore use a real modern block model and stored
 * contents use the modern item-model pipeline; inventory presentation remains independent.</p>
 */
public final class ConvertedLegacyGridPotRenderer
        implements BlockEntityRenderer<ConvertedLegacyGridPotBlockEntity, ConvertedLegacyGridPotRenderer.State> {
    private final ItemModelResolver itemModelResolver;
    private final ConvertedGridPotPresentationRuntime.Presentation presentation;
    private final BlockState cellCarrierState;

    public ConvertedLegacyGridPotRenderer(BlockEntityRendererProvider.Context context,
                                          ConvertedGridPotPresentationRuntime.Presentation presentation) {
        this.itemModelResolver = context.itemModelResolver();
        this.presentation = presentation;
        var carrier=BuiltInRegistries.BLOCK.getValue(presentation.cellCarrierBlockId());
        if(carrier==null)throw new IllegalStateException("Missing GridPot cell carrier block "+presentation.cellCarrierBlockId());
        this.cellCarrierState=carrier.defaultBlockState();
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(ConvertedLegacyGridPotBlockEntity blockEntity, State state, float tickProgress,
                                   Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
        int seed = blockEntity.getBlockPos().hashCode();
        for (int slot = 0; slot < state.items.length; slot++) {
            boolean enabled=slot < blockEntity.cells() && blockEntity.isEnabled(slot);
            state.enabled[slot]=enabled;
            ItemStack stack = enabled ? blockEntity.item(slot) : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                state.items[slot] = null;
                continue;
            }
            ItemStackRenderState itemState = state.items[slot];
            if (itemState == null) itemState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.NONE,
                    blockEntity.getLevel(), null, seed * 31 + slot);
            state.items[slot] = itemState.isEmpty() ? null : itemState;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        List<Float> offsets = presentation.gridOffsets();
        for (int slot = 0; slot < state.items.length; slot++) {
            int gridX=slot%3,gridZ=slot/3;
            float x=0.5F+offsets.get(gridX),z=0.5F+offsets.get(gridZ);
            if(state.enabled[slot])submitCarrier(x,z,state,poseStack,submitNodeCollector);
            ItemStackRenderState itemState = state.items[slot];
            if (itemState == null || itemState.isEmpty()) continue;
            poseStack.pushPose();
            poseStack.translate(x, presentation.contentTranslateY(), z);
            float scale = presentation.sourceContentScale();
            poseStack.scale(scale, scale, scale);
            AABB bounds = itemState.getModelBoundingBox();
            double centerX = (bounds.minX + bounds.maxX) * 0.5D;
            double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
            poseStack.translate(-centerX, -bounds.minY, -centerZ);
            itemState.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private void submitCarrier(float x,float z,State state,PoseStack pose,SubmitNodeCollector queue){
        pose.pushPose();
        pose.translate(x-.5F,0D,z-.5F);
        queue.submitBlock(pose,cellCarrierState,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState[] items = new ItemStackRenderState[9];
        final boolean[] enabled = new boolean[9];
    }
}
