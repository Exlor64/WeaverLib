package dev.weaverlib.client;

/** Model-space wing skeleton, in blocks. +Y is down and -Z is in front of the chest. */
public final class AngelWingMotion {
    public record Point(double x, double y, double z) {
        Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point scale(double s) { return new Point(x * s, y * s, z * s); }
        Point blend(Point p, double t) { return scale(1 - t).add(p.scale(t)); }
    }

    public record Joints(Point root, Point elbow, Point wrist, Point tip, double recovery, double guard) {
        /** Curved, feathered surface following the articulated bones, rather than a rigid panel. */
        public Point surface(double span, double trail) {
            double s = Math.clamp(span, 0, 1) * 3;
            int i = Math.min(2, (int) s);
            double t = s - i, t2 = t * t, t3 = t2 * t;
            Point a = i == 0 ? root : i == 1 ? root : elbow;
            Point b = i == 0 ? root : i == 1 ? elbow : wrist;
            Point c = i == 0 ? elbow : i == 1 ? wrist : tip;
            Point d = i == 0 ? wrist : tip;
            double wa = -.5 * t + t2 - .5 * t3, wb = 1 - 2.5 * t2 + 1.5 * t3;
            double wc = .5 * t + 2 * t2 - 1.5 * t3, wd = -.5 * t2 + .5 * t3;
            double x = a.x * wa + b.x * wb + c.x * wc + d.x * wd;
            double y = a.y * wa + b.y * wb + c.y * wc + d.y * wd;
            double z = a.z * wa + b.z * wb + c.z * wc + d.z * wd;
            if (span > 1) {
                double extra = (span - 1) * 3;
                x += (tip.x - wrist.x) * extra;
                y += (tip.y - wrist.y) * extra;
                z += (tip.z - wrist.z) * extra;
            }
            double length = .62 + .24 * Math.sin(Math.min(1, span) * Math.PI);
            // Closed feathers scoop around the chest; open feathers flex on the recovery stroke.
            return clearBody(new Point(x + .06 * trail * (1 - guard), y + trail * length,
                z + (.10 - guard * .20) * trail + .10 * recovery * Math.sin(trail * Math.PI)));
        }
    }

    private AngelWingMotion() {}

    /** Hover cadence is 30% below the original rate; normal ascent/descent varies it by up to 40%. */
    public static double hoverRate(double verticalSpeed) {
        return (.29 + .88 * .13) * .70 * (1 + Math.clamp(verticalSpeed / .15, -1, 1) * .40);
    }

    public static double smooth(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** A quick extended power stroke followed by a longer, folded recovery stroke. */
    public static double stroke(double phase) {
        double cycle = phase / (Math.PI * 2);
        cycle -= Math.floor(cycle);
        return cycle < .43 ? -1 + 2 * smooth(cycle / .43) : 1 - 2 * smooth((cycle - .43) / .57);
    }

    public static Joints sample(double phase, double flight, double fast, double effort,
            double guard, double attack, int pair) {
        guard = smooth(guard);
        double wave = stroke(phase - pair * .32);
        double delayed = stroke(phase - .72 - pair * .32);
        double tipLag = stroke(phase - 1.22 - pair * .32);
        double recovery = Math.max(0, -Math.sin(phase - .2));
        double amplitude = flight * (.26 + effort * .74);
        double size = 1 - pair * .10;
        double lift = wave * amplitude;
        double spread = pair == 1 ? -16 : pair == 2 ? 18 : 0;
        double sweep = (-13 + spread) + (1 - flight) * Math.sin(phase) * 1.6 + lift * (1 - fast) * 43 + lift * fast * 8;
        double flap = -12 + (1 - flight) * Math.sin(phase - .4) * 2 + lift * (18 + fast * 37);
        double twist = amplitude * (Math.cos(phase - .4) * 12 - 5);
        Point root = new Point(.32, .17 + pair * .22, .19);
        Point elbow = root.add(rotate(new Point(.68 * size, -.16 * size, 0), flap, sweep, twist));
        Point wrist = elbow.add(rotate(new Point(.67 * size, -.07 * size, .015),
            flap + delayed * amplitude * 27 + recovery * amplitude * 23,
            sweep + recovery * amplitude * 23, twist + delayed * amplitude * 10));
        Point tip = wrist.add(rotate(new Point(.62 * size, .025, .015),
            flap + tipLag * amplitude * 38 + recovery * amplitude * 32,
            sweep + recovery * amplitude * 32, twist + tipLag * amplitude * 16));

        if (attack >= 0 && attack < 1 && guard < .1) {
            // Wind up outside the shoulder, sweep around the torso, then unfold back to rest.
            double strength = attack < .48 ? smooth(attack / .18) : 1 - smooth((attack - .48) / .52);
            double angle = Math.toRadians(-25 + 128 * smooth((attack - .12) / .36));
            Point strikeElbow = new Point(.32 + .68 * Math.cos(angle * .55), root.y - .08,
                .19 - .68 * Math.sin(angle * .55));
            Point strikeWrist = new Point(.12 + 1.38 * Math.cos(angle * .83), root.y - .14,
                .04 - 1.38 * Math.sin(angle * .83));
            Point strikeTip = new Point(2.12 * Math.cos(angle), root.y - .18,
                -2.12 * Math.sin(angle));
            elbow = elbow.blend(strikeElbow, strength);
            wrist = wrist.blend(strikeWrist, strength);
            tip = tip.blend(strikeTip, strength);
        }
        // The upper wing clears the shoulder. Elbow and wrist then curl the primaries across the front.
        elbow = elbow.blend(new Point(.78, root.y - .10, -.29), guard);
        wrist = wrist.blend(new Point(.70, root.y - .15, -.73), guard);
        tip = tip.blend(new Point(.025, root.y - .10, -.89 - pair * .035), guard);
        return new Joints(root, clearBody(elbow), clearBody(wrist), clearBody(tip), recovery * amplitude, guard);
    }

    private static Point rotate(Point p, double flap, double sweep, double twist) {
        double a = Math.toRadians(sweep), b = Math.toRadians(flap), c = Math.toRadians(twist);
        double x = p.x * Math.cos(a) - p.y * Math.sin(a);
        double y = p.x * Math.sin(a) + p.y * Math.cos(a);
        double z = -x * Math.sin(b) + p.z * Math.cos(b);
        x = x * Math.cos(b) + p.z * Math.sin(b);
        return new Point(x, y * Math.cos(c) - z * Math.sin(c), y * Math.sin(c) + z * Math.cos(c));
    }

    private static Point clearBody(Point p) {
        // Keep transitions and feather width outside the torso, including intermediate attack frames.
        if (p.y > -.3 && p.y < 1.45 && Math.abs(p.x) < .32 && Math.abs(p.z) < .25) {
            return new Point(.32, p.y, p.z);
        }
        return p;
    }
}
