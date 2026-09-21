package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yinghuang.legacyforgebridge.compat.*;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVisualEntity;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedStackPresentation;
import dev.yinghuang.legacyforgebridge.network.FmlRuntimeCodec;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Modern renderer for source-proven slide-panel, cushion and tray legacy Entity families. */
public final class ConvertedLegacyVisualEntityRenderer extends EntityRenderer<ConvertedLegacyVisualEntity,ConvertedLegacyVisualEntityRenderer.State> {
    private final LegacyVisibleEntityRegistry.Rule rule;
    private final List<ModelPart> parts;
    private final List<ModelPart> mirroredParts;
    private final ItemModelResolver itemModelResolver;

    public ConvertedLegacyVisualEntityRenderer(EntityRendererProvider.Context context,LegacyVisibleEntityRegistry.Rule rule){
        super(context);this.rule=rule;this.shadowRadius=0F;this.itemModelResolver=context.getItemModelResolver();
        this.parts=rule.parts().stream().map(p->part(p,p.mirror(),rule.modelTextureWidth(),rule.modelTextureHeight())).toList();
        this.mirroredParts=rule.adapter()==LegacyVisibleEntityRegistry.Adapter.SLIDE_PANEL
                ?rule.parts().stream().map(p->part(p,true,rule.modelTextureWidth(),rule.modelTextureHeight())).toList():parts;
    }

    @Override public State createRenderState(){return new State();}

    @Override
    public void extractRenderState(ConvertedLegacyVisualEntity entity,State state,float partialTick){
        super.extractRenderState(entity,state,partialTick);ConvertedLegacyNoOpEntityRenderer.suppressVisualEffects(state);
        state.yaw=entity.getYRot();state.pitch=entity.getXRot();
        switch(rule.adapter()){
            case SLIDE_PANEL -> {
                state.direction=entity.watcherInt("direction",0);
                state.mirror=entity.watcherInt("mirror",0)!=0;
                state.textureValue=entity.watcherInt("texture",0);
            }
            case TINTED_CUSHION -> state.color=entity.watcherInt("color",15);
            case TRAY_ITEMS -> {
                state.itemCount=0;
                for(int slot=0;slot<rule.itemWatcherCount();slot++){
                    ItemStack stack=modernStack(entity.watcherStack(slot));
                    if(stack.isEmpty())continue;
                    ItemStackRenderState itemState=state.items[state.itemCount];
                    if(itemState==null)itemState=new ItemStackRenderState();
                    itemModelResolver.updateForNonLiving(itemState,stack,ItemDisplayContext.NONE,entity);
                    if(!itemState.isEmpty())state.items[state.itemCount++]=itemState;
                }
                for(int i=state.itemCount;i<state.items.length;i++)state.items[i]=null;
            }
        }
    }

    @Override
    public void submit(State state,PoseStack pose,SubmitNodeCollector queue,CameraRenderState camera){
        switch(rule.adapter()){
            case SLIDE_PANEL -> submitDoor(state,pose,queue);
            case TINTED_CUSHION -> submitCushion(state,pose,queue);
            case TRAY_ITEMS -> submitTray(state,pose,queue);
        }
    }

    private void submitDoor(State state,PoseStack pose,SubmitNodeCollector queue){
        var texture=rule.textureVariant(state.textureValue);if(texture==null)texture=rule.textureVariants().getFirst();
        RenderType type=texture.translucent()?RenderTypes.entityTranslucent(texture.texture()):RenderTypes.entityCutout(texture.texture());
        pose.pushPose();pose.translate(0D,1D,0D);pose.scale(.999375F,.999375F,.999375F);
        float yaw=state.mirror?(state.direction-2)*90F:state.direction*90F;
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        submitParts(state.mirror?mirroredParts:parts,type,0xFFFFFFFF,state,pose,queue);
        pose.popPose();
    }

