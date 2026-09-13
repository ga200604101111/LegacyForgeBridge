package dev.longyu.legacyforgebridge.behavior;

import java.util.*;
import java.util.function.BiFunction;

/** Narrow, version-independent API for verified source-compiled callbacks, never a bytecode interpreter. */
public final class LegacyBehaviorApi {
    private LegacyBehaviorApi() { }
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();
    public static void begin(String mod, BiFunction<String,Object[],String> translate) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested legacy behavior invocation");
        CURRENT.set(new Invocation(mod, translate));
    }
    public static void end() { CURRENT.remove(); }
    public static void step() {
        Invocation call = CURRENT.get();
        if (call == null || --call.remaining < 0) throw new IllegalStateException("Legacy callback execution budget exceeded");
    }
    public static String translate(String key, Object[] args) {
        Invocation call = CURRENT.get();
        if (call == null) throw new IllegalStateException("Legacy callback outside invocation");
        return call.translate.apply("lfb.converted." + call.mod + "." + key, args);
    }
    private static final class Invocation {
        final String mod; final BiFunction<String,Object[],String> translate; int remaining=100_000;
        Invocation(String mod, BiFunction<String,Object[],String> translate) { this.mod=mod;this.translate=translate; }
    }
    public static final class I18n {
        public static String func_135052_a(String key,Object[] args) { return translate(key,args); }
        public static String format(String key,Object... args) { return translate(key,args); }
    }
    public enum UseAction { none, eat, drink, block, bow }
    public static class Material { }
    public static class CreativeTab { }
    public static class Item {
        private String name="";
        public int maximumStackSize=64, durability=0, armorSlot=-1;
        public Item() { }
        public Item func_77655_b(String value) { name=value;return this; }
        public Item setUnlocalizedName(String value) { return func_77655_b(value); }
        public String func_77658_a() { return "item."+name; }
        public String getUnlocalizedName() { return func_77658_a(); }
        public Item func_111206_d(String texture) { return this; }
        public Item setTextureName(String texture) { return this; }
        public Item func_77637_a(CreativeTab tab) { return this; }
        public Item setCreativeTab(CreativeTab tab) { return this; }
        public Item func_77656_e(int value) { durability=value;return this; }
        public Item setMaxDamage(int value) { return func_77656_e(value); }
        public Item func_77625_d(int value) { maximumStackSize=value;return this; }
        public Item setMaxStackSize(int value) { return func_77625_d(value); }
        public Item func_77664_n() { return this; }
        public Item setFull3D() { return this; }
        public void func_77624_a(Stack stack, Player player, List<String> lines, boolean advanced) { }
        public void addInformation(Stack stack, Player player, List<String> lines, boolean advanced) { func_77624_a(stack,player,lines,advanced); }
        public void func_77663_a(Stack stack, World world, Entity entity, int index, boolean selected) { }
        public void onUpdate(Stack stack, World world, Entity entity, int index, boolean selected) { func_77663_a(stack,world,entity,index,selected); }
        public void onArmorTick(World world,Player player,Stack stack) { }
        public Stack func_77659_a(Stack stack,World world,Player player) { return stack; }
        public UseAction func_77661_b(Stack stack) { return UseAction.none; }
        public int func_77626_a(Stack stack) { return 0; }
        public void func_77615_a(Stack stack,World world,Player player,int remaining) { }

    }
    public static class Sword extends Item {
        public Sword(Material material) { maximumStackSize=1; }
        @Override public Stack func_77659_a(Stack stack,World world,Player player) { player.func_71008_a(stack,func_77626_a(stack));return stack; }
        @Override public UseAction func_77661_b(Stack stack) { return UseAction.block; }
        @Override public int func_77626_a(Stack stack) { return 72000; }
    }
    public static class Armor extends Item { public Armor(Material material,int render,int slot) { armorSlot=slot;maximumStackSize=1; } }
    public static class Stack {
        public int field_77994_a=1;
        public Item item;
        public Tag tag;
        public Object handle;
        public Stack(Item item,Tag tag) { this.item=item;this.tag=tag; }
        public Tag func_77978_p() { return tag; }
        public Tag getTagCompound() { return tag; }
        public boolean func_77942_o() { return tag!=null; }
        public boolean hasTagCompound() { return func_77942_o(); }
        public Item func_77973_b() { return item; }
        public Item getItem() { return item; }
        public void func_77982_d(Tag value) { tag=value; }
        public void setTagCompound(Tag value) { tag=value; }
    }
    public static class Tag {
        public final Map<String,Object> values = new LinkedHashMap<>();
        public Tag() { }
        public Tag(Map<String,Object> data) { values.putAll(data); }
        public int func_74762_e(String key) { Object v=values.get(key);return v instanceof Number n?n.intValue():0; }
        public int getInteger(String key) { return func_74762_e(key); }
        public String func_74779_i(String key) { Object v=values.get(key);return v instanceof String s?s:""; }
        public String getString(String key) { return func_74779_i(key); }
        public float func_74760_g(String key) { Object v=values.get(key);return v instanceof Number n?n.floatValue():0; }
        public float getFloat(String key) { return func_74760_g(key); }
        public double func_74769_h(String key) { Object v=values.get(key);return v instanceof Number n?n.doubleValue():0; }
        public double getDouble(String key) { return func_74769_h(key); }
        public boolean func_74767_n(String key) { return func_74762_e(key)!=0; }
        public boolean getBoolean(String key) { return func_74767_n(key); }
        public boolean func_74764_b(String key) { return values.containsKey(key); }
        public boolean hasKey(String key) { return func_74764_b(key); }
        public void func_74768_a(String key,int value) { values.put(key,value); }
        public void setInteger(String key,int value) { func_74768_a(key,value); }
        public void func_74778_a(String key,String value) { values.put(key,value); }
        public void setString(String key,String value) { func_74778_a(key,value); }
        public void func_74776_a(String key,float value) { values.put(key,value); }
        public void setFloat(String key,float value) { func_74776_a(key,value); }
        public void func_74780_a(String key,double value) { values.put(key,value); }
        public void setDouble(String key,double value) { func_74780_a(key,value); }
        public void func_74757_a(String key,boolean value) { values.put(key,(byte)(value?1:0)); }
        public void setBoolean(String key,boolean value) { func_74757_a(key,value); }
        public void func_82580_o(String key) { values.remove(key); }
        public void removeTag(String key) { func_82580_o(key); }
    }
    public static class Entity {
        public World field_70170_p;
        public boolean field_70128_L,field_70133_I,crouching;
        public double field_70165_t,field_70163_u,field_70161_v,field_70181_x;
        public float field_70130_N=.6F,field_70131_O=1.8F;
        public Object handle;
        public boolean func_70051_ag() { return crouching; }
        public boolean isSneaking() { return crouching; }
    }
    public static class Living extends Entity {
        public final Stack[] equipment=new Stack[5];
        public final Map<Integer,Effect> effects=new HashMap<>();
        public final List<Effect> appliedEffects=new ArrayList<>();
        public float health=20;
        public Stack func_71124_b(int slot) { return slot>=0&&slot<equipment.length?equipment[slot]:null; }
        public Stack getEquipmentInSlot(int slot) { return func_71124_b(slot); }
        public Stack func_70694_bm() { return equipment[0]; }
        public Stack getHeldItem() { return func_70694_bm(); }
        public Effect func_70660_b(Potion potion) { return effects.get(potion.field_76415_H); }
        public Effect getActivePotionEffect(Potion potion) { return func_70660_b(potion); }
        public void func_70690_d(Effect effect) { effects.put(effect.id,effect);appliedEffects.add(effect); }
        public void addPotionEffect(Effect effect) { func_70690_d(effect); }
        public float func_110143_aJ() { return health; }
        public float getHealth() { return health; }
        public void func_70606_j(float value) { health=value; }
        public void setHealth(float value) { health=value; }
    }
    public static class Player extends Living {
        public Stack requestedUse;
        public int requestedDuration;
        public void func_71008_a(Stack stack,int duration) { requestedUse=stack;requestedDuration=duration; }
        public void setItemInUse(Stack stack,int duration) { func_71008_a(stack,duration); }
    }
    public static class World {
        public boolean field_72995_K;
        public final List<Particle> particles=new ArrayList<>();
        public void func_72869_a(String type,double x,double y,double z,double dx,double dy,double dz) {
            if (particles.size()>=256) throw new IllegalStateException("Legacy particle budget exceeded");
            particles.add(new Particle(type,x,y,z,dx,dy,dz));
        }
        public void spawnParticle(String type,double x,double y,double z,double dx,double dy,double dz) { func_72869_a(type,x,y,z,dx,dy,dz); }
    }
    public record Particle(String type,double x,double y,double z,double dx,double dy,double dz) { }
    public static class Potion {
        public final int field_76415_H;
        public Potion(int id) { field_76415_H=id; }
        public static final Potion field_76439_r=new Potion(16),field_76427_o=new Potion(13);
        public static final Potion nightVision=field_76439_r,waterBreathing=field_76427_o;
    }
    public static class Effect {
        public final int id,duration,amplifier;
        public Effect(int id,int duration,int amplifier) { this.id=id;this.duration=duration;this.amplifier=amplifier; }
        public int func_76459_b() { return duration; }
        public int getDuration() { return duration; }
    }
    public static class Damage {
        public Entity attacker;
        public Entity func_76364_f() { return attacker; }
        public Entity getEntity() { return attacker; }
    }
    public static class Event {
        public Living entityLiving;
        public Damage source=new Damage();
        public float ammount,distance,damageMultiplier=1;
        private boolean canceled;
        public void setCanceled(boolean value) { canceled=value; }
        public boolean isCanceled() { return canceled; }
    }
    @FunctionalInterface public interface EventProgram { void run(Event event); }
}
