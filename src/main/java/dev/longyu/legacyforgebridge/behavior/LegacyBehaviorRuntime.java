package dev.longyu.legacyforgebridge.behavior;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
        return LegacyTagAdapter.read(data.copyTag());
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
    public record UseOutcome(boolean handled, boolean sustained) { }

    public static UseOutcome use(ItemStack stack, Player player, InteractionHand hand) {
        var definition = definition(stack);
        if (definition == null || !definition.hooks().contains("use")) return new UseOutcome(false, false);
        Snapshot snapshot = new Snapshot(player.level());
        var actor = (LegacyBehaviorApi.Player) snapshot.living(player);
        var input = snapshot.stack(stack);
        LegacyBehaviorApi.Stack[] output = {input};
        ItemStack[] replacement = {stack};
        boolean completed = invoke(definition.mod(), "use", () -> {
            output[0] = definition.item().func_77659_a(input, snapshot.world, actor);
            if (output[0] != input) replacement[0] = snapshot.materialize(output[0]);
            return true;
        }, false);
        if (!completed) return new UseOutcome(false, false);
        snapshot.commit();
        // Inventory/NBT changes on a remote Forge server remain server-authoritative.
        if (!player.level().isClientSide() && output[0] != input) player.setItemInHand(hand, replacement[0]);
        return new UseOutcome(true, actor.requestedUse == input && actor.requestedDuration > 0
                && output[0] == input && !stack.isEmpty());
    }

    public static boolean startUse(ItemStack stack, Player player) {
        return use(stack, player, InteractionHand.MAIN_HAND).sustained();
    }

    public static void hit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        var definition = definition(stack);
        if (attacker.level().isClientSide() || definition == null || !definition.hooks().contains("hit")) return;
        Snapshot snapshot = new Snapshot(attacker.level());
        var victim = snapshot.living(target);
        var actor = snapshot.living(attacker);
        if (invoke(definition.mod(), "hit", () -> {
            definition.item().func_77644_a(snapshot.stack(stack), victim, actor);
            return true;
        }, false)) snapshot.commit();
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
        // Old InventoryPlayer.onUpdate visited the 36 main slots, not armor and offhand.
        for (int index = 0; index < Math.min(36, player.getInventory().getContainerSize()); index++) {
            tick(player, player.getInventory().getItem(index), index, false);
        }
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
            tick(player, player.getItemBySlot(slot), -1, true);
        }
    }
    private static void tick(Player player, ItemStack item, int index, boolean armor) {
        var definition = definition(item);
        String hook = armor ? "armorTick" : "tick";
        if (definition == null || !definition.hooks().contains(hook)) return;
        Snapshot snapshot = new Snapshot(player.level());
        var actor = (LegacyBehaviorApi.Player) snapshot.living(player);
        if (invoke(definition.mod(), hook, () -> {
            if (armor) definition.item().onArmorTick(snapshot.world, actor, snapshot.stack(item));
            else definition.item().func_77663_a(snapshot.stack(item), snapshot.world, actor, index,
                    index == player.getInventory().getSelectedSlot());
            return true;
        }, false)) snapshot.commit();
    }
    public record SoundOutcome(boolean canceled,String name,float volume,float pitch) { }
    public static SoundOutcome soundEvent(Entity entity,String name,float volume,float pitch) {
        if(entity==null||name==null||!Float.isFinite(volume)||!Float.isFinite(pitch))
            return new SoundOutcome(false,name,volume,pitch);
        return soundPrograms(()->new Snapshot(entity.level()).entity(entity),name,volume,pitch);
    }
    static SoundOutcome soundPrograms(Supplier<LegacyBehaviorApi.Entity> entitySource,String name,float volume,float pitch) {
        String current=name;
        for(var program:LegacyBehaviorRegistry.events("sound")) {
            var event=new LegacyBehaviorApi.Event();
            event.entity=Objects.requireNonNull(entitySource.get());
            if(event.entity instanceof LegacyBehaviorApi.Living living)event.entityLiving=living;
            event.name=current;event.volume=volume;event.pitch=pitch;
            if(invoke(program.mod(),"event/sound",()->{program.program().run(event);return true;},false)) {
                if(event.isCanceled())return new SoundOutcome(true,current,volume,pitch);
                if(event.name!=null)current=event.name;
            }
        }
        return new SoundOutcome(false,current,volume,pitch);
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
        var result = new LegacyBehaviorApi.Event();
        result.ammount = amount;
        result.distance = distance;
        result.damageMultiplier = multiplier;
        for (var program : LegacyBehaviorRegistry.events(kind)) {
            if (result.isCanceled()) break;
            Snapshot snapshot = new Snapshot(entity.level());
            var event = new LegacyBehaviorApi.Event();
            event.entityLiving = snapshot.living(entity);
            event.ammount = result.ammount;
            event.distance = result.distance;
            event.damageMultiplier = result.damageMultiplier;
            if (source != null) event.source.attacker = snapshot.entity(source.getEntity());
            if (invoke(program.mod(), "event/" + kind, () -> { program.program().run(event); return true; }, false)) {
                snapshot.commit();
                result = event;
            }
        }
        return result;
    }
    private static Holder<MobEffect> effect(int id) {
        return switch(id) {
            case 16 -> MobEffects.NIGHT_VISION;
            case 13 -> MobEffects.WATER_BREATHING;
            case 19 -> MobEffects.POISON;
            case 20 -> MobEffects.WITHER;
            default -> throw new IllegalArgumentException("Unsupported legacy potion ID: " + id);
        };
    }
    private static SoundEvent sound(String name) {
        return switch (name) {
            case "random.anvil_use" -> SoundEvents.ANVIL_USE;
            case "random.break" -> SoundEvents.ITEM_BREAK.value();
            case "random.explode" -> SoundEvents.GENERIC_EXPLODE.value();
            default -> throw new IllegalArgumentException("Unsupported legacy sound: " + name);
        };
    }

    private static final class Snapshot {
        private record Original(Vec3 velocity, float health) { }
        final Level level;
        final LegacyBehaviorApi.World world = new LegacyBehaviorApi.World();
        final IdentityHashMap<ItemStack, LegacyBehaviorApi.Stack> stacks = new IdentityHashMap<>();
        final IdentityHashMap<ItemStack, Map<String, Object>> originalTags = new IdentityHashMap<>();
        final IdentityHashMap<Entity, LegacyBehaviorApi.Entity> entities = new IdentityHashMap<>();
        final IdentityHashMap<Entity, Original> originals = new IdentityHashMap<>();

        Snapshot(Level level) {
            this.level = level;
            world.field_72995_K = level != null && level.isClientSide();
            world.query = (exclude, box) -> {
                if (level == null) return List.of();
                Entity nativeExclude = exclude == null ? null : (Entity) exclude.handle;
                var found = level.getEntities(nativeExclude,
                        new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                        candidate -> !candidate.isRemoved());
                if (found.size() > 512) throw new IllegalArgumentException("Source entity query budget exceeded");
                return found.stream().map(this::entity).toList();
            };
        }
        LegacyBehaviorApi.Stack stack(ItemStack nativeStack) {
            if (nativeStack == null || nativeStack.isEmpty()) return null;
            return stacks.computeIfAbsent(nativeStack, key -> {
                var definition = definition(key);
                var item = definition == null ? new LegacyBehaviorApi.Item() : definition.item();
                var view = new LegacyBehaviorApi.Stack(item, tag(key));
                view.field_77994_a = key.getCount();
                view.handle = key;
                originalTags.put(key, view.tag == null ? null : new LinkedHashMap<>(view.tag.values));
                return view;
            });
        }
        LegacyBehaviorApi.Entity entity(Entity nativeEntity) {
            if (nativeEntity == null) return null;
            if (nativeEntity instanceof LivingEntity living) return living(living);
            return entities.computeIfAbsent(nativeEntity, key -> {
                var view = new LegacyBehaviorApi.Entity(); fill(key, view); return view;
            });
        }
        LegacyBehaviorApi.Living living(LivingEntity nativeEntity) {
            var old = entities.get(nativeEntity);
            if (old != null) return (LegacyBehaviorApi.Living) old;
            LegacyBehaviorApi.Living view = nativeEntity instanceof Player
                    ? new LegacyBehaviorApi.Player() : new LegacyBehaviorApi.Living();
            entities.put(nativeEntity, view);
            fill(nativeEntity, view);
            view.health = nativeEntity.getHealth();
            view.equipment[0] = stack(nativeEntity.getMainHandItem());
            EquipmentSlot[] armor = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < armor.length; i++) view.equipment[i + 1] = stack(nativeEntity.getItemBySlot(armor[i]));
            if (nativeEntity instanceof Player player && view instanceof LegacyBehaviorApi.Player actor) {
                // Do not copy all 36 inventory tags for callbacks which only inspect equipment.
                actor.field_71071_by.lookup = index -> index < 36
                        ? stack(player.getInventory().getItem(index)) : view.equipment[index - 35];
            }
            for (int id : new int[]{13, 16, 19, 20}) {
                var active = nativeEntity.getEffect(effect(id));
                if (active != null) view.effects.put(id, new LegacyBehaviorApi.Effect(id, active.getDuration(), active.getAmplifier()));
            }
            return view;
        }
        void fill(Entity nativeEntity, LegacyBehaviorApi.Entity view) {
            view.handle = nativeEntity;
            view.field_70170_p = world;
            view.field_70128_L = !nativeEntity.isAlive();
            view.crouching = nativeEntity.isCrouching();
            view.burning = nativeEntity.isOnFire();
            view.field_70165_t = nativeEntity.getX();
            view.field_70163_u = nativeEntity.getY();
            view.field_70161_v = nativeEntity.getZ();
            Vec3 velocity = nativeEntity.getDeltaMovement();
            view.field_70159_w = velocity.x; view.field_70181_x = velocity.y; view.field_70179_y = velocity.z;
            view.field_70130_N = nativeEntity.getBbWidth(); view.field_70131_O = nativeEntity.getBbHeight();
            var look = nativeEntity.getLookAngle();
            view.look = new LegacyBehaviorApi.Vec(look.x, look.y, look.z);
            var box = nativeEntity.getBoundingBox();
            view.field_70121_D = new LegacyBehaviorApi.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            originals.put(nativeEntity, new Original(velocity, nativeEntity instanceof LivingEntity living ? living.getHealth() : 0F));
        }
        ItemStack materialize(LegacyBehaviorApi.Stack view) {
            if (view == null || view.field_77994_a <= 0) return ItemStack.EMPTY;
            ItemStack result;
            if (view.handle instanceof ItemStack existing) result = existing.copy();
            else {
                String identity = LegacyBehaviorRegistry.identity(view.item)
                        .orElseThrow(() -> new IllegalArgumentException("Unregistered source drop item"));
                Identifier id = Identifier.parse(identity);
                if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Missing native item " + identity);
                result = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            }
            if (view.field_77994_a > result.getMaxStackSize()) throw new IllegalArgumentException("Source stack exceeds native stack limit");
            result.setCount(view.field_77994_a);
            if (view.tag == null) result.remove(DataComponents.CUSTOM_DATA);
            else result.set(DataComponents.CUSTOM_DATA, CustomData.of(LegacyTagAdapter.write(view.tag)));
            return result;
        }
        void commit() {
            // Resolve all potentially invalid adapters before publishing any native effects.
            Map<LegacyBehaviorApi.Stack, CompoundTag> tags = new IdentityHashMap<>();
            for (var view : stacks.values()) if (view.tag != null) tags.put(view, LegacyTagAdapter.write(view.tag));
            for (var view : entities.values()) if (view instanceof LegacyBehaviorApi.Living living) {
                for (var applied : living.appliedEffects) effect(applied.id);
            }
            Map<LegacyBehaviorApi.Drop, ItemStack> drops = new IdentityHashMap<>();
            for (var command : world.commands) {
                if (command instanceof LegacyBehaviorApi.Sound request) sound(request.name());
                if (command instanceof LegacyBehaviorApi.Spawn spawn && spawn.entity() instanceof LegacyBehaviorApi.Drop drop)
                    drops.put(drop, materialize(drop.stack));
            }
            for (var pair : entities.entrySet()) {
                Entity nativeEntity = pair.getKey();
                var view = pair.getValue();
                if (nativeEntity.level() != level || nativeEntity.isRemoved()) continue;
                Original before = originals.get(nativeEntity);
                // The client predicts only its own movement, never another player's combat effects.
                if (!world.field_72995_K || (nativeEntity instanceof LivingEntity living && localPlayer.test(living))) {
                    Vec3 original = before.velocity();
                    Vec3 current = nativeEntity.getDeltaMovement();
                    double x = view.field_70159_w == original.x ? current.x : view.field_70159_w;
                    double y = view.field_70181_x == original.y ? current.y : view.field_70181_x;
                    double z = view.field_70179_y == original.z ? current.z : view.field_70179_y;
                    if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && (x != current.x || y != current.y || z != current.z)) {
                        nativeEntity.setDeltaMovement(x, y, z);
                        nativeEntity.hurtMarked = true;
                    }
                }
                if (!world.field_72995_K && nativeEntity instanceof LivingEntity living && view instanceof LegacyBehaviorApi.Living source) {
                    if (Float.isFinite(source.health) && source.health != before.health()) living.setHealth(source.health);
                    for (var applied : source.appliedEffects)
                        living.addEffect(new MobEffectInstance(effect(applied.id), applied.duration, applied.amplifier));
                }
                if (nativeEntity instanceof Player player && view instanceof LegacyBehaviorApi.Player source) {
                    for (var message : source.messages) player.displayClientMessage(LegacyText.formatted(message.text()), false);
                }
            }
            if (!world.field_72995_K) for (var pair : stacks.entrySet()) {
                ItemStack nativeStack = pair.getKey();
                var view = pair.getValue();
                Map<String, Object> before = originalTags.get(nativeStack);
                if (view.tag == null) {
                    if (before != null) nativeStack.remove(DataComponents.CUSTOM_DATA);
                } else if (before == null || !before.equals(view.tag.values)) {
                    nativeStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tags.get(view)));
                }
                if (view.field_77994_a >= 0 && view.field_77994_a <= nativeStack.getMaxStackSize()) nativeStack.setCount(view.field_77994_a);
            }
            if (level instanceof ServerLevel server) for (var command : world.commands) {
                if (command instanceof LegacyBehaviorApi.Attack attack) {
                    if (attack.target().handle instanceof Entity target && target.level() == server && target.isAlive()
                            && attack.source().attacker != null && attack.source().attacker.handle instanceof Player attacker
                            && attacker.level() == server) {
                        target.hurtServer(server, server.damageSources().playerAttack(attacker), attack.amount());
                    }
                } else if (command instanceof LegacyBehaviorApi.Durability damage) {
                    if (damage.stack().handle instanceof ItemStack item && damage.actor().handle instanceof LivingEntity actor
                            && actor.level() == server) {
                        EquipmentSlot slot = EquipmentSlot.MAINHAND;
                        for (EquipmentSlot possible : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) if (actor.getItemBySlot(possible) == item) { slot = possible; break; }
                        item.hurtAndBreak(damage.amount(), actor, slot);
                    }
                } else if (command instanceof LegacyBehaviorApi.Spawn spawn) {
                    var view = spawn.entity();
                    if (view instanceof LegacyBehaviorApi.Drop drop) {
                        ItemStack item = drops.get(drop);
                        if (!item.isEmpty()) {
                            ItemEntity entity = new ItemEntity(server, view.field_70165_t, view.field_70163_u, view.field_70161_v, item);
                            entity.setPickUpDelay(Math.max(0, drop.field_145804_b));
                            server.addFreshEntity(entity);
                        }
                    } else if (view instanceof LegacyBehaviorApi.Lightning) {
                        LightningBolt entity = new LightningBolt(EntityType.LIGHTNING_BOLT, server);
                        entity.setPos(view.field_70165_t, view.field_70163_u, view.field_70161_v);
                        server.addFreshEntity(entity);
                    }
                } else if (command instanceof LegacyBehaviorApi.Sound request) {
                    server.playSound(null, request.x(), request.y(), request.z(), sound(request.name()), SoundSource.PLAYERS,
                            request.volume(), request.pitch());
                }
            }
            // Legacy World.spawnParticle was a client visual request, not an extra broadcast.
            if (level != null && world.field_72995_K) for (var particle : world.particles) {
                var type = switch (particle.type()) {
                    case "flame" -> ParticleTypes.FLAME;
                    case "smoke" -> ParticleTypes.SMOKE;
                    default -> null;
                };
                if (type != null) level.addParticle(type, particle.x(), particle.y(), particle.z(), particle.dx(), particle.dy(), particle.dz());
                else if (WARNED.add("particle/" + particle.type())) LegacyForgeBridge.LOGGER.warn("Unsupported legacy particle: {}", particle.type());
            }
        }
    }
}
