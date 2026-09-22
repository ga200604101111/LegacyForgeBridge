package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyRotatingAssemblyEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Client-side carrier for source-proven large rotating legacy Entity assemblies.
 *
 * <p>Interaction, drops and lifecycle remain authoritative on the 1.7.10 server. The modern
 * carrier replays only source-proven watcher state, custom collision bounds and visual roll.</p>
 */
public final class ConvertedLegacyRotatingAssemblyEntity extends Entity {
    private final LegacyRotatingAssemblyEntityRegistry.Rule rule;
    private final int[] watchers=new int[32];
    private final boolean[] known=new boolean[32];
    private float visualRoll;
    private boolean phaseInitialized;

    public ConvertedLegacyRotatingAssemblyEntity(EntityType<? extends ConvertedLegacyRotatingAssemblyEntity> type,Level level,
                                                 LegacyRotatingAssemblyEntityRegistry.Rule rule){
        super(type,level);this.rule=rule;installDefaults();
    }

    public LegacyRotatingAssemblyEntityRegistry.Rule assemblyRule(){return rule;}

    public boolean applyLegacyWatcher(int type,int id,Object value){
        if(type!=0||id<0||id>=watchers.length||!(value instanceof Byte number)||!isKnownWatcher(id))return false;
        watchers[id]=number.byteValue();known[id]=true;
        if(id==rule.directionWatcher()||id==rule.sizeWatcher())refreshLegacyBounds();
        return true;
    }

    public int direction(){return watcher(rule.directionWatcher(),rule.directionDefault())&3;}
    public int size(){return clamp(watcher(rule.sizeWatcher(),rule.sizeDefault()),rule.sizeMin(),rule.sizeMax());}
    public int repeatCount(){
        if(rule.adapter()!=LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL)return rule.fixedRepeatCount();
        int offset=clamp(watcher(rule.countWatcher(),rule.countDefault()),0,Math.max(0,rule.countMax()-rule.countBase()));
        return clamp(rule.countBase()+offset,rule.countBase(),rule.countMax());
    }
    public int textureIndex(){
        if(rule.textureWatcher()<0)return 0;
        return clamp(watcher(rule.textureWatcher(),rule.textureDefault()),0,rule.textures().size()-1);
    }
    public boolean reverse(){return rule.reverseWatcher()>=0&&watcher(rule.reverseWatcher(),rule.reverseDefault())!=0;}
    public float visualRoll(){return visualRoll;}

    public void initializeVisualPhase(){
        if(phaseInitialized)return;
        long seed=((long)getId()<<32)^blockPosition().asLong()^0x9E3779B97F4A7C15L;
        visualRoll=Math.floorMod((int)(seed^(seed>>>32)),360);
        phaseInitialized=true;
    }

    public void refreshLegacyBounds(){
        int direction=direction(),size=size();double s=size;
        double x=getX(),y=getY(),z=getZ();
        AABB box;
        if(rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL){
            if((direction&1)==0)box=new AABB(x-1.5D*s,y-1.5D*s,z,x+2.5D*s,y+2.0D*s,z+0.5D*s);
            else box=new AABB(x,y-1.5D*s,z-1.5D*s,x+0.5D*s,y+1.5D*s,z+2.5D*s);
        }else{
            if((direction&1)==0)box=new AABB(x-1.5D*s,y-2.0D*s,z,x+2.5D*s,y+2.0D*s,z+0.5D*s);
            else box=new AABB(x-1.5D*s,y-2.0D*s,z-1.5D*s,x+0.5D*s,y+1.5D*s,z+2.5D*s);
        }
        setBoundingBox(box);
    }

    @Override public void tick(){
        super.tick();initializeVisualPhase();refreshLegacyBounds();
        if(rule.adapter()==LegacyRotatingAssemblyEntityRegistry.Adapter.VARIABLE_Z_RADIAL){
            visualRoll=wrap(visualRoll+1F);
        }else if(sourceWaterContact()){
            visualRoll=wrap(visualRoll+(reverse()?-1F:1F));
        }
    }

    private boolean sourceWaterContact(){
        AABB box=getBoundingBox().deflate(.001D,.4010000059604645D,.001D);
        int minX=(int)Math.floor(box.minX),maxX=(int)Math.floor(box.maxX);
        int minY=(int)Math.floor(box.minY),maxY=(int)Math.floor(box.maxY);
        int minZ=(int)Math.floor(box.minZ),maxZ=(int)Math.floor(box.maxZ);
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
            cursor.set(x,y,z);
            if(level().getFluidState(cursor).is(FluidTags.WATER))return true;
        }
        return false;
    }

    @Override public boolean canCollideWith(Entity other){return rule.physicalCollision()&&!isRemoved();}
    @Override public boolean canBeCollidedWith(Entity other){return rule.physicalCollision()&&!isRemoved();}
    @Override public boolean isPickable(){return rule.playerAttackRemoves()&&!isRemoved();}
    @Override public boolean isAttackable(){return rule.playerAttackRemoves()&&!isRemoved();}

    private void installDefaults(){
        put(rule.directionWatcher(),rule.directionDefault());put(rule.sizeWatcher(),rule.sizeDefault());
        put(rule.countWatcher(),rule.countDefault());put(rule.textureWatcher(),rule.textureDefault());put(rule.reverseWatcher(),rule.reverseDefault());
    }
    private void put(int index,int value){if(index>=0&&index<watchers.length){watchers[index]=value;known[index]=true;}}
    private int watcher(int index,int fallback){return index>=0&&index<watchers.length&&known[index]?watchers[index]:fallback;}
    private boolean isKnownWatcher(int index){
        return index==rule.directionWatcher()||index==rule.sizeWatcher()||index==rule.countWatcher()
                ||index==rule.textureWatcher()||index==rule.reverseWatcher();
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private static float wrap(float degrees){degrees%=360F;if(degrees<0F)degrees+=360F;return degrees;}

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    @Override protected void readAdditionalSaveData(ValueInput input){}
    @Override protected void addAdditionalSaveData(ValueOutput output){}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
}
