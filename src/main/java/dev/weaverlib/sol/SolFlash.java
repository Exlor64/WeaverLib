package dev.weaverlib.sol;

import dev.weaverlib.WeaverLib;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SolFlash(Kind kind, Vec3 start, Vec3 end) implements CustomPacketPayload {
    public enum Kind {
        SKY, HAND, LANCE, IMPACT
    }

    public static final Type<SolFlash> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "sol_flash"));
    public static final StreamCodec<ByteBuf, SolFlash> CODEC = StreamCodec.of(
        (buf, flash) -> {
            buf.writeByte(flash.kind().ordinal());
            writePoint(buf, flash.start());
            writePoint(buf, flash.end());
        },
        buf -> new SolFlash(Kind.values()[buf.readUnsignedByte()], readPoint(buf), readPoint(buf)));

    private static Consumer<SolFlash> receiver = flash -> {};

    public static void setClientReceiver(Consumer<SolFlash> clientReceiver) {
        receiver = clientReceiver;
    }

    public void receive() {
        receiver.accept(this);
    }

    private static void writePoint(ByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x).writeDouble(point.y).writeDouble(point.z);
    }

    private static Vec3 readPoint(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<SolFlash> type() {
        return TYPE;
    }
}
