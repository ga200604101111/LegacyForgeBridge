package dev.yinghuang.legacyforgebridge.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Forge 1.7.10 FORGE channel, not FML or FML|HS. IDs come from ForgeRuntimeCodec upstream. */
public final class ForgeRuntimeCodec {
    public static final int DIMENSION_REGISTER = 1;
    public static final int FLUID_ID_MAP = 2;
    public static final int MAX_FLUIDS = 65_536;

    private ForgeRuntimeCodec() { }

    public sealed interface Message permits DimensionRegister, FluidIdMap { }
    public record DimensionRegister(int dimensionId, int providerId) implements Message { }
    public record FluidIdMap(Map<String, Integer> ids, List<String> defaults, boolean hasDefaults) implements Message {
        public FluidIdMap {
            ids = Collections.unmodifiableMap(new LinkedHashMap<>(ids));
            defaults = List.copyOf(defaults);
        }
    }

    public static Message decode(byte[] payload) {
        LegacyPayloadReader reader = new LegacyPayloadReader(payload);
        int discriminator = reader.unsignedByte();
        Message result = switch (discriminator) {
            case DIMENSION_REGISTER -> new DimensionRegister(reader.integer(), reader.integer());
            case FLUID_ID_MAP -> readFluids(reader);
            default -> throw new IllegalArgumentException("Unknown FORGE discriminator: " + discriminator);
        };
        reader.end();
        return result;
    }

    private static FluidIdMap readFluids(LegacyPayloadReader reader) {
        int count = reader.integer();
        // At least a string-length byte and an int per entry, before allocating any collection.
        if (count < 0 || count > MAX_FLUIDS || count > reader.remaining() / 5) {
            throw new IllegalArgumentException("Invalid FORGE fluid count: " + count);
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        var seenIds = new HashSet<Integer>();
        for (int i = 0; i < count; i++) {
            String name = reader.fmlString();
            int id = reader.integer();
            if (name.isBlank() || id < 0 || ids.putIfAbsent(name, id) != null || !seenIds.add(id)) {
                throw new IllegalArgumentException("Invalid or duplicate FORGE fluid identity");
            }
        }
        boolean hasDefaults = reader.remaining() != 0;
        List<String> defaults = new ArrayList<>();
        if (hasDefaults) {
            for (int i = 0; i < count; i++) {
                String name = reader.fmlString();
                if (name.isBlank()) throw new IllegalArgumentException("Empty FORGE default fluid identity");
                defaults.add(name);
            }
        }
        return new FluidIdMap(ids, defaults, hasDefaults);
    }
}
