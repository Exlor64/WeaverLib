package dev.weaverlib.client;

import dev.weaverlib.rune.AonDrawing.Point;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

final class StrokeInk {
    private StrokeInk() {}

    /** Keeps the endpoints and splits the segment with the largest remaining geometric error. */
    static List<Point> reduce(List<Point> points, int limit) {
        if (points.size() <= limit) {
            return List.copyOf(points);
        }

        var kept = new BitSet(points.size());
        kept.set(0);
        kept.set(points.size() - 1);
        var segments = new PriorityQueue<>(Comparator.comparingDouble(Split::error).reversed()
            .thenComparingInt(Split::index));
        queueSplit(points, segments, 0, points.size() - 1);
        for (int count = 2; count < limit && !segments.isEmpty(); count++) {
            Split split = segments.remove();
            kept.set(split.index());
            queueSplit(points, segments, split.start(), split.index());
            queueSplit(points, segments, split.index(), split.end());
        }
        return kept.stream().mapToObj(points::get).toList();
    }

    private static void queueSplit(List<Point> points, PriorityQueue<Split> segments, int start, int end) {
        Point from = points.get(start);
        Point to = points.get(end);
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double lengthSquared = dx * dx + dy * dy;
        double largest = 1e-12;
        int furthest = -1;
        for (int i = start + 1; i < end; i++) {
            Point point = points.get(i);
            double x = point.x() - from.x();
            double y = point.y() - from.y();
            double fraction = lengthSquared == 0 ? 0 : Math.clamp((x * dx + y * dy) / lengthSquared, 0, 1);
            double errorX = x - dx * fraction;
            double errorY = y - dy * fraction;
            double error = errorX * errorX + errorY * errorY;
            if (error > largest) {
                largest = error;
                furthest = i;
            }
        }
        if (furthest >= 0) {
            segments.add(new Split(start, end, furthest, largest));
        }
    }

    /** Rounds moderate turns for display, preserving endpoints and sharp corners. */
    static List<Point> preview(List<Point> ink) {
        List<Point> points = reduce(ink, 96);
        if (points.size() < 3) {
            return points;
        }
        var path = new ArrayList<Point>();
        path.add(points.getFirst());
        for (int i = 1; i < points.size() - 1; i++) {
            Point previous = points.get(i - 1);
            Point current = points.get(i);
            Point next = points.get(i + 1);
            double ax = current.x() - previous.x();
            double ay = current.y() - previous.y();
            double bx = next.x() - current.x();
            double by = next.y() - current.y();
            double lengths = Math.hypot(ax, ay) * Math.hypot(bx, by);
            double dot = ax * bx + ay * by;
            if (lengths == 0 || dot < lengths * .55 || dot > lengths * .995) {
                path.add(current);
                continue;
            }
            Point entry = between(current, previous, .4);
            Point exit = between(current, next, .4);
            path.add(entry);
            for (int step = 1; step <= 3; step++) {
                double t = step / 3.0;
                path.add(between(between(entry, current, t), between(current, exit, t), t));
            }
        }
        path.add(points.getLast());
        return path;
    }

    private static Point between(Point a, Point b, double fraction) {
        return new Point(a.x() + fraction * (b.x() - a.x()), a.y() + fraction * (b.y() - a.y()));
    }

    private record Split(int start, int end, int index, double error) {}
}
