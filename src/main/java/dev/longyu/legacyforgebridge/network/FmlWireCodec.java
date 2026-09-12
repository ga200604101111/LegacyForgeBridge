package dev.longyu.legacyforgebridge.network;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Minimal Forge/FML 1.7.10 handshake wire codec.
 *
 * <p>The first byte of every FML|HS payload is the FML message discriminator. The remaining
 * bytes use the legacy FML ByteBufUtils encodings.</p>
 */
public final class FmlWireCodec {
    public static final int FML_PROTOCOL = 2;

    public static final int SERVER_HELLO = 0;
    public static final int CLIENT_HELLO = 1;
    public static final int MOD_LIST = 2;
    public static final int MOD_ID_DATA = 3;
    public static final int HANDSHAKE_ACK = 0xFF;
    public static final int HANDSHAKE_RESET = 0xFE;

    private FmlWireCodec() {
    }

    public static int discriminator(byte[] payload) {
        require(payload != null && payload.length > 0, "Empty FML|HS payload");
        return payload[0] & 0xFF;
    }

    public static ServerHello parseServerHello(byte[] payload) {
        Reader reader = readerFor(payload, SERVER_HELLO);
        int protocol = reader.readUnsignedByte();
        Integer overrideDimension = null;
        if (protocol > 1 && reader.remaining() >= 4) {
            overrideDimension = reader.readInt();
        }
        return new ServerHello(protocol, overrideDimension, reader.remaining());
    }

    public static byte[] encodeClientHello() {
        return new byte[]{(byte) CLIENT_HELLO, (byte) FML_PROTOCOL};
    }

    public static byte[] encodeModList(Map<String, String> mods) {
        Map<String, String> safeMods = mods == null ? Map.of() : mods;
        Writer writer = new Writer();
        writer.writeByte(MOD_LIST);
        writer.writeVarInt(safeMods.size(), 2);
        for (Map.Entry<String, String> entry : safeMods.entrySet()) {
            writer.writeUtf8(entry.getKey());
            writer.writeUtf8(entry.getValue());
        }
        return writer.toByteArray();
    }

