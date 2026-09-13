package dev.longyu.legacyforgebridge.behavior;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Native state adapters shared by source-compiled item callbacks and events. */
public final class LegacyBehaviorRuntime {
    private LegacyBehaviorRuntime() { }
    private static final Set<String> WARNED=ConcurrentHashMap.newKeySet();
    private static BiFunction<String,Object[],String> translations=(key,args)->key;
    public static void setTranslations(BiFunction<String,Object[],String> value) { translations=value; }

    /** Same native blocking data that ViaVersion attaches to its five vanilla legacy swords. */
    public static BlocksAttacks legacyBlocking() {
        return new BlocksAttacks(0F,0F,
                List.of(new BlocksAttacks.DamageReduction(90F,Optional.empty(),-.5F,.5F)),
                new BlocksAttacks.ItemDamageFunction(0F,0F,0F),Optional.empty(),Optional.empty(),Optional.empty());
    }
    public static LegacyBehaviorRegistry.ItemDefinition definition(ItemStack stack) {
        return stack.isEmpty()?null:LegacyBehaviorRegistry.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public static <T> T invoke(String mod,String hook,Supplier<T> operation,T fallback) {
        LegacyBehaviorApi.begin(mod,translations);
        try { return operation.get(); }
        catch (RuntimeException | LinkageError error) {
            if(WARNED.add(mod+":"+hook)) LegacyForgeBridge.LOGGER.warn("Source-compiled callback rejected at runtime: mod={}, hook={}",mod,hook,error);
            return fallback;
        } finally { LegacyBehaviorApi.end(); }
    }
    public static LegacyBehaviorApi.Tag tag(ItemStack stack) {
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null) return null;
        JsonElement json=NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE,data.copyTag());
        if(!json.isJsonObject()) return null;
        Map<String,Object> values=new LinkedHashMap<>();
        json.getAsJsonObject().entrySet().forEach(e->{
            if(e.getValue().isJsonPrimitive()) {
                var p=e.getValue().getAsJsonPrimitive();
                values.put(e.getKey(),p.isString()?p.getAsString():p.isBoolean()?(byte)(p.getAsBoolean()?1:0):p.getAsNumber());
            } else values.put(e.getKey(),e.getValue());
        });
        return new LegacyBehaviorApi.Tag(values);
    }
    public static LegacyBehaviorApi.UseAction action(String id,LegacyBehaviorApi.Tag tag) {
        var d=LegacyBehaviorRegistry.item(id);
        if(d==null||!d.hooks().contains("action"))return LegacyBehaviorApi.UseAction.none;
        return invoke(d.mod(),id+"/action",()->d.item().func_77661_b(new LegacyBehaviorApi.Stack(d.item(),tag)),LegacyBehaviorApi.UseAction.none);
    }
    public static ItemUseAnimation animation(ItemStack stack) {
        return switch(action(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),tag(stack))) {
            case block->ItemUseAnimation.BLOCK;case bow->ItemUseAnimation.BOW;
            case eat->ItemUseAnimation.EAT;case drink->ItemUseAnimation.DRINK;default->ItemUseAnimation.NONE;
        };
    }
    public static int duration(ItemStack stack) {
        var d=definition(stack);if(d==null||!d.hooks().contains("duration"))return 0;
        return Math.max(0,invoke(d.mod(),"duration",()->d.item().func_77626_a(new Snapshot(null).stack(stack)),0));
    }
    public static void synchronizeBlocking(ItemStack stack) {
        var d=definition(stack);if(d==null||!d.hooks().contains("action"))return;
        if(animation(stack)==ItemUseAnimation.BLOCK)stack.set(DataComponents.BLOCKS_ATTACKS,legacyBlocking());
        else stack.remove(DataComponents.BLOCKS_ATTACKS);
    }
    public static void tooltip(ItemStack stack,Player player,boolean advanced,List<String> lines) {
        var d=definition(stack);if(d==null||!d.hooks().contains("tooltip"))return;
        Snapshot snapshot=new Snapshot(player==null?null:player.level());
        invoke(d.mod(),"tooltip/"+BuiltInRegistries.ITEM.getKey(stack.getItem()),()->{
            d.item().func_77624_a(snapshot.stack(stack),player==null?new LegacyBehaviorApi.Player():(LegacyBehaviorApi.Player)snapshot.living(player),lines,advanced);
            return true;
        },false);
    }
    public static boolean startUse(ItemStack stack,Player player) {
        var d=definition(stack);if(d==null||!d.hooks().contains("use"))return false;
        Snapshot s=new Snapshot(player.level());LegacyBehaviorApi.Player p=(LegacyBehaviorApi.Player)s.living(player);
        LegacyBehaviorApi.Stack input=s.stack(stack);
        boolean completed=invoke(d.mod(),"use",()->{d.item().func_77659_a(input,s.world,p);return true;},false);
        if(completed)s.commit();
        return completed&&p.requestedUse==input&&p.requestedDuration>0;
    }
    public static void release(ItemStack stack,LivingEntity entity,int remaining) {
        var d=definition(stack);if(d==null||!d.hooks().contains("release")||!(entity instanceof Player player))return;
        // Remote 1.7.10 servers execute the original skill release. Never run server effects twice.
        if(player.level().isClientSide())return;
        Snapshot s=new Snapshot(player.level());var p=(LegacyBehaviorApi.Player)s.living(player);
        if(invoke(d.mod(),"release",()->{d.item().func_77615_a(s.stack(stack),s.world,p,remaining);return true;},false))s.commit();
    }
    public static void inventoryTick(Player player) {
        if(player.level().isClientSide())return;
        Snapshot s=new Snapshot(player.level());var p=(LegacyBehaviorApi.Player)s.living(player);
        for(int index=0;index<player.getInventory().getContainerSize();index++) {
            ItemStack item=player.getInventory().getItem(index);var d=definition(item);if(d==null)continue;
            final int slot=index;
            if(d.hooks().contains("tick"))invoke(d.mod(),"tick",()->{
                d.item().func_77663_a(s.stack(item),s.world,p,slot,item==player.getMainHandItem());return true;
            },false);
        }
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            ItemStack item=player.getItemBySlot(slot);var d=definition(item);
            if(d!=null&&d.hooks().contains("armorTick"))invoke(d.mod(),"armorTick",()->{
                d.item().onArmorTick(s.world,p,s.stack(item));return true;
            },false);
        }
        s.commit();
    }
    public static void jump(LivingEntity entity) {
        if(!clientActorAllowed(entity))return;
        runEvent("jump",entity,null,0F,0F,1F);
    }
    public static boolean fall(LivingEntity entity,double distance,float multiplier,DamageSource damage) {
        if(!clientActorAllowed(entity))return false;
        return runEvent("fall",entity,damage,0F,(float)distance,multiplier).isCanceled();
    }
    public static float hurt(LivingEntity entity,DamageSource damage,float amount) {
        if(entity.level().isClientSide()||!Float.isFinite(amount))return amount;
        var event=runEvent("hurt",entity,damage,amount,0F,1F);
        return event.isCanceled()?0F:Float.isFinite(event.ammount)?Math.max(0F,event.ammount):amount;
    }
    private static boolean clientActorAllowed(LivingEntity entity) {
        // Player.isLocalPlayer is client-specific, so use the client-side predicate installed by the entrypoint.
        return !entity.level().isClientSide()||localPlayer.test(entity);
    }
    private static java.util.function.Predicate<LivingEntity> localPlayer=e->false;
    public static void setLocalPlayer(java.util.function.Predicate<LivingEntity> value){localPlayer=value;}
    private static LegacyBehaviorApi.Event runEvent(String kind,LivingEntity entity,DamageSource source,float amount,float distance,float multiplier) {
        Snapshot s=new Snapshot(entity.level());var e=new LegacyBehaviorApi.Event();
        e.entityLiving=s.living(entity);e.ammount=amount;e.distance=distance;e.damageMultiplier=multiplier;
        if(source!=null)e.source.attacker=s.entity(source.getEntity());
        for(var program:LegacyBehaviorRegistry.events(kind)) {
            if(e.isCanceled())break;
            invoke(program.mod(),"event/"+kind,()->{program.program().run(e);return true;},false);
        }
        s.commit();return e;
    }
    private static Holder<MobEffect> effect(int id){return switch(id){case 16->MobEffects.NIGHT_VISION;case 13->MobEffects.WATER_BREATHING;default->null;};}

    private static final class Snapshot {
        final Level level;final LegacyBehaviorApi.World world=new LegacyBehaviorApi.World();
        final IdentityHashMap<ItemStack,LegacyBehaviorApi.Stack> stacks=new IdentityHashMap<>();
        final IdentityHashMap<ItemStack,Map<String,Object>> originalTags=new IdentityHashMap<>();
        final IdentityHashMap<Entity,LegacyBehaviorApi.Entity> entities=new IdentityHashMap<>();
        Snapshot(Level level){this.level=level;world.field_72995_K=level!=null&&level.isClientSide();}
        LegacyBehaviorApi.Stack stack(ItemStack nativeStack){
            if(nativeStack==null||nativeStack.isEmpty())return null;
            return stacks.computeIfAbsent(nativeStack,k->{
                var d=definition(k);var item=d==null?new LegacyBehaviorApi.Item():d.item();
                var s=new LegacyBehaviorApi.Stack(item,tag(k));s.field_77994_a=k.getCount();s.handle=k;
                originalTags.put(k,s.tag==null?Map.of():new LinkedHashMap<>(s.tag.values));return s;
            });
        }
        LegacyBehaviorApi.Entity entity(Entity entity){
            if(entity==null)return null;if(entity instanceof LivingEntity living)return living(living);
            return entities.computeIfAbsent(entity,k->{var e=new LegacyBehaviorApi.Entity();fill(k,e);return e;});
        }
        LegacyBehaviorApi.Living living(LivingEntity nativeEntity){
            var old=entities.get(nativeEntity);if(old!=null)return (LegacyBehaviorApi.Living)old;
            LegacyBehaviorApi.Living e=nativeEntity instanceof Player?new LegacyBehaviorApi.Player():new LegacyBehaviorApi.Living();
            entities.put(nativeEntity,e);fill(nativeEntity,e);e.health=nativeEntity.getHealth();
            e.equipment[0]=stack(nativeEntity.getMainHandItem());e.equipment[1]=stack(nativeEntity.getItemBySlot(EquipmentSlot.FEET));
            e.equipment[2]=stack(nativeEntity.getItemBySlot(EquipmentSlot.LEGS));e.equipment[3]=stack(nativeEntity.getItemBySlot(EquipmentSlot.CHEST));
            e.equipment[4]=stack(nativeEntity.getItemBySlot(EquipmentSlot.HEAD));
            for(int id:new int[]{13,16}){var effect=nativeEntity.getEffect(effect(id));if(effect!=null)e.effects.put(id,new LegacyBehaviorApi.Effect(id,effect.getDuration(),effect.getAmplifier()));}
            return e;
        }
        void fill(Entity nativeEntity,LegacyBehaviorApi.Entity e){
            e.handle=nativeEntity;e.field_70170_p=world;e.field_70128_L=!nativeEntity.isAlive();e.crouching=nativeEntity.isCrouching();
            e.field_70165_t=nativeEntity.getX();e.field_70163_u=nativeEntity.getY();e.field_70161_v=nativeEntity.getZ();
            e.field_70181_x=nativeEntity.getDeltaMovement().y;e.field_70130_N=nativeEntity.getBbWidth();e.field_70131_O=nativeEntity.getBbHeight();
        }
        void commit(){
            for(var pair:entities.entrySet()) {
                Entity nativeEntity=pair.getKey();var e=pair.getValue();
                if(Double.isFinite(e.field_70181_x)&&e.field_70181_x!=nativeEntity.getDeltaMovement().y)
                    nativeEntity.setDeltaMovement(nativeEntity.getDeltaMovement().x,e.field_70181_x,nativeEntity.getDeltaMovement().z);
                if(!world.field_72995_K&&nativeEntity instanceof LivingEntity living&&e instanceof LegacyBehaviorApi.Living le){
                    if(Float.isFinite(le.health)&&le.health!=living.getHealth())living.setHealth(le.health);
                    for(var applied:le.appliedEffects){var type=effect(applied.id);if(type!=null)living.addEffect(new MobEffectInstance(type,applied.duration,applied.amplifier));}
                }
            }
            if(!world.field_72995_K)for(var pair:stacks.entrySet()) {
                ItemStack nativeStack=pair.getKey();var view=pair.getValue();
                if(view.field_77994_a>=0&&view.field_77994_a<=nativeStack.getMaxStackSize()&&view.field_77994_a!=nativeStack.getCount())nativeStack.setCount(view.field_77994_a);
                Map<String,Object> before=originalTags.get(nativeStack);
                if(view.tag==null){if(!before.isEmpty())nativeStack.remove(DataComponents.CUSTOM_DATA);continue;}
                if(before.equals(view.tag.values))continue;
                CustomData custom=nativeStack.get(DataComponents.CUSTOM_DATA);
                CompoundTag updated=custom==null?new CompoundTag():custom.copyTag();
                for(String key:before.keySet())if(!view.tag.values.containsKey(key))updated.remove(key);
                for(var field:view.tag.values.entrySet()) {
                    String key=field.getKey();Object value=field.getValue();if(Objects.equals(before.get(key),value))continue;
                    if(value instanceof String text)updated.putString(key,text);
                    else if(value instanceof Byte n)updated.putByte(key,n);
                    else if(value instanceof Integer n)updated.putInt(key,n);
                    else if(value instanceof Long n)updated.putLong(key,n);
                    else if(value instanceof Float n&&Float.isFinite(n))updated.putFloat(key,n);
                    else if(value instanceof Double n&&Double.isFinite(n))updated.putDouble(key,n);
                }
                nativeStack.set(DataComponents.CUSTOM_DATA,CustomData.of(updated));
            }
            // Legacy world.spawnParticle was a local visual effect; do not broadcast an extra server effect.
            if(level!=null&&world.field_72995_K)for(var p:world.particles){
                if(p.type().equals("flame"))level.addParticle(ParticleTypes.FLAME,p.x(),p.y(),p.z(),p.dx(),p.dy(),p.dz());
                else if(WARNED.add("particle/"+p.type()))LegacyForgeBridge.LOGGER.warn("Unsupported legacy particle name: {}",p.type());
            }
        }
    }
}
