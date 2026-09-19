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

    private static volatile Map<Integer, Identifier> legacyToModern = Map.of();
    private static volatile Map<Identifier, Integer> modernToLegacy = Map.of();
    private static volatile Map<Integer, Integer> legacyToStateToken = Map.of();
    private static volatile List<Identifier> stateTokenToModern = List.of();

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
        Map<Integer, Integer> tokens = new LinkedHashMap<>();
        List<Identifier> identities = new ArrayList<>(sorted.size());
        for (Map.Entry<Integer, Identifier> entry : sorted) {
            tokens.put(entry.getKey(), identities.size());
            identities.add(entry.getValue());
        }

        legacyToModern = Collections.unmodifiableMap(new LinkedHashMap<>(byLegacyId));
        modernToLegacy = Collections.unmodifiableMap(new LinkedHashMap<>(byModernId));
        legacyToStateToken = Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
        stateTokenToModern = List.copyOf(identities);
        return legacyToModern.size();
    }

    public static synchronized void clear() {
        legacyToModern = Map.of();
        modernToLegacy = Map.of();
        legacyToStateToken = Map.of();
        stateTokenToModern = List.of();
    }

    public static int mappedBlockCount() {
        return legacyToModern.size();
    }

    public static int stateTokenCount() {
        return stateTokenToModern.size();
    }

    /** Converts a pre-flattening {@code blockId << 4 | metadata} value to a dense session token. */
    public static int stateToken(int legacyStateId) {
        if (legacyStateId < 0) return -1;
        Integer token = legacyToStateToken.get(legacyStateId >>> 4);
        return token == null ? -1 : token;
    }

    public static Identifier modernIdentityForStateToken(int token) {
        List<Identifier> identities = stateTokenToModern;
        return token >= 0 && token < identities.size() ? identities.get(token) : null;
    }

    public static Identifier legacyIdentity(int legacyId) {
        return legacyToModern.get(legacyId);
    }

    public static Integer legacyNumericId(Identifier modernId) {
        return modernToLegacy.get(modernId);
    }
}
