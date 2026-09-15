package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.render.ConvertedProcessorPresentationRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Generic processor UI with exact source widgets when a presentation proof is available. */
public final class ConvertedLegacyProcessorScreen extends AbstractContainerScreen<ConvertedLegacyProcessorMenu> {
    private static final Identifier FALLBACK_BACKGROUND=Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    public ConvertedLegacyProcessorScreen(ConvertedLegacyProcessorMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
    }

    @Override
    protected void init(){
        super.init();
        titleLabelX=(imageWidth-font.width(title))/2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics,float delta,int mouseX,int mouseY){
        int x=(width-imageWidth)/2;
        int y=(height-imageHeight)/2;
        ConvertedProcessorPresentationRuntime.Presentation presentation=
                ConvertedProcessorPresentationRuntime.presentation(menu.presentationKey());
        if(presentation==null){
            graphics.blit(RenderPipelines.GUI_TEXTURED,FALLBACK_BACKGROUND,x,y,0.0F,0.0F,imageWidth,imageHeight,256,256);
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED,presentation.guiTexture(),x,y,0.0F,0.0F,
                presentation.guiWidth(),presentation.guiHeight(),presentation.guiTextureWidth(),presentation.guiTextureHeight());
        if(!menu.grinding())return;
        int motion=menu.grindMotion();
        ConvertedProcessorPresentationRuntime.Widget motionWidget=presentation.motion();
        if(motion>=0&&motion<motionWidget.frames()){
            graphics.blit(RenderPipelines.GUI_TEXTURED,presentation.guiTexture(),
                    x+motionWidget.x(),y+motionWidget.y(),motionWidget.u(),motionWidget.vStride()*motion,
                    motionWidget.width(),motionWidget.height(),presentation.guiTextureWidth(),presentation.guiTextureHeight());
        }
        int progress=Math.max(0,Math.min(menu.progressStage(),presentation.progress().frames()));
        ConvertedProcessorPresentationRuntime.Widget progressWidget=presentation.progress();
        graphics.blit(RenderPipelines.GUI_TEXTURED,presentation.guiTexture(),
                x+progressWidget.x(),y+progressWidget.y(),progressWidget.u(),progressWidget.vStride()*progress,
                progressWidget.width(),progressWidget.height(),presentation.guiTextureWidth(),presentation.guiTextureHeight());
    }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta){
        super.render(graphics,mouseX,mouseY,delta);
        if(ConvertedProcessorPresentationRuntime.presentation(menu.presentationKey())==null&&menu.grinding()){
            graphics.drawString(font,"Progress "+menu.progressStage()+"/3",leftPos+62,topPos+38,0xFFFFFFFF,false);
        }
        renderTooltip(graphics,mouseX,mouseY);
    }
}
