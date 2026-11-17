package dev.weaverlib.sol;

import dev.weaverlib.WeaverLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public enum SolAction implements CustomPacketPayload {
    CYCLE_MODE, SKY_BEAM, HAND_BEAM, LANCE;

    public static final Type<SolAction> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "sol_action"));
    public static final StreamCodec<ByteBuf, SolAction> CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.ordinal()), buf -> {
            int id = buf.readUnsignedByte();
            return switch (id) {
                case 0 -> CYCLE_MODE;
                case 1 -> SKY_BEAM;
                case 2 -> HAND_BEAM;
                case 3 -> LANCE;
                default -> throw new IllegalArgumentException("Unknown Sol action " + id);
            };
        });

    @Override
    public Type<SolAction> type() {
        return TYPE;
    }
}
