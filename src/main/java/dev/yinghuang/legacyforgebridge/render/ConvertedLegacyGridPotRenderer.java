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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Adapted 1.21.11 renderer for source-proven legacy GridPot stored contents.
 *
 * <p>The 3x3 cell positions and vertical anchor are source-proven. The old RenderBlocks crossed-
 * square/cactus geometry is intentionally replaced by the modern item-model pipeline and therefore
 * remains marked non-exact in the runtime sidecar.</p>
 */
public final class ConvertedLegacyGridPotRenderer
        implements BlockEntityRenderer<ConvertedLegacyGridPotBlockEntity, ConvertedLegacyGridPotRenderer.State> {
    private final ItemModelResolver itemModelResolver;
    private final ConvertedGridPotPresentationRuntime.Presentation presentation;

    public ConvertedLegacyGridPotRenderer(BlockEntityRendererProvider.Context context,
                                          ConvertedGridPotPresentationRuntime.Presentation presentation) {
        this.itemModelResolver = context.itemModelResolver();
        this.presentation = presentation;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(ConvertedLegacyGridPotBlockEntity blockEntity, State state, float tickProgress,
                                   Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
        int seed = blockEntity.getBlockPos().hashCode();
        for (int slot = 0; slot < state.items.length; slot++) {
            ItemStack stack = slot < blockEntity.cells() && blockEntity.isEnabled(slot) ? blockEntity.item(slot) : ItemStack.EMPTY;
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
            ItemStackRenderState itemState = state.items[slot];
            if (itemState == null || itemState.isEmpty()) continue;
            int gridX = slot % 3;
            int gridZ = slot / 3;
            poseStack.pushPose();
            poseStack.translate(0.5F + offsets.get(gridX), presentation.contentTranslateY(), 0.5F + offsets.get(gridZ));
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

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState[] items = new ItemStackRenderState[9];
    }
}
