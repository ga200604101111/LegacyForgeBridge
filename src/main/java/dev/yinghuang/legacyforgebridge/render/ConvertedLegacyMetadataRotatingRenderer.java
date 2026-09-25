package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.compat.LegacyMetadataRotatingTesrRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyMetadataRotatingBlockEntity;
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

import java.util.*;

/** World renderer for source-proven metadata-speed rotating legacy TESRs. */
public final class ConvertedLegacyMetadataRotatingRenderer implements BlockEntityRenderer<ConvertedLegacyMetadataRotatingBlockEntity,ConvertedLegacyMetadataRotatingRenderer.State> {
    private record Rendered(String field,ModelPart part){}
    private final LegacyMetadataRotatingTesrRegistry.Rule rule;
    private final List<Rendered> parts;
    private final RenderType renderType;

    public ConvertedLegacyMetadataRotatingRenderer(BlockEntityRendererProvider.Context context,LegacyMetadataRotatingTesrRegistry.Rule rule){
        this.rule=rule;List<Rendered> built=new ArrayList<>();
        for(var c:rule.cuboids())built.add(new Rendered(c.field(),part(c,rule.textureWidth(),rule.textureHeight())));
        this.parts=List.copyOf(built);this.renderType=RenderTypes.entityCutout(rule.texture());
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ConvertedLegacyMetadataRotatingBlockEntity blockEntity,State state,float tickProgress,
                                             Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);
        state.legacyMeta=ConvertedLegacyBlock.legacyMeta(blockEntity.getBlockState());
        long time=blockEntity.getLevel()==null?0L:blockEntity.getLevel().getGameTime();
        state.visualDegrees=blockEntity.visualDegrees(time,state.legacyMeta,rule);
    }
    @Override public void submit(State state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState cameraState){
        matrices.pushPose();matrices.translate(rule.translateX(),rule.translateY(),rule.translateZ());
        float radians=(float)Math.toRadians(state.visualDegrees);
        for(Rendered rendered:parts){
            ModelPart part=rendered.part();part.yRot=rendered.field().equals(rule.animatedPart())?radians:0F;
            queue.submitModelPart(part,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
        }
        matrices.popPose();
    }
    private static ModelPart part(LegacyMetadataRotatingTesrRegistry.Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,source.mirror(),textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        ModelPart part=new ModelPart(List.of(cube),Map.of());part.x=source.pivotX();part.y=source.pivotY();part.z=source.pivotZ();return part;
    }
    public static final class State extends BlockEntityRenderState{int legacyMeta;float visualDegrees;}
}
