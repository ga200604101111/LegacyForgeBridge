package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.compat.LegacyVariantSnowballRuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Objects;

/**
 * Modern projectile runtime for a source-proven legacy metadata-indexed EntitySnowball family.
 *
 * <p>The carried ItemStack is the authoritative selector carrier. Its LFB-owned legacy metadata
 * component is synchronized by the vanilla ThrowableItemProjectile stack channel. Impact behavior
 * is executed only on the logical server and is selected from the normalized source-proof rule.</p>
 */
public final class ConvertedLegacyVariantSnowballProjectile extends ThrowableItemProjectile {
    private static final int LEGACY_WORLD_MIN_Y = 0;
    private static final int LEGACY_WORLD_MAX_Y_EXCLUSIVE = 256;

    private final LegacyVariantSnowballRuntimeRegistry.Rule rule;

    public ConvertedLegacyVariantSnowballProjectile(
            EntityType<? extends ThrowableItemProjectile> type,
            Level level,
            LegacyVariantSnowballRuntimeRegistry.Rule rule) {
        super(type, level);
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    public ConvertedLegacyVariantSnowballProjectile(
            Level level,
            LivingEntity owner,
            ItemStack stack,
            LegacyVariantSnowballRuntimeRegistry.Rule rule) {
        super(requireType(rule), owner, level, stack);
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    public LegacyVariantSnowballRuntimeRegistry.Rule rule() {
        return rule;
    }

    public int legacyMetadata() {
        return LegacyStackComponents.get(getItem());
    }

    public LegacyVariantSnowballRuntimeRegistry.Variant variant() {
        return rule.variant(legacyMetadata());
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        LegacyVariantSnowballRuntimeRegistry.Variant variant = variant();
        if (variant == null) {
            // The 1.7 source kills the projectile immediately when the metadata selector map
            // returns null. Never invent a default selector.
            discard();
            return;
        }

        if (!(level() instanceof ServerLevel server)) return;

        if (hitResult instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            target.hurtServer(
                    server,
                    damageSources().thrown(this, getOwner()),
                    variant.baseDamage());

            // Source selector-specific effects are guarded by EntityLiving, not
            // EntityLivingBase; the modern counterpart is Mob, intentionally excluding players.
            if (target instanceof Mob mob) {
                switch (variant.effect()) {
                    case NONE -> { }
                    case POTION -> mob.addEffect(new MobEffectInstance(
                            effect(variant.potion()), variant.duration(), variant.amplifier()));
                    case RANDOM_TELEPORT -> randomTeleport(server, mob, variant);
                }
            }
        }

        // The source always emits exactly eight snowballpoof particles for every non-null selector.
        server.sendParticles(
                ParticleTypes.ITEM_SNOWBALL,
                getX(), getY(), getZ(),
                8,
                0.0D, 0.0D, 0.0D,
                0.0D);

        // The source removes the projectile only on the authoritative server path.
        discard();
    }

    private boolean randomTeleport(
            ServerLevel server,
            Mob target,
            LegacyVariantSnowballRuntimeRegistry.Variant variant) {
        double oldX = target.getX();
        double oldY = target.getY();
        double oldZ = target.getZ();

        double candidateX = oldX
                + (getRandom().nextDouble() - 0.5D)
                * (variant.horizontalRandomRadius() * 2.0D);
        double candidateY = oldY
                + getRandom().nextInt(variant.verticalRandomBound())
                + variant.verticalRandomOffset();
        double candidateZ = oldZ
                + (getRandom().nextDouble() - 0.5D)
                * (variant.horizontalRandomRadius() * 2.0D);

        int blockX = Mth.floor(candidateX);
        int blockY = Mth.floor(candidateY);
        int blockZ = Mth.floor(candidateZ);

        // Forge/Minecraft 1.7.10 World#blockExists rejects Y outside [0,256) before asking the
        // chunk provider. Preserve that source-world bound instead of silently using modern
        // negative build heights.
        if (blockY < LEGACY_WORLD_MIN_Y
                || blockY >= LEGACY_WORLD_MAX_Y_EXCLUSIVE
                || !server.hasChunkAt(new BlockPos(blockX, blockY, blockZ))) {
            return false;
        }

        boolean solidGround = false;
        while (!solidGround && blockY > LEGACY_WORLD_MIN_Y) {
            BlockState below = server.getBlockState(new BlockPos(blockX, blockY - 1, blockZ));
            if (!below.isAir() && below.blocksMotion()) {
                solidGround = true;
            } else {
                candidateY -= 1.0D;
                blockY--;
            }
        }
        if (!solidGround) return false;

        target.setPos(candidateX, candidateY, candidateZ);
        AABB box = target.getBoundingBox();

        // 1.7 World#getCollidingBoundingBoxes(thisProjectile, targetBox) combines block and
        // entity collisions while excluding this projectile. Avoid modern world-border collision
        // policy here because it was not part of that source call.
        boolean colliding = server.getBlockCollisions(this, box).iterator().hasNext()
                || !server.getEntityCollisions(this, box).isEmpty();
        boolean liquid = server.containsAnyLiquid(box);
        if (colliding || liquid) {
            target.setPos(oldX, oldY, oldZ);
            return false;
        }

        portalPresentation(server, target, oldX, oldY, oldZ, variant.portalParticleCount());
        return true;
    }

    private void portalPresentation(
            ServerLevel server,
            Mob target,
            double oldX,
            double oldY,
            double oldZ,
            int particleCount) {
        for (int index = 0; index < particleCount; index++) {
            double ratio = (double) index / (double) (particleCount - 1);
            float velocityX = (getRandom().nextFloat() - 0.5F) * 0.2F;
            float velocityY = (getRandom().nextFloat() - 0.5F) * 0.2F;
            float velocityZ = (getRandom().nextFloat() - 0.5F) * 0.2F;

            double particleX = oldX
                    + (target.getX() - oldX) * ratio
                    + (getRandom().nextDouble() - 0.5D) * target.getBbWidth() * 2.0D;
            double particleY = oldY
                    + (target.getY() - oldY) * ratio
                    + getRandom().nextDouble() * target.getBbHeight();
            double particleZ = oldZ
                    + (target.getZ() - oldZ) * ratio
                    + (getRandom().nextDouble() - 0.5D) * target.getBbWidth() * 2.0D;

            // A zero-count particle packet uses the delta fields as the single particle's exact
            // velocity, matching the source's per-particle spawnParticle call.
            server.sendParticles(
                    ParticleTypes.PORTAL,
                    particleX, particleY, particleZ,
                    0,
                    velocityX, velocityY, velocityZ,
                    1.0D);
        }

        server.playSound(
                null,
                oldX, oldY, oldZ,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.NEUTRAL,
                1.0F, 1.0F);
        server.playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.NEUTRAL,
                1.0F, 1.0F);
    }

    private static Holder<MobEffect> effect(String name) {
        return switch (name) {
            case "poison" -> MobEffects.POISON;
            case "confusion" -> MobEffects.NAUSEA;
            case "regeneration" -> MobEffects.REGENERATION;
            default -> throw new IllegalArgumentException(
                    "Unsupported normalized variant-snowball potion " + name);
        };
    }

    private static EntityType<ConvertedLegacyVariantSnowballProjectile> requireType(
            LegacyVariantSnowballRuntimeRegistry.Rule rule) {
        Objects.requireNonNull(rule, "rule");
        EntityType<ConvertedLegacyVariantSnowballProjectile> type =
                LegacyVariantSnowballRuntimeRegistry.projectileType(rule.projectileId());
        if (type == null) {
            throw new IllegalStateException(
                    "Missing registered converted variant-snowball EntityType for "
                            + rule.projectileId());
        }
        return type;
    }

    @Override
    protected Item getDefaultItem() {
        if (BuiltInRegistries.ITEM.containsKey(rule.id())) {
            return BuiltInRegistries.ITEM.getValue(rule.id());
        }
        return Items.SNOWBALL;
    }
}
