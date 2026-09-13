package dev.longyu.legacyforgebridge.behavior;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;

public final class LegacyBehaviorClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        LegacyBehaviorRuntime.setLocalPlayer(e->e==Minecraft.getInstance().player);
        LegacyBehaviorRuntime.setTranslations((key,args)->{
            if(I18n.exists(key))return I18n.get(key,args);
            int keyStart=key.indexOf('.',"lfb.converted.".length());
            String source=keyStart<0?key:key.substring(keyStart+1);
            return I18n.exists(source)?I18n.get(source,args):source;
        });
        ItemTooltipCallback.EVENT.register((stack,context,flag,lines)->{
            var extra=new ArrayList<String>();
            LegacyBehaviorRuntime.tooltip(stack,Minecraft.getInstance().player,flag.isAdvanced(),extra);
            for(String line:extra.stream().limit(128).toList())if(line!=null)lines.add(formatted(line.length()>4096?line.substring(0,4096):line));
        });
        // Integrated Fabric worlds only. A remote Forge server already runs its original handlers.
        ServerTickEvents.END_SERVER_TICK.register(server->server.getPlayerList().getPlayers().forEach(LegacyBehaviorRuntime::inventoryTick));
    }
    static Component formatted(String value){
        MutableComponent result=Component.empty();Style style=Style.EMPTY;StringBuilder text=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='\u00a7'&&i+1<value.length()){
                if(!text.isEmpty()){result.append(Component.literal(text.toString()).setStyle(style));text.setLength(0);}
                ChatFormatting format=ChatFormatting.getByCode(value.charAt(++i));
                if(format!=null)style=format==ChatFormatting.RESET?Style.EMPTY:style.applyLegacyFormat(format);
            }else text.append(c);
        }
        if(!text.isEmpty())result.append(Component.literal(text.toString()).setStyle(style));
        return result;
    }
}
