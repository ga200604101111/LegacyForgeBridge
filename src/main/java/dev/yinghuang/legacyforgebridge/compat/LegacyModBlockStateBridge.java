package dev.yinghuang.legacyforgebridge.compat;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries Forge mod block identities through ViaVersion's numeric block-state mapping chain.
 *
 * <p>Forge 1.7.10 supplies a session-local numeric Block registry. Before flattening, each mod
 * block is replaced with a dense token in the unused tail of the target protocol's block-state
 * space. Every later MappingData boundary moves that token to the next protocol's unused tail.
 * At the native client boundary the token is resolved to the generated modern block's default
 * state. The server remains authoritative; metadata variants intentionally collapse to the
 * generated block state until a semantic converter proves a richer state mapping.</p>
 */
public final class LegacyModBlockStateBridge {
    public static final int NO_MAPPING = -1;
    private static final String NATIVE_TARGET_VERSION = "1.21.11";

    private static final Map<String, Integer> protocolBases = new ConcurrentHashMap<>();
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();

    private LegacyModBlockStateBridge() { }

    /** Entry boundary for the 1.12.2 -> 1.13 flattening rewrite. */
    public static int enterLegacyState(int legacyStateId, String targetVersion, int targetRegistrySize) {
        int token = LegacyModBlockRegistryMap.stateToken(legacyStateId);
        if (token < 0) return NO_MAPPING;
        int tokenCount = LegacyModBlockRegistryMap.stateTokenCount();
        if (!canReserve(targetRegistrySize, tokenCount)) {
            warnOnce(
                    "capacity:" + targetVersion,
                    "Cannot reserve generic legacy block-state carrier range for protocol {}: registrySize={}, tokens={}",
                    targetVersion,
                    targetRegistrySize,
                    tokenCount
            );
            return NO_MAPPING;
        }
        protocolBases.put(targetVersion, targetRegistrySize);
        return targetRegistrySize + token;
    }

    /** Boundary used by every later ViaVersion MappingDataBase block-state rewrite. */
    public static int carryAcrossMapping(
            int stateId,
            String sourceVersion,
            int sourceRegistrySize,
            String targetVersion,
            int targetRegistrySize
    ) {
        int tokenCount = LegacyModBlockRegistryMap.stateTokenCount();
        if (tokenCount == 0) return NO_MAPPING;

        int sourceBase = protocolBases.getOrDefault(sourceVersion, sourceRegistrySize);
        int token = decodeToken(stateId, sourceBase, tokenCount);
        if (token < 0) return NO_MAPPING;

        if (NATIVE_TARGET_VERSION.equals(targetVersion)) {
            return resolveNativeState(token);
        }

        if (!canReserve(targetRegistrySize, tokenCount)) {
            warnOnce(
                    "capacity:" + targetVersion,
                    "Cannot carry generic legacy block-state tokens into protocol {}: registrySize={}, tokens={}",
                    targetVersion,
                    targetRegistrySize,
                    tokenCount
            );
            return NO_MAPPING;
        }

        protocolBases.put(targetVersion, targetRegistrySize);
        return targetRegistrySize + token;
    }

    public static void reset() {
        protocolBases.clear();
        warned.clear();
    }

    static int decodeToken(int stateId, int registrySize, int tokenCount) {
        if (stateId < registrySize || tokenCount <= 0) return -1;
        long token = (long) stateId - registrySize;
        return token >= 0 && token < tokenCount ? (int) token : -1;
    }

    static boolean canReserve(int registrySize, int tokenCount) {
        if (registrySize <= 0 || tokenCount <= 0) return false;
        long highestCarrierId = (long) registrySize + tokenCount - 1L;
        int bits = registrySize <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(registrySize - 1);
        if (bits >= 31) return highestCarrierId <= Integer.MAX_VALUE;
        long globalPaletteCapacity = 1L << bits;
        return highestCarrierId < globalPaletteCapacity;
    }

    private static int resolveNativeState(int token) {
        Identifier modernId = LegacyModBlockRegistryMap.modernIdentityForStateToken(token);
        if (modernId != null && BuiltInRegistries.BLOCK.containsKey(modernId)) {
            Block block = BuiltInRegistries.BLOCK.getValue(modernId);
            int stateId = Block.BLOCK_STATE_REGISTRY.getId(block.defaultBlockState());
            if (stateId >= 0) return stateId;
        }

        warnOnce(
                "missing-native:" + token,
                "Converted modern block registry entry is unavailable for legacy state token {} (identity={}); using a visible fallback block",
                token,
                modernId
        );
        return Block.BLOCK_STATE_REGISTRY.getId(Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState());
    }

    private static void warnOnce(String key, String message, Object... arguments) {
        if (warned.add(key)) LegacyForgeBridge.LOGGER.warn(message, arguments);
    }
}
