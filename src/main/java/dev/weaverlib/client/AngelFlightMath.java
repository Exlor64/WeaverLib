package dev.weaverlib.client;

/** The same yaw, pitch and longitudinal roll used by the diving player renderer. */
public record AngelFlightMath(Vector forward, Vector right, Vector up) {
    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
        public Vector add(Vector v) { return new Vector(x + v.x, y + v.y, z + v.z); }
        public Vector subtract(Vector v) { return new Vector(x - v.x, y - v.y, z - v.z); }
        public Vector scale(double s) { return new Vector(x * s, y * s, z * s); }
        public double dot(Vector v) { return x * v.x + y * v.y + z * v.z; }
        public double lengthSqr() { return dot(this); }
        public Vector cross(Vector v) {
            return new Vector(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
        }
    }

    public static double roll(double remainingSpinTicks) {
        return Math.max(0, remainingSpinTicks) * Math.PI / 3;
    }

    public static double channelRoll(double elapsedTicks) {
        return -(Math.max(0, elapsedTicks) % 6) * Math.PI / 3;
    }

    public static double settleRoll(double angle, double elapsedTicks) {
        return Math.atan2(Math.sin(angle), Math.cos(angle)) * Math.exp(-Math.max(0, elapsedTicks) / 2);
    }

    public static AngelFlightMath of(double yaw, double pitch, double roll) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vector forward = new Vector(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vector right = new Vector(Math.cos(y), 0, Math.sin(y));
        Vector up = forward.cross(right);
        return new AngelFlightMath(forward,
            right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll))),
            up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll))));
    }

    public Vector point(Vector center, double axial, double radius, double angle) {
        return center.add(forward.scale(axial))
            .add(right.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
    }
}
