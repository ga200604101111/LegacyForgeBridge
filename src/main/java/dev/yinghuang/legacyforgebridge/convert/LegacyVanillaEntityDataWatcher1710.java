package dev.yinghuang.legacyforgebridge.convert;

import java.util.List;
import java.util.Map;

/**
 * Pinned vanilla Minecraft 1.7.10 DataWatcher definitions needed when a source mod class inherits
 * platform-owned watcher state that is not declared in the mod JAR.
 *
 * <p>This table is intentionally narrow. Entries are admitted only for an exact external vanilla
 * base class and are merged with source-owned definitions by the normal duplicate/type checks.</p>
 */
public final class LegacyVanillaEntityDataWatcher1710 {
    public record Entry(int index,String valueKind,Object defaultValue,String declaredBy) { }

    private static final String GHAST = "net/minecraft/entity/monster/EntityGhast";

    private static final Map<String,List<Entry>> BY_EXTERNAL_BASE = Map.of(
            GHAST, List.of(new Entry(16, "byte", (byte)0, GHAST))
    );

    private LegacyVanillaEntityDataWatcher1710() { }

    public static List<Entry> inheritedForExternalBase(String externalBase) {
        if (externalBase == null || externalBase.isBlank()) return List.of();
        return BY_EXTERNAL_BASE.getOrDefault(externalBase, List.of());
    }
}
