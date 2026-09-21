package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.type.Types;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import dev.yinghuang.legacyforgebridge.render.LegacyMicroBlockNetworkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures legacy mod TileEntity S35 action-5 payloads immediately after the 1.7.10 -> 1.8
 * protocol has decoded them. ViaVersion 1.12.2 -> 1.13 removes action 5, so waiting for the
 * native 1.21 packet would lose the source TileEntity payload entirely.
 */
@Mixin(targets="com.viaversion.viaversion.api.protocol.AbstractProtocol",remap=false)
public abstract class ViaLegacyMicroBlockDataMixin {
    private static final String LEGACY_PROTOCOL=
            "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.Protocolr1_7_6_10Tor1_8";

    @Inject(
            method="transform(Lcom/viaversion/viaversion/api/protocol/packet/Direction;Lcom/viaversion/viaversion/api/protocol/packet/State;Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;)V",
            at=@At("TAIL"),
            remap=false
    )
    private void legacyforgebridge$captureMicroBlockS35(Direction direction,State state,PacketWrapper wrapper,CallbackInfo ci){
        if(direction!=Direction.CLIENTBOUND||state!=State.PLAY||!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target())return;
        if(!LEGACY_PROTOCOL.equals(getClass().getName()))return;
        PacketType type=wrapper.getPacketType();
        if(type==null||!"BLOCK_ENTITY_DATA".equals(type.getName()))return;
        try{
            short action=wrapper.get(Types.UNSIGNED_BYTE,0);
            if(action!=5)return;
            BlockPosition position=wrapper.get(Types.BLOCK_POSITION1_8,0);
            CompoundTag tag=wrapper.get(Types.NAMED_COMPOUND_TAG,0);
            LegacyMicroBlockNetworkBridge.capture(position,tag);
        }catch(RuntimeException ignored){
            // Not every action-5 packet belongs to a converted micro-block family. Fail closed.
        }
    }
}