    public static Map<String, String> parseModList(byte[] payload) {
        Reader reader = readerFor(payload, MOD_LIST);
        int count = reader.readVarInt(2);
        Map<String, String> mods = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            mods.put(reader.readUtf8(), reader.readUtf8());
        }
        return Collections.unmodifiableMap(mods);
    }

    public static ModIdData parseModIdData(byte[] payload) {
        Reader reader = readerFor(payload, MOD_ID_DATA);
        int count = reader.readVarInt(3);
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ids.put(reader.readUtf8(), reader.readVarInt(3));
        }

        Set<String> blockSubstitutions = new LinkedHashSet<>();
        Set<String> itemSubstitutions = new LinkedHashSet<>();

        if (reader.remaining() > 0) {
            int blockCount = reader.readVarInt(3);
            for (int i = 0; i < blockCount; i++) {
                blockSubstitutions.add(reader.readUtf8());
            }
        }

        if (reader.remaining() > 0) {
            int itemCount = reader.readVarInt(3);
            for (int i = 0; i < itemCount; i++) {
                itemSubstitutions.add(reader.readUtf8());
            }
        }

        return new ModIdData(
                Collections.unmodifiableMap(ids),
                Collections.unmodifiableSet(blockSubstitutions),
                Collections.unmodifiableSet(itemSubstitutions),
                reader.remaining()
        );
    }

    public static byte[] encodeAck(int phase) {
        if (phase < 0 || phase > 255) {
            throw new IllegalArgumentException("Invalid FML handshake phase: " + phase);
        }
        return new byte[]{(byte) HANDSHAKE_ACK, (byte) phase};
    }

    public static int parseAck(byte[] payload) {
        Reader reader = readerFor(payload, HANDSHAKE_ACK);
        return reader.readUnsignedByte();
    }

    public static String discriminatorName(int discriminator) {
        return switch (discriminator & 0xFF) {
            case SERVER_HELLO -> "ServerHello";
            case CLIENT_HELLO -> "ClientHello";
            case MOD_LIST -> "ModList";
            case MOD_ID_DATA -> "ModIdData";
            case HANDSHAKE_ACK -> "HandshakeAck";
            case HANDSHAKE_RESET -> "HandshakeReset";
            default -> "Unknown(0x%02X)".formatted(discriminator & 0xFF);
        };
    }

    public static String hex(byte[] payload, int maxBytes) {
        if (payload == null) {
            return "<null>";
        }
        int length = Math.min(payload.length, Math.max(0, maxBytes));
        StringBuilder builder = new StringBuilder(length * 3 + 32);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append("%02X".formatted(payload[i] & 0xFF));
        }
        if (payload.length > length) {
            builder.append(" ... [").append(payload.length - length).append(" more bytes]");
        }
        return builder.toString();
    }

    private static Reader readerFor(byte[] payload, int expectedDiscriminator) {
        require(payload != null && payload.length > 0, "Empty FML|HS payload");
        Reader reader = new Reader(payload);
        int actual = reader.readUnsignedByte();
        require(actual == (expectedDiscriminator & 0xFF),
                "Expected discriminator 0x%02X but got 0x%02X".formatted(expectedDiscriminator & 0xFF, actual));
        return reader;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public record ServerHello(int protocolVersion, Integer overrideDimension, int trailingBytes) {
    }

    public record ModIdData(
            Map<String, Integer> ids,
            Set<String> blockSubstitutions,
            Set<String> itemSubstitutions,
            int trailingBytes
    ) {
    }

    private static final class Reader {
        private final byte[] data;
        private int index;

        private Reader(byte[] data) {
            this.data = data;
        }

        int remaining() {
            return data.length - index;
        }

        int readUnsignedByte() {
            ensure(1);
            return data[index++] & 0xFF;
        }

        int readInt() {
            ensure(4);
            int value = ((data[index] & 0xFF) << 24)
                    | ((data[index + 1] & 0xFF) << 16)
                    | ((data[index + 2] & 0xFF) << 8)
                    | (data[index + 3] & 0xFF);
            index += 4;
            return value;
        }

        int readVarInt(int maxBytes) {
            int result = 0;
            for (int byteIndex = 0; byteIndex < maxBytes; byteIndex++) {
                int current = readUnsignedByte();
                result |= (current & 0x7F) << (byteIndex * 7);
                if ((current & 0x80) == 0) {
                    return result;
                }
            }
            throw new IllegalArgumentException("FML VarInt exceeds " + maxBytes + " bytes");
        }

        String readUtf8() {
            int length = readVarInt(2);
            ensure(length);
            String value = new String(data, index, length, StandardCharsets.UTF_8);
            index += length;
            return value;
        }

        private void ensure(int required) {
            if (required < 0 || remaining() < required) {
                throw new IllegalArgumentException(
                        "Truncated FML payload: need " + required + " bytes but only " + remaining() + " remain"
                );
            }
        }
    }

    private static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        void writeByte(int value) {
            output.write(value & 0xFF);
        }

        void writeVarInt(int value, int maxBytes) {
            if (value < 0) {
                throw new IllegalArgumentException("Negative FML VarInt: " + value);
            }
            int remaining = value;
            int bytes = 0;
            do {
                int current = remaining & 0x7F;
                remaining >>>= 7;
                if (remaining != 0) {
                    current |= 0x80;
                }
                writeByte(current);
                bytes++;
                if (bytes > maxBytes) {
                    throw new IllegalArgumentException("FML VarInt exceeds " + maxBytes + " bytes: " + value);
                }
            } while (remaining != 0);
        }

        void writeUtf8(String value) {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            writeVarInt(encoded.length, 2);
            output.writeBytes(encoded);
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
