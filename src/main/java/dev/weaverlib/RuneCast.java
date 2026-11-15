package dev.weaverlib;

import dev.weaverlib.rune.AonDrawing;
import dev.weaverlib.rune.AonDrawing.Point;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RuneCast(List<List<Point>> strokes) implements CustomPacketPayload {
    public static final Type<RuneCast> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "rune_cast"));
    public static final StreamCodec<ByteBuf, RuneCast> CODEC =
        StreamCodec.of(RuneCast::encode, RuneCast::decode);

    public RuneCast {
        strokes = AonDrawing.copyStrokes(strokes);
    }

    private static void encode(ByteBuf buffer, RuneCast payload) {
        buffer.writeByte(payload.strokes.size());
        for (List<Point> stroke : payload.strokes) {
            buffer.writeByte(stroke.size());
            for (Point point : stroke) {
                buffer.writeShort((int) Math.round(point.x() * 65535));
                buffer.writeShort((int) Math.round(point.y() * 65535));
            }
        }
    }

    private static RuneCast decode(ByteBuf buffer) {
        int strokeCount = buffer.readUnsignedByte();
        if (strokeCount < 1 || strokeCount > AonDrawing.MAX_STROKES) {
            throw new DecoderException("Invalid rune stroke count");
        }
        var drawing = new ArrayList<List<Point>>(strokeCount);
        for (int index = 0; index < strokeCount; index++) {
            int pointCount = buffer.readUnsignedByte();
            if (pointCount < 2 || pointCount > AonDrawing.MAX_POINTS) {
                throw new DecoderException("Invalid rune point count");
            }
            var stroke = new ArrayList<Point>(pointCount);
            for (int point = 0; point < pointCount; point++) {
                stroke.add(new Point(buffer.readUnsignedShort() / 65535.0, buffer.readUnsignedShort() / 65535.0));
            }
            drawing.add(stroke);
        }
        return new RuneCast(drawing);
    }

    @Override
    public Type<RuneCast> type() {
        return TYPE;
    }
}
