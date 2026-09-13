package dev.longyu.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure session-local mapping extracted from Forge 1.7.10 FML ModIdData.
 *
 * <p>This class deliberately has no ViaVersion/NBT dependencies so the FML handshake state machine
 * can own registry identity without loading protocol-rewriter implementation classes.</p>
 */
public final class LegacyModItemRegistryMap {
    static final char ITEM_REGISTRY_PREFIX = '\u0002';

    private static volatile Map<Integer, Identifier> legacyToModern = Map.of();
    private static volatile Map<Identifier, Integer> modernToLegacy = Map.of();

    private LegacyModItemRegistryMap() {
    }

    /** Installs only non-vanilla ITEM identities from one FML registry synchronization. */
    public static synchronized int install(Map<String, Integer> registryIds) {
        Map<Integer, Identifier> byLegacyId = new LinkedHashMap<>();
        Map<Identifier, Integer> byModernId = new LinkedHashMap<>();

        if (registryIds != null) {
            for (Map.Entry<String, Integer> entry : registryIds.entrySet()) {
                String rawIdentity = entry.getKey();
                Integer legacyId = entry.getValue();
                if (rawIdentity == null
                        || rawIdentity.length() < 2
                        || rawIdentity.charAt(0) != ITEM_REGISTRY_PREFIX
                        || legacyId == null
                        || legacyId < 0) {
                    continue;
                }

                Identifier modernId;
                try {
                    modernId = Identifier.parse(rawIdentity.substring(1));
                } catch (RuntimeException invalidIdentifier) {
                    continue;
                }

                // Vanilla identities remain ViaVersion-owned. LFB only fills the Forge-mod gap.
                if ("minecraft".equals(modernId.getNamespace())) {
                    continue;
                }

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

    public static int mappedItemCount() {
        return legacyToModern.size();
    }

    static Identifier legacyIdentity(int legacyId) {
        return legacyToModern.get(legacyId);
    }

    static Integer legacyNumericId(Identifier modernId) {
        return modernToLegacy.get(modernId);
    }
}
