package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.LegacyRadialModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRadialModelBlockEntity;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** World BER for a source-proven repeated radial ModelRenderer cuboid base. */
public final class ConvertedLegacyRadialModelRenderer implements BlockEntityRenderer<ConvertedLegacyRadialModelBlockEntity,ConvertedLegacyRadialModelRenderer.State> {
    private record Rendered(ModelPart part,LegacyRadialModelBlockRegistry.Pose pose){}
    private final LegacyRadialModelBlockRegistry.Rule rule;
    private final List<Rendered> parts;
    private final RenderType renderType;

    public ConvertedLegacyRadialModelRenderer(BlockEntityRendererProvider.Context context,LegacyRadialModelBlockRegistry.Rule rule){
        this.rule=rule;this.parts=new ArrayList<>();
        for(var pose:rule.poses())parts.add(new Rendered(part(rule.cuboid(),rule.textureWidth(),rule.textureHeight()),pose));
        this.renderType=RenderTypes.entityCutout(rule.texture());
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ConvertedLegacyRadialModelBlockEntity blockEntity,State state,float tickProgress,Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);
        state.legacyMeta=ConvertedLegacyBlock.legacyMeta(blockEntity.getBlockState());
    }
    @Override public void submit(State state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState cameraState){
        matrices.pushPose();matrices.translate(rule.translateX(),rule.translateY(),rule.translateZ());
        float yaw=(state.legacyMeta&rule.metadataMask())*rule.yawDegreesPerMeta();
        if(yaw!=0F)matrices.mulPose(Axis.YP.rotationDegrees(yaw));
        for(Rendered rendered:parts){
            ModelPart part=rendered.part();var pose=rendered.pose();
            var c=rule.cuboid();part.x=c.pivotX();part.y=c.pivotY();part.z=c.pivotZ();
            part.xRot=pose.xRot();part.yRot=pose.yRot();part.zRot=pose.zRot();
            queue.submitModelPart(part,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
        }
        matrices.popPose();
    }
    private static ModelPart part(LegacyRadialModelBlockRegistry.Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,false,textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }
    public static final class State extends BlockEntityRenderState{int legacyMeta;}
}
