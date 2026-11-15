package dev.weaverlib.client;

import dev.weaverlib.rune.AonDrawing.Point;
import dev.weaverlib.rune.AonRecognition.Template;
import java.util.List;

record RunePlacement(Template rune, Point center) {
    private static final double MIN_SPAN = .08;
    private static final double DEFAULT_SPAN = .16;

    boolean fits() {
        return maximumSpan() >= MIN_SPAN;
    }

    List<List<Point>> strokes(double dragDistance) {
        var points = rune.strokes().stream().flatMap(List::stream).toList();
        double minX = points.stream().mapToDouble(Point::x).min().orElseThrow();
        double maxX = points.stream().mapToDouble(Point::x).max().orElseThrow();
        double minY = points.stream().mapToDouble(Point::y).min().orElseThrow();
        double maxY = points.stream().mapToDouble(Point::y).max().orElseThrow();
        double span = Math.min(maximumSpan(), DEFAULT_SPAN + dragDistance * 2);
        double scale = span / Math.max(maxX - minX, maxY - minY);
        return rune.strokes().stream().map(stroke -> stroke.stream().map(point -> new Point(
            Math.clamp(center.x() + (point.x() - (minX + maxX) / 2) * scale, 0, 1),
            Math.clamp(center.y() + (point.y() - (minY + maxY) / 2) * scale, 0, 1)
        )).toList()).toList();
    }

    private double maximumSpan() {
        return 2 * Math.min(Math.min(center.x(), 1 - center.x()), Math.min(center.y(), 1 - center.y()));
    }
}
