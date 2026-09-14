package dev.yinghuang.legacyforgebridge.runtime;

import dev.yinghuang.legacyforgebridge.network.FmlRuntimeCodec;
import dev.yinghuang.legacyforgebridge.network.LegacyEntitySpawnCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Internal SPI for installed converted mods. Register under Fabric entrypoint legacyforgebridge-runtime. */
public interface LegacyRuntimeProvider {
    void register(Registration registration);

    record EntityContext(Minecraft client, ClientLevel level, LegacyEntitySpawnCodec.Spawn message,
                         LegacyRuntimeState.Snapshot session) { }
    record GuiContext(Minecraft client, ClientLevel level, FmlRuntimeCodec.OpenGui message,
                      LegacyRuntimeState.Snapshot session) { }

    interface EntityAdapter {
        /** Construct, but do not insert into the world. The bridge assigns network ID/position next. */
        Entity create(EntityContext context);
        /** Translate watcher identities, resolve thrower semantics and consume mod-specific spawn data. */
        void applyLegacyState(Entity entity, EntityContext context);
    }
    interface GuiAdapter {
        /** Construct a screen whose menu.containerId is the supplied legacy window ID. */
        AbstractContainerScreen<?> create(GuiContext context);
    }

    final class Registration {
        final Map<String, EntityAdapter> entities = new LinkedHashMap<>();
        final Map<String, GuiAdapter> guis = new LinkedHashMap<>();
        public void entity(String target, EntityAdapter adapter) { add(entities, target, adapter); }
        public void gui(String target, GuiAdapter adapter) { add(guis, target, adapter); }
        private static <T> void add(Map<String, T> entries, String key, T adapter) {
            Objects.requireNonNull(key, "target");
            Objects.requireNonNull(adapter, "adapter");
            if (entries.putIfAbsent(key, adapter) != null) throw new IllegalArgumentException("Duplicate runtime adapter: " + key);
        }
    }
}
