package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeCodecWatcherValuesTest {
    @Test void simpleEntitySpawnRetainsTypedLegacyWatcherValues() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(FmlRuntimeCodec.ENTITY_SPAWN);out.writeInt(91);writeUtf8(out,"ExampleMod");out.writeInt(23);
        out.writeInt(32);out.writeInt(64);out.writeInt(96);out.writeByte(0);out.writeByte(0);out.writeByte(0);
        out.writeByte((0<<5)|0);out.writeByte(0);
        out.writeByte((1<<5)|1);out.writeShort(300);
        out.writeByte((2<<5)|12);out.writeInt(123456);
        out.writeByte((3<<5)|13);out.writeFloat(1.5F);
        out.writeByte((4<<5)|14);writeUtf8(out,"orb");
        out.writeByte((6<<5)|15);out.writeInt(4);out.writeInt(5);out.writeInt(6);
        out.writeByte(127);out.writeInt(0);

        FmlRuntimeCodec.SimpleEntitySpawn spawn=FmlRuntimeCodec.parseSimpleEntitySpawn(bytes.toByteArray());
        assertTrue(spawn.plainNonThrowable());assertEquals(6,spawn.watcherEntries());assertEquals(6,spawn.watcherValues().size());
        assertEquals(Byte.valueOf((byte)0),spawn.watcherValues().get(0).value());
        assertEquals(Short.valueOf((short)300),spawn.watcherValues().get(1).value());
        assertEquals(Integer.valueOf(123456),spawn.watcherValues().get(2).value());
        assertEquals(Float.valueOf(1.5F),spawn.watcherValues().get(3).value());
        assertEquals("orb",spawn.watcherValues().get(4).value());
        assertEquals(new FmlRuntimeCodec.LegacyCoordinates(4,5,6),spawn.watcherValues().get(5).value());
        assertEquals(12,spawn.watcherValues().get(2).id());assertEquals(2,spawn.watcherValues().get(2).type());
    }

    private static void writeUtf8(DataOutputStream out,String value)throws Exception{
        byte[] encoded=value.getBytes(StandardCharsets.UTF_8);if(encoded.length>=128)throw new IllegalArgumentException("test helper only supports one-byte lengths");out.writeByte(encoded.length);out.write(encoded);
    }
}
