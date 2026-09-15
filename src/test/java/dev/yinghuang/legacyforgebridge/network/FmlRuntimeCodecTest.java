package dev.longyu.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FmlRuntimeCodecTest {
    @Test
    void runtimeDiscriminatorsDoNotReuseHandshakeNames() {
        assertEquals("CompleteHandshake", FmlRuntimeCodec.discriminatorName(0));
        assertEquals("OpenGui", FmlRuntimeCodec.discriminatorName(1));
        assertEquals("EntitySpawnMessage", FmlRuntimeCodec.discriminatorName(2));
        assertEquals("EntityAdjustMessage", FmlRuntimeCodec.discriminatorName(3));
    }

    @Test
    void parsesCompleteHandshakeTargetSide() {
        FmlRuntimeCodec.CompleteHandshake message = FmlRuntimeCodec.parseCompleteHandshake(
                new byte[]{0, 0}
        );

        assertEquals(0, message.targetOrdinal());
        assertEquals(FmlRuntimeCodec.LegacySide.CLIENT, message.target());
        assertEquals(0, message.trailingBytes());
    }

    @Test
    void parsesOpenGuiMessage() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(FmlRuntimeCodec.OPEN_GUI);
        out.writeInt(7);
        writeLegacyUtf8(out, "RPGTool1");
        out.writeInt(12);
        out.writeInt(10);
        out.writeInt(64);
        out.writeInt(-5);

        FmlRuntimeCodec.OpenGui message = FmlRuntimeCodec.parseOpenGui(bytes.toByteArray());

        assertEquals(7, message.windowId());
        assertEquals("RPGTool1", message.modId());
        assertEquals(12, message.modGuiId());
        assertEquals(10, message.x());
        assertEquals(64, message.y());
        assertEquals(-5, message.z());
        assertEquals(0, message.trailingBytes());
    }

    @Test
    void parsesEntitySpawnStableHeaderAndLeavesLegacyMetadataOpaque() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(FmlRuntimeCodec.ENTITY_SPAWN);
        out.writeInt(42);
        writeLegacyUtf8(out, "ExampleMod");
        out.writeInt(9);
        out.writeInt(320);
        out.writeInt(2048);
        out.writeInt(-64);
        out.writeByte(64);
        out.writeByte(-64);
        out.writeByte(32);
        out.write(new byte[]{11, 22, 33, 44});

        FmlRuntimeCodec.EntitySpawnHeader message = FmlRuntimeCodec.parseEntitySpawnHeader(bytes.toByteArray());

        assertEquals(42, message.entityId());
        assertEquals("ExampleMod", message.modId());
        assertEquals(9, message.modEntityTypeId());
        assertEquals(10.0D, message.x());
        assertEquals(64.0D, message.y());
        assertEquals(-2.0D, message.z());
        assertEquals(90.0F, message.yaw());
        assertEquals(-90.0F, message.pitch());
        assertEquals(45.0F, message.headYaw());
        assertEquals(4, message.remainingBytes());
    }

    @Test
    void entityAdjustConvertsLegacyFixedPointToModernPacketBaseline() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(FmlRuntimeCodec.ENTITY_ADJUST);
        out.writeInt(1234);
        out.writeInt(321);
        out.writeInt(-65);
        out.writeInt(2048);

        FmlRuntimeCodec.EntityAdjust message = FmlRuntimeCodec.parseEntityAdjust(bytes.toByteArray());

        assertEquals(1234, message.entityId());
        assertEquals(321, message.serverX());
        assertEquals(-65, message.serverY());
        assertEquals(2048, message.serverZ());
        assertEquals(10.03125D, message.x());
        assertEquals(-2.03125D, message.y());
        assertEquals(64.0D, message.z());
        assertEquals(0, message.trailingBytes());
    }

    @Test
    void rejectsTruncatedEntityAdjust() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FmlRuntimeCodec.parseEntityAdjust(new byte[]{3, 0, 0, 0, 1})
        );
    }

    private static void writeLegacyUtf8(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length >= 128) {
            throw new IllegalArgumentException("Test helper only supports one-byte legacy VarInt lengths");
        }
        out.writeByte(encoded.length);
        out.write(encoded);
    }
}
