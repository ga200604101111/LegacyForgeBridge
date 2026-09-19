package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Session-local Forge 1.7.10 numeric Block ID mapping from FML ModIdData. */
public final class LegacyModBlockRegistryMap {
    static final char BLOCK_REGISTRY_PREFIX = '\u0001';

    private static volatile Map<Integer, Identifier> legacyToModern = Map.of();
    private static volatile Map<Identifier, Integer> modernToLegacy = Map.of();

    private LegacyModBlockRegistryMap() { }

    /** Installs only non-vanilla BLOCK identities from one FML registry synchronization. */
    public static synchronized int install(Map<String, Integer> registryIds) {
        Map<Integer, Identifier> byLegacyId = new LinkedHashMap<>();
        Map<Identifier, Integer> byModernId = new LinkedHashMap<>();
        if (registryIds != null) {
            for (Map.Entry<String, Integer> entry : registryIds.entrySet()) {
                String rawIdentity = entry.getKey();
                Integer legacyId = entry.getValue();
                if (rawIdentity == null
                        || rawIdentity.length() < 2
                        || rawIdentity.charAt(0) != BLOCK_REGISTRY_PREFIX
                        || legacyId == null
                        || legacyId < 0) continue;
                Identifier modernId = LegacyRegistryIdentity.normalize(rawIdentity.substring(1));
                if (modernId == null || "minecraft".equals(modernId.getNamespace())) continue;
                byLegacyId.put(legacyId, modernId);
                byModernId.put(modernId, legacyId);
            }
        }
        legacyToModern = Collections.unmodifiableMap(new LinkedHashMap<>(byLegacyId));
        modernToLegacy = Collections.unmodifiableMap(new LinkedHashMap<>(byModernId));
        return legacyToModern.size();
    }

    public static synchronized void clear() {
        legacyToModern = Map.of();
        modernToLegacy = Map.of();
    }

    public static int mappedBlockCount() {
        return legacyToModern.size();
    }

    public static Identifier legacyIdentity(int legacyId) {
        return legacyToModern.get(legacyId);
    }

    public static Integer legacyNumericId(Identifier modernId) {
        return modernToLegacy.get(modernId);
    }
}
