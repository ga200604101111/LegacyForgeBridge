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

    private static final String LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    private static final String GHAST = "net/minecraft/entity/monster/EntityGhast";

    private static final Map<String,List<Entry>> BY_EXTERNAL_BASE = Map.of(
            LIVING_BASE, List.of(
                    new Entry(6, "float", 1.0F, LIVING_BASE),
                    new Entry(7, "int", 0, LIVING_BASE),
                    new Entry(8, "byte", (byte)0, LIVING_BASE),
                    new Entry(9, "byte", (byte)0, LIVING_BASE)
            ),
            GHAST, List.of(new Entry(16, "byte", (byte)0, GHAST))
    );

    private LegacyVanillaEntityDataWatcher1710() { }

    public static List<Entry> inheritedForExternalBase(String externalBase) {
        if (externalBase == null || externalBase.isBlank()) return List.of();
        return BY_EXTERNAL_BASE.getOrDefault(externalBase, List.of());
    }

    public static boolean matchesPlatformAccess(String owner,int index,String valueKind) {
        if (owner == null || valueKind == null) return false;
        for (Entry entry : BY_EXTERNAL_BASE.getOrDefault(owner, List.of())) {
            if (entry.index() == index && entry.valueKind().equals(valueKind)) return true;
        }
        return false;
    }
}
