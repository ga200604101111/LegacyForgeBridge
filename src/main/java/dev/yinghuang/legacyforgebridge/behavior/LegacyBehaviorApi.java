package dev.yinghuang.legacyforgebridge.behavior;

import java.util.*;
import com.google.common.collect.Multimap;
import com.google.common.collect.ArrayListMultimap;
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
    public static final class StatCollector {
        public static String func_74838_a(String key) { return translate(key,new Object[0]); }
        public static String translateToLocal(String key) { return func_74838_a(key); }
    }
    public enum UseAction { none, eat, drink, block, bow }
    public static class Material { }
    public static class CreativeTab { }
    public static class Block {
        public final String id;
        public Block(String id) { this.id=Objects.requireNonNull(id); }
    }
    public static class Item {
        private String name="";
        public int maximumStackSize=64, durability=0, armorSlot=-1;
        public static final UUID field_111210_e = UUID.fromString("cb3f55d3-645c-4f38-a497-9c13a33db5cf");
        public Item() { }
        public Multimap<String, Modifier> getAttributeModifiers(Stack stack) { return ArrayListMultimap.create(); }
        public boolean func_77644_a(Stack stack, Living target, Living attacker) { return false; }
        public boolean hitEntity(Stack stack, Living target, Living attacker) { return func_77644_a(stack,target,attacker); }
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
        public Item func_77627_a(boolean value) { return this; }
        public Item setHasSubtypes(boolean value) { return func_77627_a(value); }
        public Item func_77642_a(Item value) { return this; }
        public Item setContainerItem(Item value) { return func_77642_a(value); }
        public Item setNoRepair() { return this; }
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
    public static class ItemBlock extends Item {
        public final Block field_150939_a;
        public ItemBlock(Block block) { field_150939_a=Objects.requireNonNull(block); }
    }
    public static class Sword extends Item {
        public Sword(Material material) { maximumStackSize=1; }
        @Override public boolean func_77644_a(Stack stack,Living target,Living attacker) {
            stack.func_77972_a(1,attacker); return true;
        }
        @Override public Stack func_77659_a(Stack stack,World world,Player player) { player.func_71008_a(stack,func_77626_a(stack));return stack; }
        @Override public UseAction func_77661_b(Stack stack) { return UseAction.block; }
        @Override public int func_77626_a(Stack stack) { return 72000; }
    }
    public static class Armor extends Item { public Armor(Material material,int render,int slot) { armorSlot=slot;maximumStackSize=1; } }
    public static class Bow extends Item {
        public Bow() { maximumStackSize=1; }
        @Override public UseAction func_77661_b(Stack stack) { return UseAction.bow; }
        @Override public int func_77626_a(Stack stack) { return 72000; }
        @Override public Stack func_77659_a(Stack stack,World world,Player player) { player.func_71008_a(stack,func_77626_a(stack));return stack; }
    }
    public static class Tool extends Item { public Tool(Material material) { maximumStackSize=1; } }
    public static class Pickaxe extends Tool { public Pickaxe(Material material) { super(material); } }
    public static class Axe extends Tool { public Axe(Material material) { super(material); } }
    public static class Spade extends Tool { public Spade(Material material) { super(material); } }
    public static class Hoe extends Tool { public Hoe(Material material) { super(material); } }
    public static class Stack {
        public int field_77994_a=1;
        public Item item;
        /** Compatibility alias retained for existing bridge tests; legacy bytecode uses field_77990_d. */
        public Tag tag;
        public Tag field_77990_d;
        public Object handle;
        public Stack(Item item) { this(item,null); }
        public void func_77972_a(int amount,Living actor) {
            if (amount < 0 || amount > 100_000 || actor == null || actor.field_70170_p == null)
                throw new IllegalArgumentException("Invalid source durability request");
            if (amount > 0) actor.field_70170_p.command(new Durability(this,amount,actor));
        }
        public void damageItem(int amount,Living actor) { func_77972_a(amount,actor); }
        public Stack(Item item,Tag tag) { this.item=item;this.tag=tag;this.field_77990_d=tag; }
        public Tag func_77978_p() { return field_77990_d; }
        public Tag getTagCompound() { return field_77990_d; }
        public boolean func_77942_o() { return field_77990_d!=null; }
        public boolean hasTagCompound() { return func_77942_o(); }
        public Item func_77973_b() { return item; }
        public Item getItem() { return item; }
        public void func_77982_d(Tag value) { tag=value;field_77990_d=value; }
        public void setTagCompound(Tag value) { func_77982_d(value); }
    }
    public static class Tag {
        public final Map<String,Object> values = new LinkedHashMap<>();
        public Tag() { }
        public Tag(Map<String,Object> data) { values.putAll(data); }
        public int func_74762_e(String key) { Object v=values.get(key);return v instanceof Number n?n.intValue():0; }
        public int getInteger(String key) { return func_74762_e(key); }
        public short func_74765_d(String key) { Object v=values.get(key);return v instanceof Number n?n.shortValue():0; }
        public short getShort(String key) { return func_74765_d(key); }
        public TagList func_150295_c(String key,int type) { Object v=values.get(key);return type==10&&v instanceof TagList list?list:new TagList(); }
        public TagList getTagList(String key,int type) { return func_150295_c(key,type); }
        public String func_74779_i(String key) { Object v=values.get(key);return v instanceof String s?s:""; }
        public String getString(String key) { return func_74779_i(key); }
        public float func_74760_g(String key) { Object v=values.get(key);return v instanceof Number n?n.floatValue():0; }
        public float getFloat(String key) { return func_74760_g(key); }
        public double func_74769_h(String key) { Object v=values.get(key);return v instanceof Number n?n.doubleValue():0; }
        public double getDouble(String key) { return func_74769_h(key); }
        public boolean func_74767_n(String key) { Object value=values.get(key);return value instanceof Number number && number.byteValue()!=0; }
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
    public static class TagList {
        public final List<Tag> values=new ArrayList<>();
        public TagList() { }
        public TagList(Collection<Tag> values) {
            if(values.size()>4096)throw new IllegalArgumentException("Source NBT list budget exceeded");
            this.values.addAll(values);
        }
        public int func_74745_c() { return values.size(); }
        public int tagCount() { return func_74745_c(); }
        public Tag func_150305_b(int index) { return index>=0&&index<values.size()?values.get(index):new Tag(); }
        public Tag getCompoundTagAt(int index) { return func_150305_b(index); }
    }
    public static class Entity {
        public World field_70170_p;
        public boolean field_70128_L,field_70133_I,crouching;
        public double field_70165_t,field_70163_u,field_70161_v,field_70159_w,field_70181_x,field_70179_y;
        public Box field_70121_D = new Box(-.3,0,-.3,.3,1.8,.3);
        public Vec look = new Vec(0,0,1);
        public boolean burning;
        public boolean func_70027_ad() { return burning; }
        public boolean isBurning() { return burning; }
        public Vec func_70040_Z() { return look; }
        public Vec getLookVec() { return look; }
        public void func_70024_g(double x,double y,double z) {
            requireFinite(x,y,z);
            field_70159_w+=x; field_70181_x+=y; field_70179_y+=z; field_70133_I=true;
        }
        public void addVelocity(double x,double y,double z) { func_70024_g(x,y,z); }
        public boolean func_70097_a(Damage source,float amount) {
            if (!Float.isFinite(amount) || amount < 0 || source == null) throw new IllegalArgumentException("Invalid source damage");
            if (field_70128_L || amount == 0 || field_70170_p == null || field_70170_p.field_72995_K) return false;
            field_70170_p.command(new Attack(this,source,amount)); return true;
        }
        public boolean attackEntityFrom(Damage source,float amount) { return func_70097_a(source,amount); }
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
        public String displayName="";
        public String getDisplayName() { return displayName; }
        public Inventory field_71071_by = new Inventory();
        public final List<Chat> messages = new ArrayList<>();
        public void func_146105_b(Chat text) {
            if (messages.size() >= 128) throw new IllegalStateException("Source message budget exceeded");
            messages.add(Objects.requireNonNull(text));
        }
        public void addChatMessage(Chat text) { func_146105_b(text); }
        public void func_71008_a(Stack stack,int duration) { requestedUse=stack;requestedDuration=duration; }
        public void setItemInUse(Stack stack,int duration) { func_71008_a(stack,duration); }
    }
    public static class World {
        public boolean field_72995_K;
        public final List<Particle> particles=new ArrayList<>();
        public final List<Command> commands = new ArrayList<>();
        public java.util.function.BiFunction<Entity,Box,List<Entity>> query = (except,box)->List.of();
        public void command(Command command) {
            if (commands.size() >= 1024) throw new IllegalStateException("Source world operation budget exceeded");
            commands.add(Objects.requireNonNull(command));
        }
        public List<Entity> func_72839_b(Entity except,Box box) {
            List<Entity> result=Objects.requireNonNull(query.apply(except,box));
            if (result.size()>512) throw new IllegalStateException("Source entity query budget exceeded");
            return List.copyOf(result);
        }
        public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity except,Box box) { return func_72839_b(except,box); }
        public void func_72908_a(double x,double y,double z,String name,float volume,float pitch) {
            requireFinite(x,y,z,volume,pitch);
            command(new Sound(x,y,z,Objects.requireNonNull(name),volume,pitch));
        }
        public void playSoundEffect(double x,double y,double z,String name,float volume,float pitch) { func_72908_a(x,y,z,name,volume,pitch); }
        public boolean func_72838_d(Entity entity) {
            if (field_72995_K) return false;
            if (entity == null || entity.field_70170_p != this || !(entity instanceof Drop || entity instanceof Lightning))
                throw new IllegalArgumentException("Unsupported source entity construction");
            command(new Spawn(entity)); return true;
        }
        public boolean spawnEntityInWorld(Entity entity) { return func_72838_d(entity); }
        public boolean func_72942_c(Entity entity) { return func_72838_d(entity); }
        public boolean addWeatherEffect(Entity entity) { return func_72942_c(entity); }
        public void func_72869_a(String type,double x,double y,double z,double dx,double dy,double dz) {
            requireFinite(x,y,z,dx,dy,dz);
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
        public static final Potion field_76436_u=new Potion(19),field_82731_v=new Potion(20);
        public static final Potion nightVision=field_76439_r,waterBreathing=field_76427_o,poison=field_76436_u,wither=field_82731_v;
        public int func_76396_c() { return field_76415_H; }
        public int getId() { return field_76415_H; }
    }
    public static class Effect {
        public final int id,duration,amplifier;
        public Effect(int id,int duration,int amplifier) { this.id=id;this.duration=duration;this.amplifier=amplifier; }
        public int func_76459_b() { return duration; }
        public int getDuration() { return duration; }
    }
    public static class Damage {
        public Entity attacker;
        public static Damage func_76365_a(Player player) { Damage result=new Damage(); result.attacker=player; return result; }
        public static Damage causePlayerDamage(Player player) { return func_76365_a(player); }
        public Entity func_76364_f() { return attacker; }
        public Entity getEntity() { return attacker; }
    }
    public static class Event {
        public Entity entity;
        public Living entityLiving;
        public Stack itemStack;
        public List<String> toolTip;
        public Damage source=new Damage();
        public String name;
        public float volume,pitch;
        public float ammount,distance,damageMultiplier=1;
        private boolean canceled;
        public void setCanceled(boolean value) { canceled=value; }
        public boolean isCanceled() { return canceled; }
    }
    /** A source-owned operation is applied only after its callback completes successfully. */
    public sealed interface Command permits Attack, Durability, Spawn, Sound { }
    public record Attack(Entity target,Damage source,float amount) implements Command { }
    public record Durability(Stack stack,int amount,Living actor) implements Command { }
    public record Spawn(Entity entity) implements Command { }
    public record Sound(double x,double y,double z,String name,float volume,float pitch) implements Command { }
    public interface Chat { String text(); }
    public record Text(String text) implements Chat {
        public Text { Objects.requireNonNull(text); if(text.length()>16_384)throw new IllegalArgumentException("Source text too long"); }
    }
    public static class Inventory {
        public final Stack[] slots=new Stack[40];
        public java.util.function.IntFunction<Stack> lookup;
        public Stack func_70301_a(int slot) { return slot<0||slot>=slots.length?null:lookup==null?slots[slot]:lookup.apply(slot); }
        public Stack getStackInSlot(int slot) { return func_70301_a(slot); }
    }
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box { requireFinite(minX,minY,minZ,maxX,maxY,maxZ); }
        public Box func_72314_b(double x,double y,double z) {
            requireFinite(x,y,z);
            return new Box(minX-x,minY-y,minZ-z,maxX+x,maxY+y,maxZ+z);
        }
        public Box expand(double x,double y,double z) { return func_72314_b(x,y,z); }
    }
    public static class Vec {
        public final double field_72450_a,field_72448_b,field_72449_c;
        public Vec(double x,double y,double z) { requireFinite(x,y,z); field_72450_a=x;field_72448_b=y;field_72449_c=z; }
        public Vec func_72432_b() {
            double length=Math.sqrt(field_72450_a*field_72450_a+field_72448_b*field_72448_b+field_72449_c*field_72449_c);
            return length<1E-4?new Vec(0,0,0):new Vec(field_72450_a/length,field_72448_b/length,field_72449_c/length);
        }
        public Vec normalize() { return func_72432_b(); }
    }
    public static class Drop extends Entity {
        public final Stack stack;
        public int field_145804_b;
        public Drop(World world,double x,double y,double z,Stack stack) {
            requireFinite(x,y,z); field_70170_p=world;field_70165_t=x;field_70163_u=y;field_70161_v=z;this.stack=Objects.requireNonNull(stack);
        }
    }
    public static class Lightning extends Entity {
        public Lightning(World world,double x,double y,double z) {
            requireFinite(x,y,z);field_70170_p=world;field_70165_t=x;field_70163_u=y;field_70161_v=z;
        }
    }
    public interface Attribute { String func_111108_a(); }
    public static final class Attributes {
        public static final Attribute field_111264_e=()->"generic.attackDamage";
    }
    public static class Modifier {
        public final UUID id; public final String name; public final double amount; public final int operation;
        public Modifier(UUID id,String name,double amount,int operation) {
            this.id=Objects.requireNonNull(id);this.name=Objects.requireNonNull(name);this.amount=amount;this.operation=operation;
        }
        public double func_111164_d() { return amount; }
        public double getAmount() { return amount; }
    }
    private static void requireFinite(double... values) {
        for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite source value");
    }
    @FunctionalInterface public interface EventProgram { void run(Event event); }
}
