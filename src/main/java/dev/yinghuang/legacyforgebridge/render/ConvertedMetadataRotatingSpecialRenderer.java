package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;

/** Static inventory renderer for metadata-speed rotating TESRs; source inventory angle is zero. */
public final class ConvertedMetadataRotatingSpecialRenderer implements NoDataSpecialModelRenderer {
    private final Unbaked definition;private final List<Rendered> parts;private final RenderType renderType;
    private record Rendered(ModelPart part,Cuboid source){}
    private ConvertedMetadataRotatingSpecialRenderer(Unbaked definition){
        this.definition=definition;List<Rendered> built=new ArrayList<>();
        for(Cuboid c:definition.cuboids())built.add(new Rendered(part(c,definition.textureWidth(),definition.textureHeight()),c));
        this.parts=List.copyOf(built);this.renderType=RenderTypes.entityCutout(definition.texture());
    }
    @Override public void submit(ItemDisplayContext type,PoseStack matrices,SubmitNodeCollector queue,int light,int overlay,boolean hasFoil,int outlineColor){
        matrices.pushPose();applyTransform(matrices);for(Rendered rendered:parts)queue.submitModelPart(rendered.part(),matrices,renderType,light,overlay,null,0xFFFFFFFF,null);matrices.popPose();
    }
    private void applyTransform(PoseStack matrices){matrices.translate(definition.translateX(),definition.translateY(),definition.translateZ());matrices.scale(definition.scale(),definition.scale(),definition.scale());}
    @Override @SuppressWarnings({"rawtypes","unchecked"}) public void getExtents(Consumer output){
        PoseStack root=new PoseStack();applyTransform(root);
        for(Rendered rendered:parts){Cuboid c=rendered.source();root.pushPose();root.translate(c.pivotX()/16F,c.pivotY()/16F,c.pivotZ()/16F);
            for(float x:new float[]{c.x()/16F,(c.x()+c.width())/16F})for(float y:new float[]{c.y()/16F,(c.y()+c.height())/16F})for(float z:new float[]{c.z()/16F,(c.z()+c.depth())/16F})
                output.accept(root.last().pose().transformPosition(new Vector3f(x,y,z)));root.popPose();}
    }
    private static ModelPart part(Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),
                0F,0F,0F,source.mirror(),textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        ModelPart part=new ModelPart(List.of(cube),Map.of());part.x=source.pivotX();part.y=source.pivotY();part.z=source.pivotZ();return part;
    }

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth,float pivotX,float pivotY,float pivotZ,boolean mirror){
        public static final Codec<Cuboid> CODEC=RecordCodecBuilder.create(instance->instance.group(
                Codec.INT.fieldOf("u").forGetter(Cuboid::u),Codec.INT.fieldOf("v").forGetter(Cuboid::v),
                Codec.FLOAT.fieldOf("x").forGetter(Cuboid::x),Codec.FLOAT.fieldOf("y").forGetter(Cuboid::y),Codec.FLOAT.fieldOf("z").forGetter(Cuboid::z),
                Codec.INT.fieldOf("width").forGetter(Cuboid::width),Codec.INT.fieldOf("height").forGetter(Cuboid::height),Codec.INT.fieldOf("depth").forGetter(Cuboid::depth),
                Codec.FLOAT.optionalFieldOf("pivot_x",0F).forGetter(Cuboid::pivotX),Codec.FLOAT.optionalFieldOf("pivot_y",0F).forGetter(Cuboid::pivotY),
                Codec.FLOAT.optionalFieldOf("pivot_z",0F).forGetter(Cuboid::pivotZ),Codec.BOOL.optionalFieldOf("mirror",false).forGetter(Cuboid::mirror)
        ).apply(instance,Cuboid::new));
        public Cuboid{if(u<0||v<0||width<=0||height<=0||depth<=0||!finite(x,y,z,pivotX,pivotY,pivotZ))throw new IllegalArgumentException("Invalid rotating special cuboid");}
    }
    public record Unbaked(Identifier texture,int textureWidth,int textureHeight,List<Cuboid> cuboids,float translateX,float translateY,float translateZ,float scale) implements SpecialModelRenderer.Unbaked{
        public static final MapCodec<Unbaked> MAP_CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),Codec.INT.fieldOf("texture_width").forGetter(Unbaked::textureWidth),
                Codec.INT.fieldOf("texture_height").forGetter(Unbaked::textureHeight),Cuboid.CODEC.listOf().fieldOf("cuboids").forGetter(Unbaked::cuboids),
                Codec.FLOAT.optionalFieldOf("translate_x",0F).forGetter(Unbaked::translateX),Codec.FLOAT.optionalFieldOf("translate_y",0F).forGetter(Unbaked::translateY),
                Codec.FLOAT.optionalFieldOf("translate_z",0F).forGetter(Unbaked::translateZ),Codec.FLOAT.optionalFieldOf("scale",1F).forGetter(Unbaked::scale)
        ).apply(instance,Unbaked::new));
        public Unbaked{cuboids=List.copyOf(cuboids);if(texture==null||textureWidth<=0||textureHeight<=0||cuboids.isEmpty()||!finite(translateX,translateY,translateZ,scale)||scale<=0F)throw new IllegalArgumentException("Invalid rotating special renderer");}
        @Override public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context){return new ConvertedMetadataRotatingSpecialRenderer(this);}
        @Override public MapCodec<Unbaked> type(){return MAP_CODEC;}
    }
    private static boolean finite(float... values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
}
