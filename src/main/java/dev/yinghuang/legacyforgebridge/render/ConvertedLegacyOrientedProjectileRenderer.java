package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRemoteProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Directional modern-item carrier for source-proven arrow-family custom projectile renderers.
 *
 * <p>The legacy renderer proves that the projectile presentation follows interpolated yaw/pitch.
 * Its old immediate-mode quad mesh is not executed. The source-bound converted item remains the
 * visible primitive, but it is aligned to projectile flight rather than camera-billboarded.</p>
 */
public final class ConvertedLegacyOrientedProjectileRenderer
        extends EntityRenderer<ConvertedLegacyRemoteProjectile,ConvertedLegacyOrientedProjectileRenderer.State> {
    private static final double MIN_MOTION_SQUARED=1.0E-7D;
    private final ItemModelResolver itemModelResolver;

    public ConvertedLegacyOrientedProjectileRenderer(EntityRendererProvider.Context context){
        super(context);this.shadowRadius=0F;this.itemModelResolver=context.getItemModelResolver();
    }

    @Override public State createRenderState(){return new State();}

    @Override
    public void extractRenderState(ConvertedLegacyRemoteProjectile entity,State state,float partialTick){
        super.extractRenderState(entity,state,partialTick);
        ConvertedLegacyNoOpEntityRenderer.suppressVisualEffects(state);
        state.yaw=entity.getYRot();state.pitch=entity.getXRot();
        Vec3 motion=entity.getDeltaMovement();
        if(motion.lengthSqr()>MIN_MOTION_SQUARED){
            double horizontal=Math.sqrt(motion.x*motion.x+motion.z*motion.z);
            state.yaw=(float)Math.toDegrees(Math.atan2(motion.x,motion.z));
            state.pitch=(float)Math.toDegrees(Math.atan2(motion.y,horizontal));
        }
        itemModelResolver.updateForNonLiving(state.item,entity.getItem(),ItemDisplayContext.NONE,entity);
    }

    @Override
    public void submit(State state,PoseStack pose,SubmitNodeCollector queue,CameraRenderState camera){
        if(state.item.isEmpty())return;
        pose.pushPose();
        // 1.7 arrow-like renderer contract: world Y yaw minus 90, then local Z pitch.
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw-90F));
        pose.mulPose(Axis.ZP.rotationDegrees(state.pitch));
        state.item.submit(pose,queue,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);
        pose.popPose();
    }

    public static final class State extends EntityRenderState {
        float yaw,pitch;
        final ItemStackRenderState item=new ItemStackRenderState();
    }
}
