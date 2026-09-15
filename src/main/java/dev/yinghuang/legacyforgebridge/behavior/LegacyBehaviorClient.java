package dev.yinghuang.legacyforgebridge.behavior;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            TooltipComponents working=new TooltipComponents(lines);
            if(LegacyBehaviorRuntime.tooltipEvent(stack,working))working.commitTo(lines);
        });
        // Integrated Fabric worlds only. A remote Forge server already runs its original handlers.
        ServerTickEvents.END_SERVER_TICK.register(server->server.getPlayerList().getPlayers().forEach(LegacyBehaviorRuntime::inventoryTick));
    }
    static Component formatted(String value) { return LegacyText.formatted(value); }
    static final class TooltipComponents extends AbstractList<String> {
        private final List<Component> values;
        TooltipComponents(List<Component> source){values=new ArrayList<>(Objects.requireNonNull(source));}
        @Override public String get(int index){return values.get(index).getString();}
        @Override public int size(){return values.size();}
        @Override public String set(int index,String value){
            Objects.requireNonNull(value);Component old=values.get(index);String before=old.getString();Component replacement;
            if(value.startsWith(before)){
                MutableComponent copy=old.copy();String suffix=value.substring(before.length());
                if(!suffix.isEmpty())copy.append(formatted(suffix));replacement=copy;
            }else replacement=formatted(value);
            values.set(index,replacement);return before;
        }
        @Override public void add(int index,String value){Objects.requireNonNull(value);if(values.size()>=256)throw new IllegalStateException("Source tooltip line budget exceeded");values.add(index,formatted(value));}
        @Override public String remove(int index){return values.remove(index).getString();}
        void commitTo(List<Component> target){target.clear();target.addAll(values);}
    }
}
