package dev.weaverlib.angel;

import dev.weaverlib.WeaverLib;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record AngelBurst(int kind, Vec3 from, Vec3 to) implements CustomPacketPayload {
    /** Packet flag: animated mesh effects only, with no native particles. */
    public static final int FIRE = 64;
    public boolean fire() { return (kind & FIRE) != 0; }
    public int effect() { return kind & ~FIRE; }

    public static final int AWAKEN = 0;
    public static final int DIM = 1;
    public static final int IMPACT = 2;
    public static final int FEATHER = 3;
    public static final int FEATHER_IMPACT = 4;
    public static final Type<AngelBurst> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "angel_burst"));
    public static final StreamCodec<ByteBuf, AngelBurst> CODEC = StreamCodec.of((buf, burst) -> {
        buf.writeByte(burst.kind());
        write(buf, burst.from());
        write(buf, burst.to());
    }, buf -> new AngelBurst(buf.readUnsignedByte(), read(buf), read(buf)));
    private static Consumer<AngelBurst> receiver = burst -> {};

    private static void write(ByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x).writeDouble(v.y).writeDouble(v.z);
    }

    private static Vec3 read(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void receiver(Consumer<AngelBurst> callback) {
        receiver = callback;
    }

    public void receive() {
        receiver.accept(this);
    }

    @Override
    public Type<AngelBurst> type() {
        return TYPE;
    }
}
