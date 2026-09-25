package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.behavior.ConvertedBehaviorItem;
import dev.yinghuang.legacyforgebridge.compat.LegacyVariantSnowballRuntimeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Random;

/**
 * Specialized generated item for a source-complete metadata-indexed legacy ItemSnowball family.
 *
 * <p>The dedicated use path replaces only the proven legacy right-click launch callback. Other
 * source-compiled behavior hooks remain available through ConvertedBehaviorItem.</p>
 */
public final class ConvertedLegacyVariantSnowballItem extends ConvertedBehaviorItem {
    private static final Random LEGACY_ITEM_RANDOM = new Random();

    private final LegacyVariantSnowballRuntimeRegistry.Rule rule;

    public ConvertedLegacyVariantSnowballItem(
            Properties properties,
            LegacyVariantSnowballRuntimeRegistry.Rule rule) {
        super(properties);
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    public LegacyVariantSnowballRuntimeRegistry.Rule rule() {
        return rule;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Preserve the source metadata before the legacy one-count consumption happens. The
        // projectile receives a one-count copy because an exact last-item throw would otherwise
        // turn the carried stack into EMPTY after the source-equivalent decrement.
        ItemStack carried = stack.copyWithCount(1);

        // 1.7 PlayerCapabilities#isCreativeMode was the only consumption bypass.
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // The source places both sound and spawn behind World#isRemote == false.
        if (level instanceof ServerLevel server) {
            float pitch = rule.launchPitchNumerator()
                    / (LEGACY_ITEM_RANDOM.nextFloat() * rule.launchPitchRandomScale()
                    + rule.launchPitchBase());

            server.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOWBALL_THROW,
                    SoundSource.NEUTRAL,
                    rule.launchVolume(),
                    pitch);

            Projectile.spawnProjectileFromRotation(
                    (spawnLevel, owner, projectileStack) ->
                            new ConvertedLegacyVariantSnowballProjectile(
                                    spawnLevel, owner, projectileStack, rule),
                    server,
                    carried,
                    player,
                    0.0F,
                    1.5F,
                    1.0F);
        }

        return InteractionResult.SUCCESS;
    }
}
