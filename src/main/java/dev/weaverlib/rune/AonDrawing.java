package dev.weaverlib.rune;

import java.util.List;

/** Bounds shared by canvas capture, network decoding, and recognition. */
public final class AonDrawing {
    public static final int MAX_GLYPHS = 8;
    public static final int MAX_STROKES = 16;
    public static final int MAX_POINTS = 48;
    private static final double TOP_SLOT_TOLERANCE = Math.toRadians(5);

    private AonDrawing() {}

    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
                throw new IllegalArgumentException("Point outside canvas");
            }
        }
    }

    // AonDor orders children clockwise from twelve o'clock around their parent's ink bounds.
    static double clockwiseAngle(Point center, Point point) {
        double angle = Math.atan2(point.x() - center.x(), center.y() - point.y());
        if (Math.abs(angle) <= TOP_SLOT_TOLERANCE) {
            return 0;
        }
        return angle < 0 ? angle + Math.TAU : angle;
    }

    static Point center(List<List<Point>> glyph) {
        double minX = 1;
        double minY = 1;
        double maxX = 0;
        double maxY = 0;
        for (var stroke : glyph) {
            for (var point : stroke) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
        }
        return new Point((minX + maxX) / 2, (minY + maxY) / 2);
    }

    static List<Integer> clockwiseOrder(List<List<List<Point>>> glyphs) {
        var centers = glyphs.stream().map(AonDrawing::center).toList();
        Point root = centers.getFirst();
        var indexes = new java.util.ArrayList<Integer>();
        indexes.add(0);
        indexes.addAll(java.util.stream.IntStream.range(1, glyphs.size()).boxed()
            .sorted(java.util.Comparator.comparingDouble((Integer i) -> clockwiseAngle(root, centers.get(i)))
                .thenComparingDouble(i -> Math.hypot(centers.get(i).x() - root.x(), centers.get(i).y() - root.y())))
            .toList());
        return indexes;
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
