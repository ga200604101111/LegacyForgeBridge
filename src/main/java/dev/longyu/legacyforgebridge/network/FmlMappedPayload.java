package dev.longyu.legacyforgebridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;

/**
 * Opaque modern payload IDs used as reversible aliases for legacy Forge plugin channels.
 * ViaVersion maps these IDs back to FML|HS/FML/FORGE on the 1.7.10 side.
 */
public record FmlMappedPayload(Type<FmlMappedPayload> type, byte[] data) implements CustomPacketPayload {
    public static final Type<FmlMappedPayload> FML_HS = type("fml_hs");
    public static final Type<FmlMappedPayload> FML = type("fml");
    public static final Type<FmlMappedPayload> FORGE = type("forge");

    public FmlMappedPayload {
        data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
    }

    public static StreamCodec<RegistryFriendlyByteBuf, FmlMappedPayload> codec(Type<FmlMappedPayload> type) {
        return CustomPacketPayload.codec(
                FmlMappedPayload::write,
                buffer -> read(type, buffer)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBytes(data);
    }

    private static FmlMappedPayload read(Type<FmlMappedPayload> type, RegistryFriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new FmlMappedPayload(type, data);
    }

    private static Type<FmlMappedPayload> type(String path) {
        return new Type<>(Identifier.fromNamespaceAndPath("legacyforgebridge", path));
    }
}
