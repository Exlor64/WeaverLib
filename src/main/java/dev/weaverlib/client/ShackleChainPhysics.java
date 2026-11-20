package dev.weaverlib.client;

/** Client-side constrained chain. World-space inertia and gravity never rotate with the player model. */
public final class ShackleChainPhysics {
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point scale(double s) { return new Point(x * s, y * s, z * s); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public Point lerp(Point p, double t) { return add(p.subtract(this).scale(t)); }
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    @FunctionalInterface public interface Collision {
        Point project(Point point, double radius);
    }
    public static final Collision EMPTY = (p, r) -> p;
    public static final int LINKS = 6;
    private static final Point ZERO = new Point(0, 0, 0);
    private final Point[] points = new Point[LINKS + 1], velocities = new Point[LINKS + 1];
    private final double length;
    private double time = Double.NaN;
    private Point anchor;

    public ShackleChainPhysics(double linkLength) {
        if (!Double.isFinite(linkLength) || linkLength <= 0) throw new IllegalArgumentException("Link length");
        length = linkLength;
    }
    public Point point(int i) { return points[i]; }
    public double linkLength() { return length; }

    public void advance(double now, Point nextAnchor, Point airflow, Collision collision) {
        if (!Double.isFinite(now) || !nextAnchor.finite() || !airflow.finite()) return;
        if (!Double.isFinite(time) || now < time || now - time > 5
                || anchor.subtract(nextAnchor).length() > 3) {
            reset(now, nextAnchor);
            return;
        }
        double elapsed = now - time;
        if (elapsed < 1e-6) return; // Repeated render passes must not advance or reinject momentum.
        int steps = Math.max(1, (int) Math.ceil(elapsed / .2));
        double h = elapsed / steps;
        Point oldAnchor = anchor;
        for (int step = 1; step <= steps; step++) {
            Point pin = oldAnchor.lerp(nextAnchor, step / (double) steps);
            Point[] before = points.clone();
            points[0] = pin;
            double damping = Math.exp(-.055 * h);
            for (int i = 1; i <= LINKS; i++) {
                // Relative air drag lets chains trail behind fast flight rather than oscillating on a timer.
                Point acceleration = new Point(0, -.045, 0).add(airflow.subtract(velocities[i]).scale(.025));
                velocities[i] = velocities[i].scale(damping).add(acceleration.scale(h));
                points[i] = points[i].add(velocities[i].scale(h));
            }
            for (int iteration = 0; iteration < 18; iteration++) {
                for (int i = 1; i <= LINKS; i++) {
                    Point delta = points[i].subtract(points[i - 1]);
                    double distance = delta.length();
                    if (distance < 1e-9) delta = new Point(0, -length, 0);
                    else delta = delta.scale((distance - length) / distance);
                    if (i == 1) points[i] = points[i].subtract(delta);
                    else {
                        points[i - 1] = points[i - 1].add(delta.scale(.5));
                        points[i] = points[i].subtract(delta.scale(.5));
                    }
                }
                for (int i = 1; i <= LINKS; i++) points[i] = collision.project(points[i], length * .15);
                points[0] = pin;
            }
            for (int i = 1; i <= LINKS; i++) {
                velocities[i] = points[i].subtract(before[i]).scale(1 / h);
                double speed = velocities[i].length();
                if (speed > 1.5) velocities[i] = velocities[i].scale(1.5 / speed);
                if (!points[i].finite()) { reset(now, nextAnchor); return; }
            }
        }
        time = now;
        anchor = nextAnchor;
    }

    private void reset(double now, Point pin) {
        for (int i = 0; i <= LINKS; i++) {
            points[i] = pin.add(new Point(0, -i * length, 0));
            velocities[i] = ZERO;
        }
        time = now;
        anchor = pin;
    }

    /** Rounded body collision; the closest point is on the posed limb, including in diving/rolling flight. */
    public static Point outsideCapsule(Point p, Point a, Point b, double radius) {
        Point axis = b.subtract(a);
        double square = axis.dot(axis);
        double t = square < 1e-12 ? 0 : Math.clamp(p.subtract(a).dot(axis) / square, 0, 1);
        Point center = a.add(axis.scale(t)), delta = p.subtract(center);
        double distance = delta.length();
        if (distance >= radius) return p;
        return center.add(distance < 1e-9 ? new Point(radius, 0, 0) : delta.scale(radius / distance));
    }
}
