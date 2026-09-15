package dev.longyu.legacyforgebridge.convert.runtime;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Functional generic processor UI; source-specific MillStone presentation remains a later pass. */
public final class ConvertedLegacyProcessorScreen extends AbstractContainerScreen<ConvertedLegacyProcessorMenu> {
    private static final Identifier BACKGROUND=Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

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
        graphics.blit(RenderPipelines.GUI_TEXTURED,BACKGROUND,x,y,0.0F,0.0F,imageWidth,imageHeight,256,256);
    }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta){
        super.render(graphics,mouseX,mouseY,delta);
        if(menu.grinding()){
            graphics.drawString(font,"Progress "+menu.progressStage()+"/3",leftPos+62,topPos+38,0xFFFFFFFF,false);
        }
        renderTooltip(graphics,mouseX,mouseY);
    }
}