    private void submitCushion(State state,PoseStack pose,SubmitNodeCollector queue){
        int index=state.color>=0&&state.color<rule.palette().size()?state.color:0;
        int color=0xFF000000|rule.palette().get(index);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180F-state.yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(180F-state.pitch));
        submitParts(parts,RenderTypes.entityCutout(rule.fixedTexture()),color,state,pose,queue);
        pose.popPose();
    }

    private void submitTray(State state,PoseStack pose,SubmitNodeCollector queue){
        pose.pushPose();pose.translate(0D,.2D,0D);pose.mulPose(Axis.XP.rotationDegrees(180F));pose.mulPose(Axis.YP.rotationDegrees(state.yaw+180F));
        submitParts(parts,RenderTypes.entityCutout(rule.fixedTexture()),0xFFFFFFFF,state,pose,queue);pose.popPose();
        if(state.itemCount<=0)return;
        pose.pushPose();pose.scale(.7F,.7F,.7F);
        if(state.itemCount==1){
            submitItem(state.items[0],0F,.18F,0F,state,pose,queue);
        }else{
            float step=360F/state.itemCount;float radius=.25F;
            for(int i=0;i<state.itemCount;i++){
                double angle=Math.toRadians(step*(i+1)+state.yaw+180F);
                if(state.itemCount==3)angle+=.5D;
                else if(state.itemCount==4){angle-=.78D;radius=.3F;}
                else if(state.itemCount==5){angle-=.32D;radius=.325F;}
                submitItem(state.items[i],(float)(radius*Math.cos(angle)),.18F,(float)(radius*Math.sin(angle)),state,pose,queue);
            }
        }
        pose.popPose();
    }

    private static void submitItem(ItemStackRenderState item,float x,float y,float z,State state,PoseStack pose,SubmitNodeCollector queue){
        if(item==null||item.isEmpty())return;pose.pushPose();pose.translate(x,y,z);
        item.submit(pose,queue,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();
    }

    private static void submitParts(List<ModelPart> parts,RenderType type,int color,State state,PoseStack pose,SubmitNodeCollector queue){
        for(ModelPart part:parts)queue.submitModelPart(part,pose,type,state.lightCoords,OverlayTexture.NO_OVERLAY,null,color,null);
    }

    private static ModelPart part(LegacyVisibleEntityRegistry.Part source,boolean mirror,int textureWidth,int textureHeight){
        ModelPart.Cube cube=new ModelPart.Cube(source.u(),source.v(),source.x(),source.y(),source.z(),
                source.width(),source.height(),source.depth(),0F,0F,0F,mirror,
                textureWidth,textureHeight,EnumSet.allOf(Direction.class));
        ModelPart part=new ModelPart(List.of(cube),Map.of());part.x=source.pivotX();part.y=source.pivotY();part.z=source.pivotZ();
        part.xRot=source.xRot();part.yRot=source.yRot();part.zRot=source.zRot();return part;
    }

    private static ItemStack modernStack(FmlRuntimeCodec.LegacyItemStack legacy){
        if(legacy==null||legacy.empty())return ItemStack.EMPTY;
        Identifier id=LegacyModItemRegistryMap.legacyIdentity(legacy.legacyItemId());
        if(id==null)id=LegacyModBlockRegistryMap.legacyIdentity(legacy.legacyItemId());
        if(id==null||!BuiltInRegistries.ITEM.containsKey(id))return ItemStack.EMPTY;
        Item item=BuiltInRegistries.ITEM.getValue(id);if(item==null)return ItemStack.EMPTY;
        ItemStack stack=ConvertedStackPresentation.create(item,legacy.damage());
        stack.setCount(Math.max(1,Math.min(legacy.count(),stack.getMaxStackSize())));
        ConvertedStackPresentation.apply(stack,legacy.damage());return stack;
    }

    public static final class State extends EntityRenderState {
        int direction,textureValue,color=15,itemCount;boolean mirror;float yaw,pitch;
        final ItemStackRenderState[] items=new ItemStackRenderState[5];
    }
}
