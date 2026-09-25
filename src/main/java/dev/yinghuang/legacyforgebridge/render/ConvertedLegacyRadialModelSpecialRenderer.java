package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Inventory/held renderer for a source-proven repeated radial legacy ModelRenderer base. */
public final class ConvertedLegacyRadialModelSpecialRenderer implements NoDataSpecialModelRenderer {
    private record Rendered(ModelPart part,Pose pose) { }

    private final Unbaked definition;
    private final List<Rendered> parts;
    private final RenderType renderType;

    private ConvertedLegacyRadialModelSpecialRenderer(Unbaked definition){
        this.definition=definition;
        List<Rendered> built=new ArrayList<>();
        for(Pose pose:definition.poses())
            built.add(new Rendered(part(definition.cuboid(),definition.textureWidth(),definition.textureHeight()),pose));
        this.parts=List.copyOf(built);
        this.renderType=RenderTypes.entityCutout(definition.texture());
    }

    @Override
    public void submit(ItemDisplayContext type,PoseStack matrices,SubmitNodeCollector queue,
                       int light,int overlay,boolean hasFoil,int outlineColor){
        matrices.pushPose();
        LegacyRenderMath.restoreLegacyModelRendererItemOrigin(matrices);
        matrices.translate(0D,definition.translateY(),0D);
        matrices.scale(definition.scale(),definition.scale(),definition.scale());
        for(Rendered rendered:parts){
            ModelPart part=rendered.part();Pose pose=rendered.pose();Cuboid c=definition.cuboid();
            part.x=c.pivotX();part.y=c.pivotY();part.z=c.pivotZ();
            part.xRot=pose.xRot();part.yRot=pose.yRot();part.zRot=pose.zRot();
            queue.submitModelPart(part,matrices,renderType,light,OverlayTexture.NO_OVERLAY,null,0xFFFFFFFF,null);
        }
        matrices.popPose();
    }

    @Override
    @SuppressWarnings({"rawtypes","unchecked"})
    public void getExtents(Consumer output){
        PoseStack root=new PoseStack();
        LegacyRenderMath.restoreLegacyModelRendererItemOrigin(root);
        root.translate(0D,definition.translateY(),0D);
        root.scale(definition.scale(),definition.scale(),definition.scale());
        Cuboid c=definition.cuboid();
        for(Pose pose:definition.poses()){
            root.pushPose();
            root.translate(c.pivotX()/16F,c.pivotY()/16F,c.pivotZ()/16F);
            if(pose.zRot()!=0F)root.mulPose(Axis.ZP.rotation(pose.zRot()));
            if(pose.yRot()!=0F)root.mulPose(Axis.YP.rotation(pose.yRot()));
            if(pose.xRot()!=0F)root.mulPose(Axis.XP.rotation(pose.xRot()));
            float minX=c.x()/16F,minY=c.y()/16F,minZ=c.z()/16F;
            float maxX=(c.x()+c.width())/16F,maxY=(c.y()+c.height())/16F,maxZ=(c.z()+c.depth())/16F;
            for(float x:new float[]{minX,maxX})for(float y:new float[]{minY,maxY})for(float z:new float[]{minZ,maxZ})
                output.accept(root.last().pose().transformPosition(new Vector3f(x,y,z)));
            root.popPose();
        }
    }

    private static ModelPart part(Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),
                source.width(),source.height(),source.depth(),0F,0F,0F,false,
                textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ){
        public static final Codec<Cuboid> CODEC=RecordCodecBuilder.create(instance->instance.group(
                Codec.INT.fieldOf("u").forGetter(Cuboid::u),Codec.INT.fieldOf("v").forGetter(Cuboid::v),
                Codec.FLOAT.fieldOf("x").forGetter(Cuboid::x),Codec.FLOAT.fieldOf("y").forGetter(Cuboid::y),Codec.FLOAT.fieldOf("z").forGetter(Cuboid::z),
                Codec.INT.fieldOf("width").forGetter(Cuboid::width),Codec.INT.fieldOf("height").forGetter(Cuboid::height),Codec.INT.fieldOf("depth").forGetter(Cuboid::depth),
                Codec.FLOAT.fieldOf("pivot_x").forGetter(Cuboid::pivotX),Codec.FLOAT.fieldOf("pivot_y").forGetter(Cuboid::pivotY),Codec.FLOAT.fieldOf("pivot_z").forGetter(Cuboid::pivotZ)
        ).apply(instance,Cuboid::new));
        public Cuboid{
            if(u<0||v<0||width<=0||height<=0||depth<=0
                    ||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)
                    ||!Float.isFinite(pivotX)||!Float.isFinite(pivotY)||!Float.isFinite(pivotZ))
                throw new IllegalArgumentException("Invalid radial special cuboid");
        }
    }

    public record Pose(float xRot,float yRot,float zRot){
        public static final Codec<Pose> CODEC=RecordCodecBuilder.create(instance->instance.group(
                Codec.FLOAT.fieldOf("x_rot").forGetter(Pose::xRot),
                Codec.FLOAT.fieldOf("y_rot").forGetter(Pose::yRot),
                Codec.FLOAT.fieldOf("z_rot").forGetter(Pose::zRot)
        ).apply(instance,Pose::new));
        public Pose{
            if(!Float.isFinite(xRot)||!Float.isFinite(yRot)||!Float.isFinite(zRot))
                throw new IllegalArgumentException("Invalid radial special pose");
        }
    }

    public record Unbaked(Identifier texture,int textureWidth,int textureHeight,Cuboid cuboid,List<Pose> poses,
                          float translateY,float scale) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.INT.fieldOf("texture_width").forGetter(Unbaked::textureWidth),
                Codec.INT.fieldOf("texture_height").forGetter(Unbaked::textureHeight),
                Cuboid.CODEC.fieldOf("cuboid").forGetter(Unbaked::cuboid),
                Pose.CODEC.listOf().fieldOf("poses").forGetter(Unbaked::poses),
                Codec.FLOAT.fieldOf("translate_y").forGetter(Unbaked::translateY),
                Codec.FLOAT.fieldOf("scale").forGetter(Unbaked::scale)
        ).apply(instance,Unbaked::new));

        public Unbaked{
            poses=List.copyOf(poses);
            if(texture==null||textureWidth<=0||textureHeight<=0||cuboid==null||poses.size()<2||poses.size()>16
                    ||!Float.isFinite(translateY)||!Float.isFinite(scale)||scale<=0F)
                throw new IllegalArgumentException("Invalid radial special renderer");
        }

        @Override public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context){
            return new ConvertedLegacyRadialModelSpecialRenderer(this);
        }
        @Override public MapCodec<Unbaked> type(){return MAP_CODEC;}
    }
}
