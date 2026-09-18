package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyVariantSnowballRuntimeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Dormant modern projectile shell for a source-proven legacy metadata-indexed EntitySnowball.
 *
 * EntityType creation is wired in the current runtime slice, but generated items do not launch this
 * entity until the dedicated item/launch slice is admitted. Impact semantics are likewise closed.
 */
public final class ConvertedLegacyVariantSnowballProjectile extends ThrowableItemProjectile {
    private final LegacyVariantSnowballRuntimeRegistry.Rule rule;

    public ConvertedLegacyVariantSnowballProjectile(
            EntityType<? extends ThrowableItemProjectile> type,
            Level level,
            LegacyVariantSnowballRuntimeRegistry.Rule rule) {
        super(type, level);
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    public LegacyVariantSnowballRuntimeRegistry.Rule rule() {
        return rule;
    }

    @Override
    protected Item getDefaultItem() {
        if (BuiltInRegistries.ITEM.containsKey(rule.id())) {
            return BuiltInRegistries.ITEM.getValue(rule.id());
        }
        return Items.SNOWBALL;
    }
}
