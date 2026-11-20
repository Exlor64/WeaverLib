package dev.weaverlib.client;

/** A reversible, render-rate-independent transition between upright and fast flight. */
public record AngelFlightTransition(double from, double target, double changedAt) {
    public static final double DURATION = 8;

    public static AngelFlightTransition steady(boolean fast, double now) {
        return new AngelFlightTransition(fast ? 1 : 0, fast ? 1 : 0, now);
    }

    public double value(double now) {
        double t = Math.clamp((now - changedAt) / DURATION, 0, 1);
        return from + (target - from) * t * t * (3 - 2 * t);
    }

    public AngelFlightTransition toward(boolean fast, double now) {
        double next = fast ? 1 : 0;
        return next == target ? this : new AngelFlightTransition(value(now), next, now);
    }
}
