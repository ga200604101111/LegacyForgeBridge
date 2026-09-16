package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeCodecTest {
    @Test void runtimeDiscriminatorsDoNotReuseHandshakeNames(){assertEquals("CompleteHandshake",FmlRuntimeCodec.discriminatorName(0));assertEquals("OpenGui",FmlRuntimeCodec.discriminatorName(1));assertEquals("EntitySpawnMessage",FmlRuntimeCodec.discriminatorName(2));assertEquals("EntityAdjustMessage",FmlRuntimeCodec.discriminatorName(3));}

    @Test void parsesCompleteHandshakeTargetSide(){var message=FmlRuntimeCodec.parseCompleteHandshake(new byte[]{0,0});assertEquals(0,message.targetOrdinal());assertEquals(FmlRuntimeCodec.LegacySide.CLIENT,message.target());assertEquals(0,message.trailingBytes());}

    @Test void parsesOpenGuiMessage()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeByte(FmlRuntimeCodec.OPEN_GUI);out.writeInt(7);writeLegacyUtf8(out,"RPGTool1");out.writeInt(12);out.writeInt(10);out.writeInt(64);out.writeInt(-5);
        var message=FmlRuntimeCodec.parseOpenGui(bytes.toByteArray());assertEquals(7,message.windowId());assertEquals("RPGTool1",message.modId());assertEquals(12,message.modGuiId());assertEquals(10,message.x());assertEquals(64,message.y());assertEquals(-5,message.z());assertEquals(0,message.trailingBytes());
    }

    @Test void parsesEntitySpawnStableHeaderAndLeavesLegacyMetadataOpaque()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeSpawnHeader(out,42,"ExampleMod",9,320,2048,-64,64,-64,32);out.write(new byte[]{11,22,33,44});
        var message=FmlRuntimeCodec.parseEntitySpawnHeader(bytes.toByteArray());assertEquals(42,message.entityId());assertEquals("ExampleMod",message.modId());assertEquals(9,message.modEntityTypeId());assertEquals(10.0D,message.x());assertEquals(64.0D,message.y());assertEquals(-2.0D,message.z());assertEquals(90.0F,message.yaw());assertEquals(-90.0F,message.pitch());assertEquals(45.0F,message.headYaw());assertEquals(4,message.remainingBytes());
    }

    @Test void parsesSimpleEntitySpawnPastLegacyBaseWatcherStream()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeSpawnHeader(out,77,"ExampleMod",23,16,32,48,0,0,0);
        out.writeByte(0);out.writeByte(0);                    // type 0, id 0: flags byte
        out.writeByte((1<<5)|1);out.writeShort(300);         // type 1, id 1: air short
        out.writeByte((4<<5)|2);writePacketString(out,"");   // type 4, id 2: custom name
        out.writeByte(3);out.writeByte(0);                   // type 0, id 3: name visible
        out.writeByte(127);out.writeInt(0);                  // watcher terminator + non-throwable
        var spawn=FmlRuntimeCodec.parseSimpleEntitySpawn(bytes.toByteArray());
        assertEquals(77,spawn.header().entityId());assertEquals(4,spawn.watcherEntries());assertEquals(0,spawn.throwerId());assertEquals(0,spawn.additionalSpawnBytes());assertTrue(spawn.plainNonThrowable());
    }

    @Test void simpleEntitySpawnRetainsAdditionalSpawnBytesForFailClosedCaller()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeSpawnHeader(out,78,"ExampleMod",23,0,0,0,0,0,0);out.writeByte(127);out.writeInt(0);out.writeByte(99);
        var spawn=FmlRuntimeCodec.parseSimpleEntitySpawn(bytes.toByteArray());assertEquals(1,spawn.additionalSpawnBytes());assertFalse(spawn.plainNonThrowable());
    }

    @Test void simpleEntitySpawnRejectsItemStackWatcherBoundary()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeSpawnHeader(out,79,"ExampleMod",23,0,0,0,0,0,0);out.writeByte((5<<5)|4);
        assertThrows(IllegalArgumentException.class,()->FmlRuntimeCodec.parseSimpleEntitySpawn(bytes.toByteArray()));
    }

    @Test void entityAdjustConvertsLegacyFixedPointToModernPacketBaseline()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeByte(FmlRuntimeCodec.ENTITY_ADJUST);out.writeInt(1234);out.writeInt(321);out.writeInt(-65);out.writeInt(2048);
        var message=FmlRuntimeCodec.parseEntityAdjust(bytes.toByteArray());assertEquals(1234,message.entityId());assertEquals(321,message.serverX());assertEquals(-65,message.serverY());assertEquals(2048,message.serverZ());assertEquals(10.03125D,message.x());assertEquals(-2.03125D,message.y());assertEquals(64.0D,message.z());assertEquals(0,message.trailingBytes());
    }

    @Test void rejectsTruncatedEntityAdjust(){assertThrows(IllegalArgumentException.class,()->FmlRuntimeCodec.parseEntityAdjust(new byte[]{3,0,0,0,1}));}

    private static void writeSpawnHeader(DataOutputStream out,int entityId,String modId,int typeId,int x,int y,int z,int yaw,int pitch,int headYaw)throws IOException{out.writeByte(FmlRuntimeCodec.ENTITY_SPAWN);out.writeInt(entityId);writeLegacyUtf8(out,modId);out.writeInt(typeId);out.writeInt(x);out.writeInt(y);out.writeInt(z);out.writeByte(yaw);out.writeByte(pitch);out.writeByte(headYaw);}
    private static void writeLegacyUtf8(DataOutputStream out,String value)throws IOException{byte[] encoded=value.getBytes(StandardCharsets.UTF_8);if(encoded.length>=128)throw new IllegalArgumentException("Test helper only supports one-byte legacy VarInt lengths");out.writeByte(encoded.length);out.write(encoded);}
    private static void writePacketString(DataOutputStream out,String value)throws IOException{byte[] encoded=value.getBytes(StandardCharsets.UTF_8);if(encoded.length>=128)throw new IllegalArgumentException("Test helper only supports one-byte packet VarInt lengths");out.writeByte(encoded.length);out.write(encoded);}
}
