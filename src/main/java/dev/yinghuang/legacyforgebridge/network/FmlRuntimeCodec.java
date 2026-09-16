package dev.yinghuang.legacyforgebridge.network;

import java.nio.charset.StandardCharsets;

/** Forge/FML 1.7.10 runtime-channel codec for the legacy {@code FML} custom payload channel. */
public final class FmlRuntimeCodec {
    public static final int COMPLETE_HANDSHAKE=0,OPEN_GUI=1,ENTITY_SPAWN=2,ENTITY_ADJUST=3;
    private FmlRuntimeCodec() { }

    public static int discriminator(byte[] payload){require(payload!=null&&payload.length>0,"Empty FML runtime payload");return payload[0]&0xFF;}

    public static CompleteHandshake parseCompleteHandshake(byte[] payload){Reader reader=readerFor(payload,COMPLETE_HANDSHAKE);int targetOrdinal=reader.readUnsignedByte();return new CompleteHandshake(targetOrdinal,LegacySide.fromOrdinal(targetOrdinal),reader.remaining());}

    public static OpenGui parseOpenGui(byte[] payload){Reader reader=readerFor(payload,OPEN_GUI);int windowId=reader.readInt();String modId=reader.readUtf8();int modGuiId=reader.readInt();int x=reader.readInt(),y=reader.readInt(),z=reader.readInt();return new OpenGui(windowId,modId,modGuiId,x,y,z,reader.remaining());}

    /** Decodes only the stable prefix; watcher/throwable/additional spawn bytes remain opaque. */
    public static EntitySpawnHeader parseEntitySpawnHeader(byte[] payload){return readEntitySpawnHeader(readerFor(payload,ENTITY_SPAWN));}

    /**
     * Strict spawn envelope for converted entities whose source proof excludes throwable and
     * IEntityAdditionalSpawnData behavior. The 1.7 DataWatcher list is structurally skipped so the
     * following thrower id and additional bytes cannot be confused with watcher payload.
     *
     * <p>ItemStack watcher entries are deliberately rejected by this bounded codec. The admitted
     * transient-seat source directly extends the legacy Entity base and has an empty entityInit,
     * so its watcher stream contains only base scalar/string values. Future entity families may
     * extend this decoder when source proof requires more watcher types.</p>
     */
    public static SimpleEntitySpawn parseSimpleEntitySpawn(byte[] payload){
        Reader reader=readerFor(payload,ENTITY_SPAWN);EntitySpawnHeader header=readEntitySpawnHeader(reader);
        int watcherEntries=skipLegacyDataWatcher(reader);int throwerId=reader.readInt();
        return new SimpleEntitySpawn(header,watcherEntries,throwerId,reader.remaining());
    }

    public static EntityAdjust parseEntityAdjust(byte[] payload){Reader reader=readerFor(payload,ENTITY_ADJUST);int entityId=reader.readInt(),serverX=reader.readInt(),serverY=reader.readInt(),serverZ=reader.readInt();return new EntityAdjust(entityId,serverX,serverY,serverZ,reader.remaining());}

    public static String discriminatorName(int discriminator){return switch(discriminator&0xFF){case COMPLETE_HANDSHAKE->"CompleteHandshake";case OPEN_GUI->"OpenGui";case ENTITY_SPAWN->"EntitySpawnMessage";case ENTITY_ADJUST->"EntityAdjustMessage";default->"Unknown(0x%02X)".formatted(discriminator&0xFF);};}

    private static EntitySpawnHeader readEntitySpawnHeader(Reader reader){
        int entityId=reader.readInt();String modId=reader.readUtf8();int modEntityTypeId=reader.readInt();
        int rawX=reader.readInt(),rawY=reader.readInt(),rawZ=reader.readInt();
        int rawYaw=reader.readSignedByte(),rawPitch=reader.readSignedByte(),rawHeadYaw=reader.readSignedByte();
        return new EntitySpawnHeader(entityId,modId,modEntityTypeId,rawX,rawY,rawZ,
                rawX/32.0D,rawY/32.0D,rawZ/32.0D,
                rawYaw*360.0F/256.0F,rawPitch*360.0F/256.0F,rawHeadYaw*360.0F/256.0F,reader.remaining());
    }

