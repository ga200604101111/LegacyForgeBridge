package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyProcessorBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Exact world renderer for the source-proven centered two-layer rotating processor family. */
public final class ConvertedLegacyProcessorRenderer implements BlockEntityRenderer<ConvertedLegacyProcessorBlockEntity,ConvertedLegacyProcessorRenderer.State> {
    private final ConvertedProcessorPresentationRuntime.Presentation presentation;
    private final ModelPart rotatingLower;
    private final ModelPart staticUpper;
    private final RenderType renderType;

    public ConvertedLegacyProcessorRenderer(BlockEntityRendererProvider.Context context,
                                            ConvertedProcessorPresentationRuntime.Presentation presentation) {
        this.presentation=presentation;
        this.rotatingLower=part(presentation.rotatingLower(),presentation.modelTextureWidth(),presentation.modelTextureHeight());
        this.staticUpper=part(presentation.staticUpper(),presentation.modelTextureWidth(),presentation.modelTextureHeight());
        this.renderType=RenderTypes.entityCutout(presentation.entityTexture());
    }

    @Override public State createRenderState(){return new State();}

    @Override
    public void extractRenderState(ConvertedLegacyProcessorBlockEntity blockEntity,State state,float tickProgress,
                                   Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);
        state.rollRadians=(float)Math.toRadians(blockEntity.clientRoll());
    }

    @Override
    public void submit(State state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState cameraState){
        matrices.pushPose();
        matrices.translate(0.5D,0.5D,0.5D);
        queue.submitModelPart(staticUpper,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotation(state.rollRadians));
        queue.submitModelPart(rotatingLower,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
        matrices.popPose();
        matrices.popPose();
    }

    private static ModelPart part(ConvertedProcessorPresentationRuntime.Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(
                source.u(),source.v(),source.x(),source.y(),source.z(),
                source.width(),source.height(),source.depth(),
                0F,0F,0F,true,textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }

    public static final class State extends BlockEntityRenderState {
        float rollRadians;
    }
}
