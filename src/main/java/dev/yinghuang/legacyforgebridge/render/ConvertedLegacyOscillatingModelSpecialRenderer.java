package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.yinghuang.legacyforgebridge.compat.LegacyOscillatingModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyOscillatingModelRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Inventory special renderer for the source-proven oscillating decorative family. */
public final class ConvertedLegacyOscillatingModelSpecialRenderer implements NoDataSpecialModelRenderer {
    private final Unbaked definition;
    private final List<RenderedPart> parts;
    private final RenderType renderType;

    private ConvertedLegacyOscillatingModelSpecialRenderer(Unbaked definition){
        this.definition=definition;
        List<RenderedPart> built=new ArrayList<>();
        for(Part source:definition.parts()){
            var runtime=source.runtime();
            built.add(new RenderedPart(source,ConvertedLegacyOscillatingModelRenderer.part(runtime,definition.textureWidth(),definition.textureHeight())));
        }
        this.parts=List.copyOf(built);
        this.renderType=RenderTypes.entityCutout(definition.texture());
    }

    @Override
    public void submit(ItemDisplayContext type,PoseStack matrices,SubmitNodeCollector queue,
                       int light,int overlay,boolean hasFoil,int outlineColor){
        matrices.pushPose();
        applyRoot(matrices);
        for(RenderedPart rendered:parts){
            var source=rendered.source();
            var runtime=source.runtime();
            ConvertedLegacyOscillatingModelRenderer.applyPose(rendered.part(),runtime,
                    runtime.animated()?definition.dynamicAngleDegrees():Float.NaN);
            queue.submitModelPart(rendered.part(),matrices,renderType,light,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,null);
        }
        matrices.popPose();
    }

    private void applyRoot(PoseStack matrices){
        matrices.mulPose(Axis.YP.rotationDegrees(definition.yawDegrees()));
        matrices.translate(0D,definition.translateY(),0D);
        matrices.scale(definition.scale(),definition.scale(),definition.scale());
    }

    @Override
    @SuppressWarnings({"rawtypes","unchecked"})
    public void getExtents(Consumer output){
        PoseStack root=new PoseStack();
        applyRoot(root);
        for(Part source:definition.parts()){
            float xRot=source.animated()? (float)Math.toRadians(definition.dynamicAngleDegrees()) : source.baseXRot();
            root.pushPose();
            root.translate(source.pivotX()/16F,source.pivotY()/16F,source.pivotZ()/16F);
            if(source.baseZRot()!=0F)root.mulPose(Axis.ZP.rotation(source.baseZRot()));
            if(source.baseYRot()!=0F)root.mulPose(Axis.YP.rotation(source.baseYRot()));
            if(xRot!=0F)root.mulPose(Axis.XP.rotation(xRot));
            float minX=source.x()/16F,minY=source.y()/16F,minZ=source.z()/16F;
            float maxX=(source.x()+source.width())/16F,maxY=(source.y()+source.height())/16F,maxZ=(source.z()+source.depth())/16F;
            for(float x:new float[]{minX,maxX})for(float y:new float[]{minY,maxY})for(float z:new float[]{minZ,maxZ})
                output.accept(root.last().pose().transformPosition(new Vector3f(x,y,z)));
            root.popPose();
        }
    }

    private record RenderedPart(Part source,ModelPart part) { }

    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,boolean mirror,
                       float baseXRot,float baseYRot,float baseZRot,boolean animated) {
        public static final Codec<Part> CODEC=RecordCodecBuilder.create(instance->instance.group(
                Codec.STRING.fieldOf("field").forGetter(Part::field),
                Codec.INT.fieldOf("u").forGetter(Part::u),Codec.INT.fieldOf("v").forGetter(Part::v),
                Codec.FLOAT.fieldOf("x").forGetter(Part::x),Codec.FLOAT.fieldOf("y").forGetter(Part::y),Codec.FLOAT.fieldOf("z").forGetter(Part::z),
                Codec.INT.fieldOf("width").forGetter(Part::width),Codec.INT.fieldOf("height").forGetter(Part::height),Codec.INT.fieldOf("depth").forGetter(Part::depth),
                Codec.FLOAT.fieldOf("pivot_x").forGetter(Part::pivotX),Codec.FLOAT.fieldOf("pivot_y").forGetter(Part::pivotY),Codec.FLOAT.fieldOf("pivot_z").forGetter(Part::pivotZ),
                Codec.BOOL.optionalFieldOf("mirror",false).forGetter(Part::mirror),
                Codec.FLOAT.optionalFieldOf("base_x_rot",0F).forGetter(Part::baseXRot),Codec.FLOAT.optionalFieldOf("base_y_rot",0F).forGetter(Part::baseYRot),Codec.FLOAT.optionalFieldOf("base_z_rot",0F).forGetter(Part::baseZRot),
                Codec.BOOL.optionalFieldOf("animated",false).forGetter(Part::animated)
        ).apply(instance,Part::new));
        LegacyOscillatingModelBlockRegistry.Part runtime(){return new LegacyOscillatingModelBlockRegistry.Part(field,u,v,x,y,z,width,height,depth,pivotX,pivotY,pivotZ,mirror,baseXRot,baseYRot,baseZRot,animated);}
    }

    public record Unbaked(Identifier texture,int textureWidth,int textureHeight,List<Part> parts,
                          float yawDegrees,float translateY,float scale,float dynamicAngleDegrees)
            implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.INT.fieldOf("texture_width").forGetter(Unbaked::textureWidth),
                Codec.INT.fieldOf("texture_height").forGetter(Unbaked::textureHeight),
                Part.CODEC.listOf().fieldOf("parts").forGetter(Unbaked::parts),
                Codec.FLOAT.fieldOf("yaw_degrees").forGetter(Unbaked::yawDegrees),
                Codec.FLOAT.fieldOf("translate_y").forGetter(Unbaked::translateY),
                Codec.FLOAT.fieldOf("scale").forGetter(Unbaked::scale),
                Codec.FLOAT.fieldOf("dynamic_angle_degrees").forGetter(Unbaked::dynamicAngleDegrees)
        ).apply(instance,Unbaked::new));
        public Unbaked {
            parts=List.copyOf(parts);
            if(texture==null||textureWidth<=0||textureHeight<=0||parts.isEmpty()||parts.stream().filter(Part::animated).count()!=1
                    ||!Float.isFinite(yawDegrees)||!Float.isFinite(translateY)||!Float.isFinite(scale)||scale<=0F||!Float.isFinite(dynamicAngleDegrees))
                throw new IllegalArgumentException("Invalid oscillating special model");
        }
        @Override public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context){return new ConvertedLegacyOscillatingModelSpecialRenderer(this);}
        @Override public MapCodec<Unbaked> type(){return MAP_CODEC;}
    }
}
