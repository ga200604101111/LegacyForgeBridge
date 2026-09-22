package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.LegacyRotatingAssemblyEntityRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRotatingAssemblyEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

import java.util.*;

/** Renderer for source-proven large rotating legacy Entity assemblies. */
public final class ConvertedLegacyRotatingAssemblyEntityRenderer extends EntityRenderer<ConvertedLegacyRotatingAssemblyEntity,ConvertedLegacyRotatingAssemblyEntityRenderer.State> {
    private record Rendered(LegacyRotatingAssemblyEntityRegistry.Cuboid source,ModelPart part){}
    private final LegacyRotatingAssemblyEntityRegistry.Rule rule;
    private final List<Rendered> statics;
    private final List<List<Rendered>> primaryByRepeat;
    private final List<List<Rendered>> secondaryByRepeat;
    private final List<RenderType> renderTypes;

    public ConvertedLegacyRotatingAssemblyEntityRenderer(EntityRendererProvider.Context context,LegacyRotatingAssemblyEntityRegistry.Rule rule){
        super(context);this.rule=rule;this.shadowRadius=0F;
        this.statics=build(rule.staticParts(),rule.modelTextureWidth(),rule.modelTextureHeight());
        int capacity=rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL?rule.countMax():rule.fixedRepeatCount();
        this.primaryByRepeat=buildRepeats(rule.repeatedPrimary(),capacity,rule.modelTextureWidth(),rule.modelTextureHeight());
        this.secondaryByRepeat=buildRepeats(rule.repeatedSecondary(),capacity,rule.modelTextureWidth(),rule.modelTextureHeight());
        this.renderTypes=rule.textures().stream().map(RenderTypes::entityCutout).toList();
    }

    @Override public State createRenderState(){return new State();}

    @Override public void extractRenderState(ConvertedLegacyRotatingAssemblyEntity entity,State state,float partialTick){
        super.extractRenderState(entity,state,partialTick);
        ConvertedLegacyNoOpEntityRenderer.suppressVisualEffects(state);
        state.direction=entity.direction();state.size=entity.size();state.repeatCount=entity.repeatCount();
        state.textureIndex=entity.textureIndex();state.roll=entity.visualRoll();
    }

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector queue,CameraRenderState camera){
        pose.pushPose();
        float shift=(state.size-1F)/2F;
        switch(state.direction&3){
            case 0 -> pose.translate(0D,0D,-shift);
            case 2 -> pose.translate(0D,0D,shift);
            case 3 -> pose.translate(-shift,0D,0D);
            default -> pose.translate(shift,0D,0D);
        }

        float scale=rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL
                ?state.size:1+(state.size/2);
        pose.scale(scale,scale,scale);

        float yaw=rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL
                ?windYaw(state.direction):waterYaw(state.direction);
        if(yaw!=0F)pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if(rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL)
            pose.mulPose(Axis.ZP.rotationDegrees(state.roll));
        else pose.mulPose(Axis.XP.rotationDegrees(state.roll));

        RenderType type=renderTypes.get(Math.max(0,Math.min(state.textureIndex,renderTypes.size()-1)));
        submitStatic(statics,type,state,pose,queue);

        int repeats=Math.max(0,Math.min(state.repeatCount,primaryByRepeat.size()));
        for(int i=0;i<repeats;i++){
            float angle=(float)(Math.PI*2D*i/repeats);
            for(Rendered rendered:primaryByRepeat.get(i)){
                applyRepeated(rendered,angle,0F);
                queue.submitModelPart(rendered.part(),pose,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,null);
            }
            if(i<secondaryByRepeat.size())for(Rendered rendered:secondaryByRepeat.get(i)){
                applyRepeated(rendered,angle,(float)Math.toRadians(rule.secondaryPhaseDegrees()));
                queue.submitModelPart(rendered.part(),pose,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,null);
            }
        }
        pose.popPose();
    }

    private void applyRepeated(Rendered rendered,float primaryAngle,float secondaryOffset){
        var source=rendered.source();ModelPart part=rendered.part();
        part.x=source.pivotX();part.y=source.pivotY();part.z=source.pivotZ();
        if(rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL){
            part.xRot=source.xRot();part.yRot=source.yRot();part.zRot=primaryAngle;
        }else{
            part.xRot=primaryAngle+secondaryOffset;part.yRot=source.yRot();part.zRot=source.zRot();
        }
    }

    private static void submitStatic(List<Rendered> parts,RenderType type,State state,PoseStack pose,SubmitNodeCollector queue){
        for(Rendered rendered:parts){
            var source=rendered.source();ModelPart part=rendered.part();
            part.x=source.pivotX();part.y=source.pivotY();part.z=source.pivotZ();
            part.xRot=source.xRot();part.yRot=source.yRot();part.zRot=source.zRot();
            queue.submitModelPart(part,pose,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,null);
        }
    }

    private static float windYaw(int direction){
        int dir=direction&3;if(dir==1||dir==3)dir+=2;return dir*90F;
    }
    private static float waterYaw(int direction){
        int dir=direction&3;dir+=(dir==1||dir==3)?-1:1;return dir*90F;
    }

    private static List<Rendered> build(List<LegacyRotatingAssemblyEntityRegistry.Cuboid> source,int textureWidth,int textureHeight){
        List<Rendered> out=new ArrayList<>();for(var cuboid:source)out.add(new Rendered(cuboid,part(cuboid,textureWidth,textureHeight)));return List.copyOf(out);
    }
    private static List<List<Rendered>> buildRepeats(List<LegacyRotatingAssemblyEntityRegistry.Cuboid> source,int count,int textureWidth,int textureHeight){
        if(source.isEmpty()||count<=0)return List.of();List<List<Rendered>> out=new ArrayList<>();
        for(int i=0;i<count;i++)out.add(build(source,textureWidth,textureHeight));return List.copyOf(out);
    }
    private static ModelPart part(LegacyRotatingAssemblyEntityRegistry.Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,source.mirror(),textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }

    public static final class State extends EntityRenderState{
        int direction,size,repeatCount,textureIndex;float roll;
    }
}
