package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ForgeRuntimeCodecTest {
    interface Writer { void write(DataOutputStream out) throws Exception; }
    static byte[] packet(Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        writer.write(out);
        return bytes.toByteArray();
    }
    static void utf8(DataOutputStream out, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        do { int next = length & 127; length >>>= 7; out.writeByte(next | (length == 0 ? 0 : 128)); } while (length != 0);
        out.write(bytes);
    }
    static byte[] fluids(boolean defaults) throws Exception {
        return packet(out -> {
            out.writeByte(2); out.writeInt(2);
            utf8(out, "water"); out.writeInt(42);
            utf8(out, "lava"); out.writeInt(9);
            if (defaults) { utf8(out, "minecraft:water"); utf8(out, "minecraft:lava"); }
        });
    }
    @Test void decodesSignedDimensionAndProviderIds() throws Exception {
        assertEquals(new ForgeRuntimeCodec.DimensionRegister(-5, -1), ForgeRuntimeCodec.decode(packet(out -> {
            out.writeByte(1); out.writeInt(-5); out.writeInt(-1);
        })));
    }
    @Test void supportsLegacyFluidMapWithoutDefaultList() throws Exception {
        var result = (ForgeRuntimeCodec.FluidIdMap) ForgeRuntimeCodec.decode(fluids(false));
        assertEquals(Map.of("water", 42, "lava", 9), result.ids());
        assertFalse(result.hasDefaults()); assertTrue(result.defaults().isEmpty());
    }
    @Test void readsDefaultListAndKeepsServerIds() throws Exception {
        var result = (ForgeRuntimeCodec.FluidIdMap) ForgeRuntimeCodec.decode(fluids(true));
        assertTrue(result.hasDefaults());
        assertEquals(List.of("minecraft:water", "minecraft:lava"), result.defaults());
        assertEquals(42, result.ids().get("water"));
        assertThrows(UnsupportedOperationException.class, () -> result.ids().put("other", 3));
        assertThrows(UnsupportedOperationException.class, () -> result.defaults().add("other"));
    }
    @Test void rejectsEveryTruncatedDimensionPrefix() throws Exception {
        byte[] valid = packet(out -> { out.writeByte(1); out.writeInt(3); out.writeInt(7); });
        for (int i = 0; i < valid.length; i++) {
            byte[] truncated = Arrays.copyOf(valid, i);
            assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(truncated));
        }
    }
    @Test void rejectsIncompleteDefaultListAndTrailingGarbage() throws Exception {
        byte[] valid = fluids(true);
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
    }
    @Test void rejectsNegativeAndHugeCountsBeforeAllocation() throws Exception {
        for (int count : new int[]{-1, Integer.MAX_VALUE, ForgeRuntimeCodec.MAX_FLUIDS + 1}) {
            byte[] input = packet(out -> { out.writeByte(2); out.writeInt(count); });
            assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(input));
        }
    }
    @Test void rejectsDuplicateFluidNamesAndIds() throws Exception {
        for (boolean duplicateName : new boolean[]{true, false}) {
            byte[] input = packet(out -> {
                out.writeByte(2); out.writeInt(2);
                utf8(out, "water"); out.writeInt(1);
                utf8(out, duplicateName ? "water" : "lava"); out.writeInt(duplicateName ? 2 : 1);
            });
            assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(input));
        }
    }
    @Test void rejectsMalformedUtf8AndOverlongVarInt() throws Exception {
        byte[] invalid = packet(out -> { out.writeByte(2); out.writeInt(1); out.writeByte(1); out.writeByte(0xFF); out.writeInt(1); });
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(invalid));
        byte[] overlong = packet(out -> { out.writeByte(2); out.writeInt(1); out.write(new byte[]{(byte)128, (byte)128, 0, 0, 0}); });
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(overlong));
    }
    @Test void emptyFluidMapIsValidButUnknownChannelDiscriminatorIsNot() throws Exception {
        var empty = (ForgeRuntimeCodec.FluidIdMap) ForgeRuntimeCodec.decode(packet(out -> { out.writeByte(2); out.writeInt(0); }));
        assertTrue(empty.ids().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(new byte[]{0, 0}));
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> ForgeRuntimeCodec.decode(new byte[1_048_577]));
    }
}
