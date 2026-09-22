package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyProjectilePresentationRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Client presentation carrier for a source-proven legacy remote projectile.
 *
 * <p>Gameplay remains authoritative on the 1.7.10 server. This entity only mirrors the remote
 * identity, pose, velocity baseline and proven presentation selector; it never replays source
 * impact/damage/explosion code on the modern client.</p>
 */
public final class ConvertedLegacyRemoteProjectile extends Entity implements ItemSupplier {
    private final LegacyProjectilePresentationRegistry.Rule rule;
    private Object selectorValue;

    public ConvertedLegacyRemoteProjectile(EntityType<? extends ConvertedLegacyRemoteProjectile> type,Level level,
                                           LegacyProjectilePresentationRegistry.Rule rule){
        super(type,level);this.rule=rule;
    }

    public LegacyProjectilePresentationRegistry.Rule projectileRule(){return rule;}

    public boolean applyLegacyWatcher(int type,int id,Object value){
        if(rule.metadataWatcherIndex()<0)return false;
        if(id!=rule.metadataWatcherIndex()||type!=rule.metadataWatcherWireType()||!(value instanceof Number))return false;
        selectorValue=value;return true;
    }

    public int legacyItemMetadata(){return rule.itemMetadata(selectorValue);}

    @Override public ItemStack getItem(){
        Item item=BuiltInRegistries.ITEM.containsKey(rule.itemId())?BuiltInRegistries.ITEM.getValue(rule.itemId()):null;
        if(item==null)item=Items.SNOWBALL;
        int metadata=legacyItemMetadata();
        ItemStack stack=ConvertedStackPresentation.create(item,metadata);
        ConvertedStackPresentation.apply(stack,metadata);
        return stack;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    @Override protected void readAdditionalSaveData(ValueInput input){}
    @Override protected void addAdditionalSaveData(ValueOutput output){}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
}
