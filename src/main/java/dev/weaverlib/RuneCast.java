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

public record RuneCast(List<List<List<Point>>> glyphs) implements CustomPacketPayload {
    public static final Type<RuneCast> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "rune_cast"));
    public static final StreamCodec<ByteBuf, RuneCast> CODEC =
        StreamCodec.of(RuneCast::encode, RuneCast::decode);

    public RuneCast {
        if (glyphs.isEmpty() || glyphs.size() > AonDrawing.MAX_GLYPHS) {
            throw new IllegalArgumentException("Invalid rune glyph count");
        }
        glyphs = glyphs.stream().map(AonDrawing::copyStrokes).toList();
    }

    private static void encode(ByteBuf buffer, RuneCast payload) {
        buffer.writeByte(payload.glyphs.size());
        for (var glyph : payload.glyphs) {
            writeGlyph(buffer, glyph);
        }
    }

    private static void writeGlyph(ByteBuf buffer, List<List<Point>> strokes) {
        buffer.writeByte(strokes.size());
        for (List<Point> stroke : strokes) {
            buffer.writeByte(stroke.size());
            for (Point point : stroke) {
                buffer.writeShort((int) Math.round(point.x() * 65535));
                buffer.writeShort((int) Math.round(point.y() * 65535));
            }
        }
    }

    private static RuneCast decode(ByteBuf buffer) {
        int glyphCount = buffer.readUnsignedByte();
        if (glyphCount < 1 || glyphCount > AonDrawing.MAX_GLYPHS) {
            throw new DecoderException("Invalid rune glyph count");
        }
        var glyphs = new ArrayList<List<List<Point>>>(glyphCount);
        for (int index = 0; index < glyphCount; index++) {
            glyphs.add(readGlyph(buffer));
        }
        return new RuneCast(glyphs);
    }

    private static List<List<Point>> readGlyph(ByteBuf buffer) {
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
        return drawing;
    }

    @Override
    public Type<RuneCast> type() {
        return TYPE;
    }
}
