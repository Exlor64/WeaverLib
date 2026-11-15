package dev.weaverlib;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CastInscription(UUID inscriptionId) implements CustomPacketPayload {
    public static final Type<CastInscription> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "cast_inscription"));
    public static final StreamCodec<ByteBuf, CastInscription> CODEC =
        UUIDUtil.STREAM_CODEC.map(CastInscription::new, CastInscription::inscriptionId);

    @Override
    public Type<CastInscription> type() {
        return TYPE;
    }
}
