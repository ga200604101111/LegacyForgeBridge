package dev.yinghuang.legacyforgebridge.network;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded reader for untrusted legacy payloads; never decompresses network NBT. */
final class LegacyPayloadReader {
    static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private final byte[] data;
    private int offset;

    LegacyPayloadReader(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Legacy payload must contain 1.." + MAX_PAYLOAD_BYTES + " bytes");
        }
        this.data = data;
    }

    int remaining() { return data.length - offset; }
    void skip(int length) { require(length); offset += length; }
    int unsignedByte() { require(1); return data[offset++] & 255; }
    byte signedByte() { return (byte) unsignedByte(); }
    short signedShort() { return (short) ((unsignedByte() << 8) | unsignedByte()); }
    int integer() {
        return (unsignedByte() << 24) | (unsignedByte() << 16) | (unsignedByte() << 8) | unsignedByte();
    }
    float floating() { return Float.intBitsToFloat(integer()); }
    byte[] bytes(int length) {
        require(length);
        byte[] result = Arrays.copyOfRange(data, offset, offset + length);
        offset += length;
        return result;
    }
    int varInt(int maxBytes) {
        long value = 0;
        for (int i = 0; i < maxBytes; i++) {
            int current = unsignedByte();
            value |= (long) (current & 127) << (i * 7);
            if ((current & 128) == 0) {
                if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Legacy length overflows int");
                return (int) value;
            }
        }
        throw new IllegalArgumentException("Legacy VarInt exceeds " + maxBytes + " bytes");
    }
    String fmlString() { return string(2, 16_383, 16_383); }
    String watcherString() { return string(5, 32_767 * 4, 32_767); }
    private String string(int maxVarIntBytes, int maxBytes, int maxChars) {
        int length = varInt(maxVarIntBytes);
        if (length > maxBytes) throw new IllegalArgumentException("Legacy string exceeds byte limit");
        require(length);
        try {
            String result = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, offset, length)).toString();
            if (result.length() > maxChars) throw new IllegalArgumentException("Legacy string exceeds character limit");
            offset += length;
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 in legacy payload", exception);
        }
    }
    void end() {
        if (remaining() != 0) throw new IllegalArgumentException("Unexpected legacy trailing bytes: " + remaining());
    }
    private void require(int count) {
        if (count < 0 || count > remaining()) {
            throw new IllegalArgumentException("Truncated legacy payload: need " + count + ", remaining " + remaining());
        }
    }
}
