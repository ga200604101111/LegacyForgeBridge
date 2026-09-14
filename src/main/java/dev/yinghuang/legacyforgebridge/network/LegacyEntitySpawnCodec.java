package dev.yinghuang.legacyforgebridge.network;

import java.util.ArrayList;
import java.util.List;

/** Full FML EntitySpawnMessage framing, with legacy identity retained for a converted-mod adapter. */
public final class LegacyEntitySpawnCodec {
    private LegacyEntitySpawnCodec() { }

    public record BlockPosition(int x, int y, int z) { }

    /** NBT is the original length-delimited gzip bytes, not modern NBT or a modern raw item ID. */
    public record LegacyItem(int legacyId, int count, int damage, byte[] compressedNbt) {
        public LegacyItem { compressedNbt = compressedNbt == null ? null : compressedNbt.clone(); }
        @Override public byte[] compressedNbt() { return compressedNbt == null ? null : compressedNbt.clone(); }
    }

    /** value is Byte, Short, Integer, Float, String, nullable LegacyItem, or BlockPosition. */
    public record Watcher(int index, int type, Object value) { }

    public record Spawn(FmlRuntimeCodec.EntitySpawnHeader header, List<Watcher> watchers,
                        int throwerId, double velocityX, double velocityY, double velocityZ,
                        byte[] additionalData) {
        public Spawn {
            watchers = List.copyOf(watchers);
            additionalData = additionalData.clone();
        }
        @Override public byte[] additionalData() { return additionalData.clone(); }
    }

    public static Spawn decode(byte[] payload) {
        LegacyPayloadReader reader = new LegacyPayloadReader(payload);
        if (reader.unsignedByte() != FmlRuntimeCodec.ENTITY_SPAWN) {
            throw new IllegalArgumentException("Expected FML EntitySpawnMessage");
        }
        int entityId = reader.integer();
        String modId = reader.fmlString();
        if (!modId.matches("[A-Za-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid entity mod identity");
        int typeId = reader.integer();
        int rawX = reader.integer(), rawY = reader.integer(), rawZ = reader.integer();
        float yaw = reader.signedByte() * 360.0F / 256.0F;
        float pitch = reader.signedByte() * 360.0F / 256.0F;
        float headYaw = reader.signedByte() * 360.0F / 256.0F;
        var header = new FmlRuntimeCodec.EntitySpawnHeader(entityId, modId, typeId, rawX, rawY, rawZ,
                rawX / 32.0D, rawY / 32.0D, rawZ / 32.0D, yaw, pitch, headYaw, reader.remaining());
        List<Watcher> watchers = new ArrayList<>();
        boolean[] occupied = new boolean[32];
        while (true) {
            int encoded = reader.unsignedByte();
            if (encoded == 127) break;
            int index = encoded & 31;
            int type = encoded >>> 5;
            if (occupied[index]) throw new IllegalArgumentException("Duplicate DataWatcher index: " + index);
            occupied[index] = true;
            Object value = switch (type) {
                case 0 -> reader.signedByte();
                case 1 -> reader.signedShort();
                case 2 -> reader.integer();
                case 3 -> reader.floating();
                case 4 -> reader.watcherString();
                case 5 -> readItem(reader);
                case 6 -> new BlockPosition(reader.integer(), reader.integer(), reader.integer());
                default -> throw new IllegalArgumentException("Unknown 1.7.10 DataWatcher type: " + type);
            };
            watchers.add(new Watcher(index, type, value));
        }
        int throwerId = reader.integer();
        double x = 0, y = 0, z = 0;
        if (throwerId != 0) {
            // FML uses three INTS here, unlike the vanilla spawn packet's velocity shorts.
            x = reader.integer() / 8000.0D;
            y = reader.integer() / 8000.0D;
            z = reader.integer() / 8000.0D;
        }
        return new Spawn(header, watchers, throwerId, x, y, z, reader.bytes(reader.remaining()));
    }

    private static LegacyItem readItem(LegacyPayloadReader reader) {
        int id = reader.signedShort();
        if (id < 0) return null;
        int count = reader.signedByte();
        int damage = reader.signedShort();
        int nbtLength = reader.signedShort();
        byte[] nbt = nbtLength < 0 ? null : reader.bytes(nbtLength);
        return new LegacyItem(id, count, damage, nbt);
    }
}
