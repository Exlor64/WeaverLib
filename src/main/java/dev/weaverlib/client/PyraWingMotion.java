package dev.weaverlib.client;

/** Pyra's aerial attacks share the articulated feather skeleton and keep every shoulder anchored. */
public final class PyraWingMotion {
    private PyraWingMotion() {}

    public static double slashScale(double phase) {
        if (phase < 0 || phase >= 1) return 1;
        double envelope = phase < .38 ? AngelWingMotion.smooth(phase / .22)
            : 1 - AngelWingMotion.smooth((phase - .38) / .62);
        return 1 + .5 * envelope;
    }

    public static AngelWingMotion.Joints slash(AngelWingMotion.Joints j, double phase) {
        double scale = slashScale(phase);
        return new AngelWingMotion.Joints(j.root(), stretch(j.root(), j.elbow(), scale),
            stretch(j.root(), j.wrist(), scale), stretch(j.root(), j.tip(), scale),
            j.recovery(), j.guard(), j.strikeRoll(), j.growth(), j.streamline());
    }
    private static AngelWingMotion.Point stretch(AngelWingMotion.Point root, AngelWingMotion.Point p, double size) {
        return new AngelWingMotion.Point(root.x() + (p.x() - root.x()) * size,
            root.y() + (p.y() - root.y()) * size, root.z() + (p.z() - root.z()) * size);
    }
    private static AngelWingMotion.Point blend(AngelWingMotion.Point a, AngelWingMotion.Point b, double t) {
        return new AngelWingMotion.Point(a.x() + (b.x() - a.x()) * t,
            a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
    }

    /** Elbows go outside the arms first, followed by wrists and tips; the last tips gather in front. */
    public static AngelWingMotion.Joints gather(AngelWingMotion.Joints j, double elapsed, int pair,
            int side, double yaw, double pitch) {
        double p = AngelWingMotion.smooth(elapsed / 18);
        var root = j.root();
        double radians = Math.toRadians(pitch);
        double reach = 1.8;
        double heading = Math.toRadians(yaw);
        // Surface() enlarges from the shoulder by 1.30, so compensate to place the real tips together.
        var goal = new AngelWingMotion.Point(side * Math.sin(heading) * Math.cos(radians) * reach + .035 + pair * .025,
            -.12 + Math.sin(radians) * reach, -Math.cos(heading) * Math.cos(radians) * reach);
        var tip = stretch(root, goal, 1 / 1.30);
        var elbow = new AngelWingMotion.Point(.88, root.y() - .10, .25);
        var wrist = new AngelWingMotion.Point(.65, tip.y() + .06, tip.z() * .65);
        return new AngelWingMotion.Joints(root, blend(j.elbow(), elbow, AngelWingMotion.smooth(elapsed / 10)),
            blend(j.wrist(), wrist, AngelWingMotion.smooth((elapsed - 3) / 14)), blend(j.tip(), tip, p),
            j.recovery() * (1 - p), 0, j.strikeRoll() * (1 - p), j.growth(), p);
    }
}
