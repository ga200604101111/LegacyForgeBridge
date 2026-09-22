package dev.yinghuang.legacyforgebridge.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
     * IEntityAdditionalSpawnData behavior. Primitive/string/coordinate values and the bounded
     * 1.7.10 ItemStack wire envelope are retained so proof-gated generated/visual entities can map
     * source-owned synchronized fields. Compressed stack NBT is carried opaquely and never inflated here.
     */
    public static SimpleEntitySpawn parseSimpleEntitySpawn(byte[] payload){
        Reader reader=readerFor(payload,ENTITY_SPAWN);EntitySpawnHeader header=readEntitySpawnHeader(reader);
        List<LegacyDataWatcherEntry> watcherValues=readLegacyDataWatcher(reader);int throwerId=reader.readInt();
        int rawVelocityX=0,rawVelocityY=0,rawVelocityZ=0;
        if(throwerId!=0){
            rawVelocityX=reader.readInt();rawVelocityY=reader.readInt();rawVelocityZ=reader.readInt();
        }
        return new SimpleEntitySpawn(header,watcherValues.size(),watcherValues,throwerId,
                rawVelocityX,rawVelocityY,rawVelocityZ,reader.remaining());
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

    private static List<LegacyDataWatcherEntry> readLegacyDataWatcher(Reader reader){
        boolean[] seen=new boolean[32];List<LegacyDataWatcherEntry> entries=new ArrayList<>();
        while(true){
            int header=reader.readUnsignedByte();if(header==127)return List.copyOf(entries);
            int type=(header&224)>>5,id=header&31;
            require(!seen[id],"Duplicate legacy DataWatcher id "+id);seen[id]=true;
            require(entries.size()<32,"Legacy DataWatcher entry count exceeds 32");
            Object value=switch(type){
                case 0->Byte.valueOf((byte)reader.readSignedByte());
                case 1->Short.valueOf(reader.readShort());
                case 2->Integer.valueOf(reader.readInt());
                case 3->Float.valueOf(reader.readFloat());
                case 4->reader.readPacketString();
                case 5->reader.readLegacyItemStack();
                case 6->new LegacyCoordinates(reader.readInt(),reader.readInt(),reader.readInt());
                default->throw new IllegalArgumentException("Unsupported legacy DataWatcher type "+type);
            };
            entries.add(new LegacyDataWatcherEntry(type,id,value));
        }
    }

    private static Reader readerFor(byte[] payload,int expected){require(payload!=null&&payload.length>0,"Empty FML runtime payload");Reader reader=new Reader(payload);int actual=reader.readUnsignedByte();require(actual==(expected&0xFF),"Expected runtime discriminator 0x%02X but got 0x%02X".formatted(expected&0xFF,actual));return reader;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}

    public enum LegacySide{CLIENT,SERVER,UNKNOWN;static LegacySide fromOrdinal(int ordinal){return switch(ordinal){case 0->CLIENT;case 1->SERVER;default->UNKNOWN;};}}
    public record CompleteHandshake(int targetOrdinal,LegacySide target,int trailingBytes){}
    public record OpenGui(int windowId,String modId,int modGuiId,int x,int y,int z,int trailingBytes){}
    public record EntitySpawnHeader(int entityId,String modId,int modEntityTypeId,int rawX,int rawY,int rawZ,double x,double y,double z,float yaw,float pitch,float headYaw,int remainingBytes){}
    public record LegacyCoordinates(int x,int y,int z){}
    public record LegacyItemStack(int legacyItemId,int count,int damage,byte[] compressedNbt){
        public static final LegacyItemStack EMPTY=new LegacyItemStack(-1,0,0,new byte[0]);
        public LegacyItemStack{
            require(legacyItemId>=-1,"Invalid legacy ItemStack id");
            require(count>=0&&count<=255,"Invalid legacy ItemStack count");
            require(damage>=0&&damage<=65535,"Invalid legacy ItemStack damage");
            compressedNbt=compressedNbt==null?new byte[0]:compressedNbt.clone();
            require(compressedNbt.length<=32767,"Legacy ItemStack NBT exceeds short-length boundary");
            if(legacyItemId<0&&(count!=0||damage!=0||compressedNbt.length!=0))throw new IllegalArgumentException("Null legacy ItemStack carried data");
        }
        @Override public byte[] compressedNbt(){return compressedNbt.clone();}
        public boolean empty(){return legacyItemId<0||count==0;}
    }
    public record LegacyDataWatcherEntry(int type,int id,Object value){
        public LegacyDataWatcherEntry{require(type>=0&&type<=6,"Invalid legacy DataWatcher type "+type);require(id>=0&&id<=31,"Invalid legacy DataWatcher id "+id);require(value!=null,"Missing legacy DataWatcher value");}
    }
    public record SimpleEntitySpawn(EntitySpawnHeader header,int watcherEntries,List<LegacyDataWatcherEntry> watcherValues,int throwerId,
                                    int rawVelocityX,int rawVelocityY,int rawVelocityZ,int additionalSpawnBytes){
        public SimpleEntitySpawn{watcherValues=List.copyOf(watcherValues);require(watcherEntries==watcherValues.size(),"Legacy DataWatcher count/value mismatch");}
        public boolean throwableEnvelope(){return throwerId!=0;}
        public boolean additionalSpawnDataEmpty(){return additionalSpawnBytes==0;}
        public boolean plainNonThrowable(){return throwerId==0&&additionalSpawnBytes==0;}
        public double velocityX(){return rawVelocityX/8000.0D;}
        public double velocityY(){return rawVelocityY/8000.0D;}
        public double velocityZ(){return rawVelocityZ/8000.0D;}
    }
    public record EntityAdjust(int entityId,int serverX,int serverY,int serverZ,int trailingBytes){public double x(){return serverX/32.0D;}public double y(){return serverY/32.0D;}public double z(){return serverZ/32.0D;}}

    private static final class Reader{
        private final byte[] data;private int index;private Reader(byte[] data){this.data=data;}
        int remaining(){return data.length-index;}
        int readUnsignedByte(){ensure(1);return data[index++]&0xFF;}
        int readSignedByte(){ensure(1);return data[index++];}
        short readShort(){ensure(2);short value=(short)(((data[index]&0xFF)<<8)|(data[index+1]&0xFF));index+=2;return value;}
        int readInt(){ensure(4);int value=((data[index]&0xFF)<<24)|((data[index+1]&0xFF)<<16)|((data[index+2]&0xFF)<<8)|(data[index+3]&0xFF);index+=4;return value;}
        float readFloat(){return Float.intBitsToFloat(readInt());}
        int readVarInt(int maxBytes){int result=0;for(int byteIndex=0;byteIndex<maxBytes;byteIndex++){int current=readUnsignedByte();result|=(current&0x7F)<<(byteIndex*7);if((current&0x80)==0)return result;}throw new IllegalArgumentException("FML VarInt exceeds "+maxBytes+" bytes");}
        String readUtf8(){int length=readVarInt(2);require(length>=0&&length<=32767,"Legacy FML string exceeds 32767 bytes");return readStringBytes(length);}
        String readPacketString(){int length=readVarInt(5);require(length>=0&&length<=32767,"Legacy DataWatcher string exceeds 32767 bytes");return readStringBytes(length);}
        LegacyItemStack readLegacyItemStack(){
            int itemId=readShort();if(itemId<0)return LegacyItemStack.EMPTY;
            int count=readUnsignedByte();int damage=Short.toUnsignedInt(readShort());int nbtLength=readShort();
            require(nbtLength>=-1,"Invalid legacy ItemStack compressed NBT length "+nbtLength);
            byte[] nbt=nbtLength<0?new byte[0]:readBytes(nbtLength);
            return new LegacyItemStack(itemId,count,damage,nbt);
        }
        byte[] readBytes(int length){require(length>=0,"Negative byte length");ensure(length);byte[] value=java.util.Arrays.copyOfRange(data,index,index+length);index+=length;return value;}
        private String readStringBytes(int length){ensure(length);String value=new String(data,index,length,StandardCharsets.UTF_8);index+=length;return value;}
        private void ensure(int required){if(required<0||remaining()<required)throw new IllegalArgumentException("Truncated FML runtime payload: need "+required+" bytes but only "+remaining()+" remain");}
    }
}
