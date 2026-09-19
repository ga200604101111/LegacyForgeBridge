package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session-local Forge 1.7.10 numeric Block ID mapping from FML ModIdData. */
public final class LegacyModBlockRegistryMap {
    static final char BLOCK_REGISTRY_PREFIX = '\u0001';
    private static final int LEGACY_METADATA_VALUES = 16;

    private static volatile Map<Integer, Identifier> legacyToModern = Map.of();
    private static volatile Map<Identifier, Integer> modernToLegacy = Map.of();
    private static volatile Map<Integer, Integer> legacyToStateTokenBase = Map.of();
    private static volatile List<StateIdentity> stateTokenIdentities = List.of();

    private LegacyModBlockRegistryMap() { }

    public static int install(Map<String, Integer> registryIds) {
        return install(registryIds, Map.of());
    }

    /** Installs only non-vanilla BLOCK identities from one FML registry synchronization. */
    public static synchronized int install(
            Map<String, Integer> registryIds,
            Map<String, Identifier> conversionAliases
    ) {
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

                String unprefixed = rawIdentity.substring(1);
                Identifier legacyIdentity = LegacyRegistryIdentity.normalize(unprefixed);
                if (legacyIdentity == null || "minecraft".equals(legacyIdentity.getNamespace())) continue;
                Identifier modernId = LegacyRegistryIdentity.resolve(unprefixed, conversionAliases);
                if (modernId == null) continue;

                byLegacyId.put(legacyId, modernId);
                byModernId.put(modernId, legacyId);
            }
        }

        List<Map.Entry<Integer, Identifier>> sorted = new ArrayList<>(byLegacyId.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getKey));
        Map<Integer, Integer> tokenBases = new LinkedHashMap<>();
        List<StateIdentity> identities = new ArrayList<>(sorted.size() * LEGACY_METADATA_VALUES);
        for (Map.Entry<Integer, Identifier> entry : sorted) {
            tokenBases.put(entry.getKey(), identities.size());
            for (int metadata = 0; metadata < LEGACY_METADATA_VALUES; metadata++) {
                identities.add(new StateIdentity(entry.getValue(), metadata));
            }
        }

        legacyToModern = Collections.unmodifiableMap(new LinkedHashMap<>(byLegacyId));
        modernToLegacy = Collections.unmodifiableMap(new LinkedHashMap<>(byModernId));
        legacyToStateTokenBase = Collections.unmodifiableMap(new LinkedHashMap<>(tokenBases));
        stateTokenIdentities = List.copyOf(identities);
        return legacyToModern.size();
    }

    public static synchronized void clear() {
        legacyToModern = Map.of();
        modernToLegacy = Map.of();
        legacyToStateTokenBase = Map.of();
        stateTokenIdentities = List.of();
    }

    public static int mappedBlockCount() { return legacyToModern.size(); }
    public static int stateTokenCount() { return stateTokenIdentities.size(); }

    /** Converts a pre-flattening {@code blockId << 4 | metadata} value to a dense session token. */
    public static int stateToken(int legacyStateId) {
        if (legacyStateId < 0) return -1;
        Integer base = legacyToStateTokenBase.get(legacyStateId >>> 4);
        return base == null ? -1 : base + (legacyStateId & 0xF);
    }

    public static Identifier modernIdentityForStateToken(int token) {
        StateIdentity identity = stateIdentity(token);
        return identity == null ? null : identity.modernId();
    }

    public static int legacyMetadataForStateToken(int token) {
        StateIdentity identity = stateIdentity(token);
        return identity == null ? -1 : identity.metadata();
    }

    public static Identifier legacyIdentity(int legacyId) { return legacyToModern.get(legacyId); }
    public static Integer legacyNumericId(Identifier modernId) { return modernToLegacy.get(modernId); }

    private static StateIdentity stateIdentity(int token) {
        List<StateIdentity> identities = stateTokenIdentities;
        return token >= 0 && token < identities.size() ? identities.get(token) : null;
    }

    private record StateIdentity(Identifier modernId, int metadata) { }
}
