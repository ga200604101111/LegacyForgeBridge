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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generic inventory/item renderer for a source-proven two-layer processor model.
 * The rotating lower layer is intentionally fixed at zero radians because that is the proven
 * legacy inventory renderer behavior; world rotation stays in ConvertedLegacyProcessorRenderer.
 */
public final class ConvertedLegacyProcessorSpecialRenderer implements NoDataSpecialModelRenderer {
    private final Unbaked definition;
    private final ModelPart rotatingLower;
    private final ModelPart staticUpper;
    private final RenderType renderType;

    private ConvertedLegacyProcessorSpecialRenderer(Unbaked definition) {
        this.definition=definition;
        this.rotatingLower=part(definition.rotatingLower(),definition.textureWidth(),definition.textureHeight());
        this.staticUpper=part(definition.staticUpper(),definition.textureWidth(),definition.textureHeight());
        this.renderType=RenderTypes.entityCutout(definition.texture());
    }

    @Override
    public void submit(ItemDisplayContext type,PoseStack matrices,SubmitNodeCollector queue,
                       int light,int overlay,boolean hasFoil,int outlineColor) {
        matrices.pushPose();
        if(definition.centered())LegacyRenderMath.restoreLegacyModelRendererItemOrigin(matrices);
        queue.submitModelPart(staticUpper,matrices,renderType,light,overlay,null,0xFFFFFFFF,null);
        // Source ModelMillStone.renderInv() explicitly writes lower.rotateAngleY = 0 before render.
        queue.submitModelPart(rotatingLower,matrices,renderType,light,overlay,null,0xFFFFFFFF,null);
        matrices.popPose();
    }

    @Override
    @SuppressWarnings({"rawtypes","unchecked"})
    public void getExtents(Consumer output) {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(Cuboid cuboid:List.of(definition.rotatingLower(),definition.staticUpper())){
            float x0=cuboid.x()/16F,x1=(cuboid.x()+cuboid.width())/16F;
            float y0=cuboid.y()/16F,y1=(cuboid.y()+cuboid.height())/16F;
            float z0=cuboid.z()/16F,z1=(cuboid.z()+cuboid.depth())/16F;
            minX=Math.min(minX,Math.min(x0,x1));maxX=Math.max(maxX,Math.max(x0,x1));
            minY=Math.min(minY,Math.min(y0,y1));maxY=Math.max(maxY,Math.max(y0,y1));
            minZ=Math.min(minZ,Math.min(z0,z1));maxZ=Math.max(maxZ,Math.max(z0,z1));
        }
        if(definition.centered()){
            minX+=0.5F;maxX+=0.5F;minY+=0.5F;maxY+=0.5F;minZ+=0.5F;maxZ+=0.5F;
        }
        for(float x:new float[]{minX,maxX})for(float y:new float[]{minY,maxY})for(float z:new float[]{minZ,maxZ})
            output.accept(new Vector3f(x,y,z));
    }

    private static ModelPart part(Cuboid source,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(
                source.u(),source.v(),source.x(),source.y(),source.z(),
                source.width(),source.height(),source.depth(),
                0F,0F,0F,true,textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube),Map.of());
    }

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth) {
        public static final Codec<Cuboid> CODEC=RecordCodecBuilder.create(instance->instance.group(
                Codec.INT.fieldOf("u").forGetter(Cuboid::u),
                Codec.INT.fieldOf("v").forGetter(Cuboid::v),
                Codec.FLOAT.fieldOf("x").forGetter(Cuboid::x),
                Codec.FLOAT.fieldOf("y").forGetter(Cuboid::y),
                Codec.FLOAT.fieldOf("z").forGetter(Cuboid::z),
                Codec.INT.fieldOf("width").forGetter(Cuboid::width),
                Codec.INT.fieldOf("height").forGetter(Cuboid::height),
                Codec.INT.fieldOf("depth").forGetter(Cuboid::depth)
        ).apply(instance,Cuboid::new));
        public Cuboid {
            if(u<0||v<0||width<=0||height<=0||depth<=0||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                throw new IllegalArgumentException("Invalid converted processor cuboid");
        }
    }

    public record Unbaked(Identifier texture,int textureWidth,int textureHeight,Cuboid rotatingLower,Cuboid staticUpper,
                          boolean centered) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.INT.fieldOf("texture_width").forGetter(Unbaked::textureWidth),
                Codec.INT.fieldOf("texture_height").forGetter(Unbaked::textureHeight),
                Cuboid.CODEC.fieldOf("rotating_lower").forGetter(Unbaked::rotatingLower),
                Cuboid.CODEC.fieldOf("static_upper").forGetter(Unbaked::staticUpper),
                Codec.BOOL.optionalFieldOf("centered",true).forGetter(Unbaked::centered)
        ).apply(instance,Unbaked::new));
        public Unbaked {
            if(texture==null||textureWidth<=0||textureHeight<=0||rotatingLower==null||staticUpper==null)
                throw new IllegalArgumentException("Invalid converted processor inventory renderer definition");
        }
        @Override public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context){
            return new ConvertedLegacyProcessorSpecialRenderer(this);
        }
        @Override public MapCodec<Unbaked> type(){return MAP_CODEC;}
    }
}
