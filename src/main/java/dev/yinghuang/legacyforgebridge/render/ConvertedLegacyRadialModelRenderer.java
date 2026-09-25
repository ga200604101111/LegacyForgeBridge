package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.LegacyRadialConditionalModelRegistry;
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

import java.util.*;

/** World BER for source-proven repeated radial bases and metadata-selected legacy sub-model groups. */
public final class ConvertedLegacyRadialModelRenderer implements BlockEntityRenderer<ConvertedLegacyRadialModelBlockEntity,ConvertedLegacyRadialModelRenderer.State> {
    private record Rendered(ModelPart part,LegacyRadialModelBlockRegistry.Pose pose){}
    private record ConditionalRendered(ModelPart part,LegacyRadialConditionalModelRegistry.Cuboid cuboid,
                                       LegacyRadialConditionalModelRegistry.Pose pose,
                                       LegacyRadialConditionalModelRegistry.Animation animation){}

    private final LegacyRadialModelBlockRegistry.Rule rule;
    private final LegacyRadialConditionalModelRegistry.Rule conditionalRule;
    private final List<Rendered> parts;
    private final Map<Integer,List<ConditionalRendered>> conditionalParts;
    private final RenderType renderType;

    public ConvertedLegacyRadialModelRenderer(BlockEntityRendererProvider.Context context,LegacyRadialModelBlockRegistry.Rule rule){
        this.rule=rule;this.parts=new ArrayList<>();
        for(var pose:rule.poses())parts.add(new Rendered(part(rule.cuboid(),rule.textureWidth(),rule.textureHeight()),pose));
        this.conditionalRule=LegacyRadialConditionalModelRegistry.rule(rule.id());
        this.conditionalParts=buildConditionalParts(conditionalRule,rule.textureWidth(),rule.textureHeight());
        this.renderType=RenderTypes.entityCutout(rule.texture());
    }

    @Override public State createRenderState(){return new State();}

    @Override public void extractRenderState(ConvertedLegacyRadialModelBlockEntity blockEntity,State state,float tickProgress,
                                             Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay){
        BlockEntityRenderer.super.extractRenderState(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);
        state.legacyMeta=ConvertedLegacyBlock.legacyMeta(blockEntity.getBlockState());
        state.visualTicks=blockEntity.getLevel()==null?0L:blockEntity.getLevel().getGameTime();
        long position=blockEntity.getBlockPos().asLong();
        state.phaseSeed=position^(position>>>33)^0x9E3779B97F4A7C15L;
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

        if(conditionalRule!=null){
            var group=conditionalRule.groupForMeta(state.legacyMeta);
            if(group!=null){
                List<ConditionalRendered> renderedParts=conditionalParts.get(group.selectorValue());
                if(renderedParts!=null)for(ConditionalRendered rendered:renderedParts)
                    submitConditional(rendered,state,matrices,queue);
            }
        }
        matrices.popPose();
    }

    private void submitConditional(ConditionalRendered rendered,State state,PoseStack matrices,SubmitNodeCollector queue){
        ModelPart part=rendered.part();var c=rendered.cuboid();var pose=rendered.pose();
        part.x=c.pivotX();part.y=c.pivotY();part.z=c.pivotZ();
        float x=pose.xRot(),y=pose.yRot(),z=pose.zRot();
        var animation=rendered.animation();
        if(animation!=null){
            long phase=animation.randomizedPhase()?Math.floorMod(state.phaseSeed,(long)animation.periodTicks()):0L;
            long tick=Math.floorMod(state.visualTicks+phase,(long)animation.periodTicks());
            float radians=(float)Math.toRadians(tick*animation.degreesPerTick());
            if(animation.axis()==LegacyRadialConditionalModelRegistry.Axis.X)x+=radians;
            else if(animation.axis()==LegacyRadialConditionalModelRegistry.Axis.Y)y+=radians;
            else z+=radians;
        }
        part.xRot=x;part.yRot=y;part.zRot=z;
        queue.submitModelPart(part,matrices,renderType,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,state.breakProgress);
    }

    private static Map<Integer,List<ConditionalRendered>> buildConditionalParts(LegacyRadialConditionalModelRegistry.Rule rule,
                                                                                 int textureWidth,int textureHeight){
        if(rule==null)return Map.of();
        Map<Integer,List<ConditionalRendered>> out=new LinkedHashMap<>();
        for(var group:rule.groups()){
            List<ConditionalRendered> rendered=new ArrayList<>();
            for(var sourcePart:group.parts())for(var pose:sourcePart.poses())
                rendered.add(new ConditionalRendered(part(sourcePart.cuboid(),textureWidth,textureHeight),
                        sourcePart.cuboid(),pose,sourcePart.animation()));
            out.put(group.selectorValue(),List.copyOf(rendered));
        }
        return Map.copyOf(out);
    }

    private static ModelPart part(LegacyRadialModelBlockRegistry.Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,false,textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }

    private static ModelPart part(LegacyRadialConditionalModelRegistry.Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,false,textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }

    public static final class State extends BlockEntityRenderState{
        int legacyMeta;
        long visualTicks;
        long phaseSeed;
    }
}
