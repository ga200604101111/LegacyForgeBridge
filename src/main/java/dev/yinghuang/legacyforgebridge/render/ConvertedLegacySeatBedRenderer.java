package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedPresentationRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatBedBlock;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatBedBlockEntity;
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

/** Exact world renderer for a source-proven two-texture, four-cuboid legacy bed/seat TESR. */
public final class ConvertedLegacySeatBedRenderer implements BlockEntityRenderer<ConvertedLegacySeatBedBlockEntity,ConvertedLegacySeatBedRenderer.State> {
    private final LegacySeatBedPresentationRegistry.Rule rule;
    private final Map<String,ModelPart> parts=new LinkedHashMap<>();
    private final RenderType footRenderType;
    private final RenderType headRenderType;

    public ConvertedLegacySeatBedRenderer(BlockEntityRendererProvider.Context context,LegacySeatBedPresentationRegistry.Rule rule){
        this.rule=rule;
        for(var source:rule.parts())parts.put(source.field(),part(source,rule.modelTextureWidth(),rule.modelTextureHeight()));
        footRenderType=RenderTypes.entityCutout(rule.footTexture());
        headRenderType=RenderTypes.entityCutout(rule.headTexture());
    }

    @Override public State createRenderState(){return new State();}

    @Override
    public void extractRenderState(ConvertedLegacySeatBedBlockEntity blockEntity,State state,float tickProgress,Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);
        state.legacyMeta=ConvertedLegacySeatBedBlock.legacyMeta(blockEntity.getBlockState());
    }

    @Override
    public void submit(State state,PoseStack matrices,SubmitNodeCollector queue,CameraRenderState cameraState){
        int direction=state.legacyMeta&3;
        matrices.pushPose();
        matrices.translate(rule.translateXByDirection().get(direction),0F,rule.translateZByDirection().get(direction));
        float yaw=rule.yawDegreesByDirection().get(direction);
        if(yaw!=0F)matrices.mulPose(Axis.YP.rotationDegrees(yaw));
        renderGroup(rule.footParts(),footRenderType,state,matrices,queue);
        renderGroup(rule.headParts(),headRenderType,state,matrices,queue);
        matrices.popPose();
    }

    private void renderGroup(List<String> group,RenderType type,State state,PoseStack matrices,SubmitNodeCollector queue){
        for(String field:group){
            ModelPart part=parts.get(field);
            if(part==null)throw new IllegalStateException("Missing seat-bed model part "+field);
            queue.submitModelPart(part,matrices,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
        }
    }

    @Override public boolean shouldRenderOffScreen(){return rule.expandedRenderBoundsProven();}

    static ModelPart part(LegacySeatBedPresentationRegistry.Part source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,source.mirror(),textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        ModelPart part=new ModelPart(List.of(cube),Map.of());
        part.setPos(source.pivotX(),source.pivotY(),source.pivotZ());
        part.setRotation(source.xRot(),source.yRot(),source.zRot());
        return part;
    }

    public static final class State extends BlockEntityRenderState { int legacyMeta; }
}
