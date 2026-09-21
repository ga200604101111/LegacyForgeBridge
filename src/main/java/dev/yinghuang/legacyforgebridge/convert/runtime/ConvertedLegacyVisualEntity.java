package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyVisibleEntityRegistry;
import dev.yinghuang.legacyforgebridge.network.FmlRuntimeCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;

/** Client presentation carrier for a source-proven visible legacy Entity family. */
public final class ConvertedLegacyVisualEntity extends Entity {
    private final LegacyVisibleEntityRegistry.Rule rule;
    private final Object[] legacyWatchers=new Object[32];

    public ConvertedLegacyVisualEntity(EntityType<? extends ConvertedLegacyVisualEntity> type,Level level,
                                       LegacyVisibleEntityRegistry.Rule rule){
        super(type,level);this.rule=rule;installDefaults();
    }

    public LegacyVisibleEntityRegistry.Rule visualRule(){return rule;}

    public boolean applyLegacyWatcher(int type,int id,Object value){
        if(id<0||id>=legacyWatchers.length||value==null)return false;
        boolean semantic=switch(rule.adapter()){
            case SLIDE_PANEL -> applySlide(type,id,value);
            case TINTED_CUSHION -> applyCushion(type,id,value);
            case TRAY_ITEMS -> applyTray(type,id,value);
        };
        if(semantic)return true;
        Integer expected=rule.watcherTypes().get(id);
        if(expected==null||expected!=type||!wireValue(type,value))return false;
        legacyWatchers[id]=value;return true;
    }

    private static boolean wireValue(int type,Object value){
        return switch(type){
            case 0->value instanceof Byte;
            case 1->value instanceof Short;
            case 2->value instanceof Integer;
            case 3->value instanceof Float;
            case 4->value instanceof String;
            case 5->value instanceof FmlRuntimeCodec.LegacyItemStack;
            case 6->true;
            default->false;
        };
    }

    public int watcherInt(String semantic,int fallback){
        Integer index=rule.watcherIndices().get(semantic);if(index==null)return fallback;Object value=legacyWatchers[index];
        return value instanceof Number n?n.intValue():fallback;
    }

    public FmlRuntimeCodec.LegacyItemStack watcherStack(int slot){
        if(rule.adapter()!=LegacyVisibleEntityRegistry.Adapter.TRAY_ITEMS||slot<0||slot>=rule.itemWatcherCount())return FmlRuntimeCodec.LegacyItemStack.EMPTY;
        Object value=legacyWatchers[rule.itemWatcherBase()+slot];
        return value instanceof FmlRuntimeCodec.LegacyItemStack stack?stack:FmlRuntimeCodec.LegacyItemStack.EMPTY;
    }

    private boolean applySlide(int type,int id,Object value){
        Integer direction=rule.watcherIndices().get("direction"),mirror=rule.watcherIndices().get("mirror"),texture=rule.watcherIndices().get("texture");
        if((id==direction||id==mirror)&&type==0&&value instanceof Byte){legacyWatchers[id]=value;return true;}
        if(id==texture&&type==1&&value instanceof Short){legacyWatchers[id]=value;return true;}
        return false;
    }

    private boolean applyCushion(int type,int id,Object value){
        Integer color=rule.watcherIndices().get("color");
        if(id==color&&type==0&&value instanceof Byte){legacyWatchers[id]=value;return true;}
        return false;
    }

    private boolean applyTray(int type,int id,Object value){
        if(id>=rule.itemWatcherBase()&&id<rule.itemWatcherBase()+rule.itemWatcherCount()
                &&type==5&&value instanceof FmlRuntimeCodec.LegacyItemStack){legacyWatchers[id]=value;return true;}
        return false;
    }

    private void installDefaults(){
        Arrays.fill(legacyWatchers,null);
        switch(rule.adapter()){
            case SLIDE_PANEL -> {
                legacyWatchers[rule.watcherIndices().get("direction")]=Byte.valueOf((byte)0);
                legacyWatchers[rule.watcherIndices().get("mirror")]=Byte.valueOf((byte)0);
                legacyWatchers[rule.watcherIndices().get("texture")]=Short.valueOf((short)0);
            }
            case TINTED_CUSHION -> legacyWatchers[rule.watcherIndices().get("color")]=Byte.valueOf((byte)15);
            case TRAY_ITEMS -> {
                for(int i=0;i<rule.itemWatcherCount();i++)legacyWatchers[rule.itemWatcherBase()+i]=FmlRuntimeCodec.LegacyItemStack.EMPTY;
            }
        }
    }

    /**
     * Legacy furniture entities such as trays, cushions and sliding doors explicitly returned
     * true from Entity#canBeCollidedWith while alive. Keep that source-visible contract on the
     * modern carrier so the local client resolves entity collision instead of falling through
     * and waiting for the 1.7 server to rubber-band the player back up.
     */
    @Override public boolean canCollideWith(Entity other){return rule.physicalCollision()&&!isRemoved();}

    /**
     * The same legacy entities were attackable/pickable. Without this override the converted
     * carrier can render correctly but the modern client never sends the interact/attack packet
     * for the server-owned legacy entity, so breaking it appears to do nothing.
     */
    @Override public boolean isPickable(){return rule.playerAttackRemoves()&&!isRemoved();}
    @Override public boolean isAttackable(){return rule.playerAttackRemoves()&&!isRemoved();}

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    @Override protected void readAdditionalSaveData(ValueInput input){}
    @Override protected void addAdditionalSaveData(ValueOutput output){}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
}
