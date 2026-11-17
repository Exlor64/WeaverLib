package dev.weaverlib;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public enum CastCurioInscription implements CustomPacketPayload {
    INSTANCE;

    public static final Type<CastCurioInscription> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "cast_curio_inscription"));
    public static final StreamCodec<ByteBuf, CastCurioInscription> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<CastCurioInscription> type() {
        return TYPE;
    }
}
