package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Legacy section-sign styles shared by source tooltips and messages on either logical side. */
public final class LegacyText {
    private LegacyText() { }
    public static Component formatted(String value){
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
