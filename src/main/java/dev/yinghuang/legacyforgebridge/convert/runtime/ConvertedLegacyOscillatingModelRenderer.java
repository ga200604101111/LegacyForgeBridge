package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.LegacyOscillatingModelBlockRegistry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact BER for source-proven fixed parts plus one degree-driven X-axis joint. */
public final class ConvertedLegacyOscillatingModelRenderer implements BlockEntityRenderer<ConvertedLegacyOscillatingModelBlockEntity,ConvertedLegacyOscillatingModelRenderer.State> {
    private final LegacyOscillatingModelBlockRegistry.Rule rule;
    private final Map<String,ModelPart> parts;
    private final RenderType renderType;

    public ConvertedLegacyOscillatingModelRenderer(BlockEntityRendererProvider.Context context,LegacyOscillatingModelBlockRegistry.Rule rule){
        this.rule=rule;
        this.parts=new LinkedHashMap<>();
        for(var source:rule.presentation().parts())parts.put(source.field(),part(source,rule.presentation().modelTextureWidth(),rule.presentation().modelTextureHeight()));
        this.renderType=RenderTypes.entityCutout(rule.presentation().texture());
    }

    @Override public State createRenderState(){return new State();}

    @Override
    public void extractRenderState(ConvertedLegacyOscillatingModelBlockEntity blockEntity,State state,float tickProgress,
                                   Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);
        state.angleDegrees=blockEntity.angleDegrees();
        state.legacyMeta=ConvertedLegacyBlock.legacyMeta(blockEntity.getBlockState());
    }

    @Override
    public void submit(State state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState cameraState){
        var presentation=rule.presentation();
        matrices.pushPose();
        matrices.translate(presentation.translateX(),presentation.translateY(),presentation.translateZ());
        float yaw=(state.legacyMeta&presentation.metadataMask())*presentation.yawDegreesPerMeta()+presentation.yawOffsetDegrees();
        matrices.mulPose(Axis.YP.rotationDegrees(yaw));
        for(var source:presentation.parts()){
            ModelPart part=parts.get(source.field());
            applyPose(part,source,source.animated()?state.angleDegrees:Float.NaN);
            queue.submitModelPart(part,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
        }
        matrices.popPose();
    }

    /** Shared by the block-entity and inventory special renderers. */
    public static void applyPose(ModelPart part,LegacyOscillatingModelBlockRegistry.Part source,float dynamicDegrees){
        part.x=source.pivotX();part.y=source.pivotY();part.z=source.pivotZ();
        part.xRot=source.animated()&&Float.isFinite(dynamicDegrees)?(float)Math.toRadians(dynamicDegrees):source.baseXRot();
        part.yRot=source.baseYRot();part.zRot=source.baseZRot();
    }

    /** Shared model-part materializer for world and inventory rendering. */
    public static ModelPart part(LegacyOscillatingModelBlockRegistry.Part source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),
                source.width(),source.height(),source.depth(),0F,0F,0F,source.mirror(),textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        ModelPart part=new ModelPart(List.of(cube),Map.of());
        applyPose(part,source,Float.NaN);
        return part;
    }

    public static final class State extends BlockEntityRenderState {
        float angleDegrees;
        int legacyMeta;
    }
}
