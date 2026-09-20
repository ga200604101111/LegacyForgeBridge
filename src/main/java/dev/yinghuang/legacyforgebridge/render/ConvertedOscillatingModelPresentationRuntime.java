package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyOscillatingModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyOscillatingModelBlockEntity;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyOscillatingModelRenderer;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacyOscillatingModelBootstrap;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only BER registration for fully proven oscillating decorative blocks. */
public final class ConvertedOscillatingModelPresentationRuntime {
    private static final Set<String> INITIALIZED_MODS = ConcurrentHashMap.newKeySet();
    private ConvertedOscillatingModelPresentationRuntime() { }

    public static void initializeMod(String modId) {
        if (modId == null || modId.isBlank() || !INITIALIZED_MODS.add(modId)) return;
        int renderers = 0;
        try {
            for (var rule : LegacyOscillatingModelBlockRegistry.rules(modId)) {
                BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> type = LegacyOscillatingModelBootstrap.type(rule.id());
                if (type == null) {
                    LegacyForgeBridge.LOGGER.error("Oscillating presentation {} has no BlockEntityType", rule.id());
                    continue;
                }
                BlockEntityRenderers.register(type, context -> new ConvertedLegacyOscillatingModelRenderer(context, rule));
                renderers++;
            }
        } catch (Exception exception) {
            INITIALIZED_MODS.remove(modId);
            LegacyForgeBridge.LOGGER.error("Failed to initialize oscillating model presentation for {}", modId, exception);
            return;
        }
        if (renderers > 0) LegacyForgeBridge.LOGGER.info(
                "Initialized converted oscillating model presentation: mod={}, worldRenderers={}", modId, renderers);
    }
}
