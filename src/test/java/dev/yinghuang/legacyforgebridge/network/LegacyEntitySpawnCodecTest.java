package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;
import java.io.DataOutputStream;
import java.util.Arrays;
import static dev.yinghuang.legacyforgebridge.network.ForgeRuntimeCodecTest.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyEntitySpawnCodecTest {
    private static void header(DataOutputStream out) throws Exception {
        out.writeByte(2); out.writeInt(55); utf8(out, "ExampleMod"); out.writeInt(8);
        out.writeInt(321); out.writeInt(-65); out.writeInt(2048);
        out.writeByte(64); out.writeByte(-64); out.writeByte(32);
    }
    private static byte[] spawn(Writer tail) throws Exception {
        return packet(out -> { header(out); tail.write(out); });
    }
    @Test void readsAllSevenWatcherTypesAndIntVelocityTail() throws Exception {
        byte[] payload = spawn(out -> {
            out.writeByte(0); out.writeByte(-17);
            out.writeByte(33); out.writeShort(-3200);
            out.writeByte(66); out.writeInt(1234567);
            out.writeByte(99); out.writeFloat(3.125F);
            out.writeByte(132); utf8(out, "虎王");
            out.writeByte(165); out.writeShort(4096); out.writeByte(3); out.writeShort(7);
            out.writeShort(3); out.write(new byte[]{31, (byte)139, 8});
            out.writeByte(198); out.writeInt(-5); out.writeInt(64); out.writeInt(30);
            out.writeByte(167); out.writeShort(-1);
            out.writeByte(127); out.writeInt(99);
            out.writeInt(-31200); out.writeInt(8000); out.writeInt(0);
            out.write(new byte[]{10, 20, 30, 40});
        });
        var result = LegacyEntitySpawnCodec.decode(payload);
        assertEquals(8, result.watchers().size());
        assertEquals((byte)-17, result.watchers().get(0).value());
        assertEquals((short)-3200, result.watchers().get(1).value());
        assertEquals(1234567, result.watchers().get(2).value());
        assertEquals(3.125F, result.watchers().get(3).value());
        assertEquals("虎王", result.watchers().get(4).value());
        var item = (LegacyEntitySpawnCodec.LegacyItem) result.watchers().get(5).value();
        assertEquals(4096, item.legacyId()); assertEquals(3, item.count()); assertEquals(7, item.damage());
        assertArrayEquals(new byte[]{31, (byte)139, 8}, item.compressedNbt());
        assertEquals(new LegacyEntitySpawnCodec.BlockPosition(-5, 64, 30), result.watchers().get(6).value());
        assertNull(result.watchers().get(7).value());
        assertEquals(99, result.throwerId());
        assertEquals(-3.9D, result.velocityX()); assertEquals(1.0D, result.velocityY()); assertEquals(0.0D, result.velocityZ());
        assertArrayEquals(new byte[]{10, 20, 30, 40}, result.additionalData());
        assertEquals(10.03125D, result.header().x()); assertEquals(-2.03125D, result.header().y());
        assertEquals(90F, result.header().yaw()); assertEquals(-90F, result.header().pitch()); assertEquals(45F, result.header().headYaw());
    }
    @Test void zeroThrowerDoesNotConsumeExtraSpawnDataAsVelocity() throws Exception {
        var result = LegacyEntitySpawnCodec.decode(spawn(out -> {
            out.writeByte(127); out.writeInt(0); out.write(new byte[12]);
        }));
        assertEquals(12, result.additionalData().length); assertEquals(0D, result.velocityX());
    }
    @Test void preservesNullItemNbtAndNullItemFraming() throws Exception {
        var result = LegacyEntitySpawnCodec.decode(spawn(out -> {
            out.writeByte(160); out.writeShort(12); out.writeByte(1); out.writeShort(0); out.writeShort(-1);
            out.writeByte(161); out.writeShort(-1); out.writeByte(127); out.writeInt(0);
        }));
        assertNull(((LegacyEntitySpawnCodec.LegacyItem) result.watchers().getFirst().value()).compressedNbt());
        assertNull(result.watchers().get(1).value());
    }
    @Test void byteArraysAndWatcherListsAreDefensive() throws Exception {
        byte[] payload = spawn(out -> {
            out.writeByte(160); out.writeShort(12); out.writeByte(1); out.writeShort(0); out.writeShort(1); out.writeByte(23);
            out.writeByte(127); out.writeInt(0); out.writeByte(42);
        });
        var result = LegacyEntitySpawnCodec.decode(payload);
        Arrays.fill(payload, (byte)0);
        result.additionalData()[0] = 0;
        var item = (LegacyEntitySpawnCodec.LegacyItem) result.watchers().getFirst().value();
        item.compressedNbt()[0] = 0;
        assertArrayEquals(new byte[]{42}, result.additionalData()); assertArrayEquals(new byte[]{23}, item.compressedNbt());
        assertThrows(UnsupportedOperationException.class, () -> result.watchers().clear());
    }
    @Test void rejectsAllTruncatedPrefixesOfCompleteThrowablePacket() throws Exception {
        byte[] valid = spawn(out -> { out.writeByte(127); out.writeInt(55); out.writeInt(0); out.writeInt(0); out.writeInt(0); });
        for (int i = 0; i < valid.length; i++) {
            byte[] truncated = Arrays.copyOf(valid, i);
            assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(truncated), "prefix=" + i);
        }
    }
    @Test void rejectsDuplicateSlotsUnknownTypesAndMissingTerminator() throws Exception {
        byte[] duplicate = spawn(out -> { out.writeByte(0); out.writeByte(1); out.writeByte(0); out.writeByte(2); out.writeByte(127); out.writeInt(0); });
        byte[] unknown = spawn(out -> out.writeByte(224));
        byte[] unterminated = spawn(out -> { out.writeByte(0); out.writeByte(1); });
        assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(duplicate));
        assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(unknown));
        assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(unterminated));
    }
    @Test void rejectsTruncatedCompressedNbtWithoutInflation() throws Exception {
        byte[] invalid = spawn(out -> {
            out.writeByte(160); out.writeShort(12); out.writeByte(1); out.writeShort(0); out.writeShort(100); out.writeByte(1);
        });
        assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(invalid));
    }
    @Test void rejectsMalformedWatcherStringAndHugePayload() throws Exception {
        byte[] invalid = spawn(out -> { out.writeByte(128); out.writeByte(1); out.writeByte(255); out.writeByte(127); out.writeInt(0); });
        assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(invalid));
        assertThrows(IllegalArgumentException.class, () -> LegacyEntitySpawnCodec.decode(new byte[1_048_577]));
    }
}
