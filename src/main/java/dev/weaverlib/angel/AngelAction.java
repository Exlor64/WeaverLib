package dev.weaverlib.angel;

import dev.weaverlib.WeaverLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AngelAction(int action, int sequence) implements CustomPacketPayload {
    public static final int INVOKE = 0;
    public static final int ATTACK = 1;
    public static final int DEFEND = 2;
    public static final int RELEASE = 3;
    public static final int FLIGHT = 4;
    public static final int DASH = 5;
    public static final int STOP_DASH = 6;
    public static final Type<AngelAction> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "angel_action"));
    public static final StreamCodec<ByteBuf, AngelAction> CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.action()).writeByte(action.sequence()),
        buf -> new AngelAction(buf.readUnsignedByte(), buf.readUnsignedByte()));

    @Override
    public Type<AngelAction> type() {
        return TYPE;
    }
}
