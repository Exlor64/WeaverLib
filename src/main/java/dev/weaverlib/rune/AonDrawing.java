package dev.weaverlib.rune;

import java.util.List;

/** Bounds shared by canvas capture, network decoding, and recognition. */
public final class AonDrawing {
    public static final int MAX_STROKES = 16;
    public static final int MAX_POINTS = 48;

    private AonDrawing() {}

    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
                throw new IllegalArgumentException("Point outside canvas");
            }
        }
    }

    public static List<List<Point>> copyStrokes(List<List<Point>> strokes) {
        if (strokes.isEmpty() || strokes.size() > MAX_STROKES) {
            throw new IllegalArgumentException("Invalid rune stroke count");
        }
        for (List<Point> stroke : strokes) {
            if (stroke.size() < 2 || stroke.size() > MAX_POINTS) {
                throw new IllegalArgumentException("Invalid rune point count");
            }
        }
        return strokes.stream().map(List::copyOf).toList();
    }
}
