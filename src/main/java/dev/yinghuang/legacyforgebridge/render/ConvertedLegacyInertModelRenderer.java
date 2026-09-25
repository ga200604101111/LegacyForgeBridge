package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.LegacyInertModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyInertModelBlockEntity;
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

/** World renderer for source-proven inert fixed-cuboid legacy TESR models. */
public final class ConvertedLegacyInertModelRenderer implements BlockEntityRenderer<ConvertedLegacyInertModelBlockEntity,ConvertedLegacyInertModelRenderer.State> {
    private final LegacyInertModelBlockRegistry.Rule rule;
    private final List<ModelPart> parts;
    private final RenderType renderType;

    public ConvertedLegacyInertModelRenderer(BlockEntityRendererProvider.Context context,LegacyInertModelBlockRegistry.Rule rule){
        this.rule=rule;this.parts=new ArrayList<>();var p=rule.presentation();for(var cuboid:p.cuboids())parts.add(part(cuboid,p.modelTextureWidth(),p.modelTextureHeight()));this.renderType=RenderTypes.entityCutout(p.texture());
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ConvertedLegacyInertModelBlockEntity blockEntity,State state,float tickProgress,Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);state.legacyMeta=ConvertedLegacyBlock.legacyMeta(blockEntity.getBlockState());
    }
    @Override public void submit(State state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState cameraState){
        var p=rule.presentation();matrices.pushPose();matrices.translate(p.translateX(),p.translateY(),p.translateZ());float degrees=(state.legacyMeta&p.metadataMask())*p.yawDegreesPerMeta();if(degrees!=0F)matrices.mulPose(Axis.YP.rotationDegrees(degrees));for(ModelPart part:parts)queue.submitModelPart(part,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);matrices.popPose();
    }
    private static ModelPart part(LegacyInertModelBlockRegistry.Cuboid source,int textureWidth,int textureHeight){ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),0F,0F,0F,false,textureWidth,textureHeight,EnumSet.allOf(Direction.class));return new ModelPart(List.of(cube),Map.of());}
    public static final class State extends BlockEntityRenderState { int legacyMeta; }
}