    private static int skipLegacyDataWatcher(Reader reader){
        boolean[] seen=new boolean[32];int entries=0;
        while(true){
            int header=reader.readUnsignedByte();if(header==127)return entries;
            int type=(header&224)>>5,id=header&31;
            require(!seen[id],"Duplicate legacy DataWatcher id "+id);seen[id]=true;
            require(++entries<=32,"Legacy DataWatcher entry count exceeds 32");
            switch(type){
                case 0->reader.skip(1); // byte
                case 1->reader.skip(2); // short
                case 2,3->reader.skip(4); // int / float
                case 4->{int length=reader.readVarInt(5);require(length>=0&&length<=32767,"Legacy DataWatcher string exceeds 32767 bytes");reader.skip(length);}
                case 5->throw new IllegalArgumentException("ItemStack legacy DataWatcher entry is outside simple-entity spawn boundary");
                case 6->reader.skip(12); // ChunkCoordinates x/y/z
                default->throw new IllegalArgumentException("Unsupported legacy DataWatcher type "+type);
            }
        }
    }

    private static Reader readerFor(byte[] payload,int expected){require(payload!=null&&payload.length>0,"Empty FML runtime payload");Reader reader=new Reader(payload);int actual=reader.readUnsignedByte();require(actual==(expected&0xFF),"Expected runtime discriminator 0x%02X but got 0x%02X".formatted(expected&0xFF,actual));return reader;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}

    public enum LegacySide{CLIENT,SERVER,UNKNOWN;static LegacySide fromOrdinal(int ordinal){return switch(ordinal){case 0->CLIENT;case 1->SERVER;default->UNKNOWN;};}}
    public record CompleteHandshake(int targetOrdinal,LegacySide target,int trailingBytes){}
    public record OpenGui(int windowId,String modId,int modGuiId,int x,int y,int z,int trailingBytes){}
    public record EntitySpawnHeader(int entityId,String modId,int modEntityTypeId,int rawX,int rawY,int rawZ,double x,double y,double z,float yaw,float pitch,float headYaw,int remainingBytes){}
    public record SimpleEntitySpawn(EntitySpawnHeader header,int watcherEntries,int throwerId,int additionalSpawnBytes){
        public boolean plainNonThrowable(){return throwerId==0&&additionalSpawnBytes==0;}
    }
    public record EntityAdjust(int entityId,int serverX,int serverY,int serverZ,int trailingBytes){public double x(){return serverX/32.0D;}public double y(){return serverY/32.0D;}public double z(){return serverZ/32.0D;}}

    private static final class Reader{
        private final byte[] data;private int index;private Reader(byte[] data){this.data=data;}
        int remaining(){return data.length-index;}
        int readUnsignedByte(){ensure(1);return data[index++]&0xFF;}
        int readSignedByte(){ensure(1);return data[index++];}
        int readInt(){ensure(4);int value=((data[index]&0xFF)<<24)|((data[index+1]&0xFF)<<16)|((data[index+2]&0xFF)<<8)|(data[index+3]&0xFF);index+=4;return value;}
        int readVarInt(int maxBytes){int result=0;for(int byteIndex=0;byteIndex<maxBytes;byteIndex++){int current=readUnsignedByte();result|=(current&0x7F)<<(byteIndex*7);if((current&0x80)==0)return result;}throw new IllegalArgumentException("FML VarInt exceeds "+maxBytes+" bytes");}
        String readUtf8(){int length=readVarInt(2);ensure(length);String value=new String(data,index,length,StandardCharsets.UTF_8);index+=length;return value;}
        void skip(int bytes){ensure(bytes);index+=bytes;}
        private void ensure(int required){if(required<0||remaining()<required)throw new IllegalArgumentException("Truncated FML runtime payload: need "+required+" bytes but only "+remaining()+" remain");}
    }
}
