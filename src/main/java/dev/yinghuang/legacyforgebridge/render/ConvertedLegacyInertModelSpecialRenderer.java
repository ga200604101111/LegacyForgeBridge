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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Inventory/item renderer for a source-proven inert fixed-cuboid legacy renderer. */
public final class ConvertedLegacyInertModelSpecialRenderer implements NoDataSpecialModelRenderer {
    private final Unbaked definition;private final List<ModelPart> parts;private final RenderType renderType;
    private ConvertedLegacyInertModelSpecialRenderer(Unbaked definition){this.definition=definition;this.parts=new ArrayList<>();for(Cuboid c:definition.cuboids())parts.add(part(c,definition.textureWidth(),definition.textureHeight()));this.renderType=RenderTypes.entityCutout(definition.texture());}
    @Override public void submit(ItemDisplayContext type,PoseStack matrices,SubmitNodeCollector queue,int light,int overlay,boolean hasFoil,int outlineColor){matrices.pushPose();applyTransform(matrices);for(ModelPart part:parts)queue.submitModelPart(part,matrices,renderType,light,overlay,null,0xFFFFFFFF,null);matrices.popPose();}
    private void applyTransform(PoseStack matrices){LegacyRenderMath.restoreLegacyModelRendererItemOrigin(matrices);matrices.translate(definition.translateX(),definition.translateY(),definition.translateZ());matrices.scale(definition.scale(),definition.scale(),definition.scale());}
    @Override @SuppressWarnings({"rawtypes","unchecked"}) public void getExtents(Consumer output){PoseStack matrices=new PoseStack();applyTransform(matrices);for(Cuboid c:definition.cuboids()){for(float x:new float[]{c.x()/16F,(c.x()+c.width())/16F})for(float y:new float[]{c.y()/16F,(c.y()+c.height())/16F})for(float z:new float[]{c.z()/16F,(c.z()+c.depth())/16F})output.accept(matrices.last().pose().transformPosition(new Vector3f(x,y,z)));}}
    private static ModelPart part(Cuboid source,int textureWidth,int textureHeight){ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),source.width(),source.height(),source.depth(),0F,0F,0F,false,textureWidth,textureHeight,EnumSet.allOf(Direction.class));return new ModelPart(List.of(cube),Map.of());}

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth){
        public static final Codec<Cuboid> CODEC=RecordCodecBuilder.create(instance->instance.group(Codec.INT.fieldOf("u").forGetter(Cuboid::u),Codec.INT.fieldOf("v").forGetter(Cuboid::v),Codec.FLOAT.fieldOf("x").forGetter(Cuboid::x),Codec.FLOAT.fieldOf("y").forGetter(Cuboid::y),Codec.FLOAT.fieldOf("z").forGetter(Cuboid::z),Codec.INT.fieldOf("width").forGetter(Cuboid::width),Codec.INT.fieldOf("height").forGetter(Cuboid::height),Codec.INT.fieldOf("depth").forGetter(Cuboid::depth)).apply(instance,Cuboid::new));
        public Cuboid{if(u<0||v<0||width<=0||height<=0||depth<=0||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid inert special cuboid");}
    }
    public record Unbaked(Identifier texture,int textureWidth,int textureHeight,List<Cuboid> cuboids,float translateX,float translateY,float translateZ,float scale) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),Codec.INT.fieldOf("texture_width").forGetter(Unbaked::textureWidth),Codec.INT.fieldOf("texture_height").forGetter(Unbaked::textureHeight),Cuboid.CODEC.listOf().fieldOf("cuboids").forGetter(Unbaked::cuboids),Codec.FLOAT.optionalFieldOf("translate_x",0F).forGetter(Unbaked::translateX),Codec.FLOAT.optionalFieldOf("translate_y",0F).forGetter(Unbaked::translateY),Codec.FLOAT.optionalFieldOf("translate_z",0F).forGetter(Unbaked::translateZ),Codec.FLOAT.optionalFieldOf("scale",1F).forGetter(Unbaked::scale)).apply(instance,Unbaked::new));
        public Unbaked{cuboids=List.copyOf(cuboids);if(texture==null||textureWidth<=0||textureHeight<=0||cuboids.isEmpty()||!Float.isFinite(translateX)||!Float.isFinite(translateY)||!Float.isFinite(translateZ)||!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid inert special renderer");}
        @Override public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context){return new ConvertedLegacyInertModelSpecialRenderer(this);}
        @Override public MapCodec<Unbaked> type(){return MAP_CODEC;}
    }
}
