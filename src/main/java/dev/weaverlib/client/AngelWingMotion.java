package dev.weaverlib.client;

/** Model-space wing skeleton, in blocks. +Y is down and -Z is in front of the chest. */
public final class AngelWingMotion {
    private static final double WING_SCALE = 1.30;

    public record Point(double x, double y, double z) {
        Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point scale(double s) { return new Point(x * s, y * s, z * s); }
        Point blend(Point p, double t) { return scale(1 - t).add(p.scale(t)); }
    }

    public record Joints(Point root, Point elbow, Point wrist, Point tip, double recovery, double guard,
                         double strikeRoll, double growth, double streamline, double resting) {
        public Joints(Point root, Point elbow, Point wrist, Point tip, double recovery, double guard,
                double strikeRoll, double growth, double streamline) {
            this(root, elbow, wrist, tip, recovery, guard, strikeRoll, growth, streamline, 0);
        }
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
            double dx = (.06 * (1 - guard) * (1 - streamline) - .14 * streamline) * trail;
            double dy = trail * length * (1 - streamline * .25);
            double dz = ((.10 - guard * .20) * (1 - streamline) - .18 * streamline) * trail
                + .10 * recovery * Math.sin(trail * Math.PI) * (1 - streamline);
            // Resting feathers lie down the back rather than adopting the forward-curling dash fan.
            dz += (trail * .20 - dz) * resting;
            if (strikeRoll > 0) {
                // Roll the feather fan around its curved leading edge. The shoulder stays anchored;
                // the outer wing turns edge-first, with the feathers trailing behind the strike.
                double da = -.5 + 2 * t - 1.5 * t2, db = -5 * t + 4.5 * t2;
                double dc = .5 + 4 * t - 4.5 * t2, dd = -t + 1.5 * t2;
                double tx = a.x * da + b.x * db + c.x * dc + d.x * dd;
                double ty = a.y * da + b.y * db + c.y * dc + d.y * dd;
                double tz = a.z * da + b.z * db + c.z * dc + d.z * dd;
                double inverse = 1 / Math.sqrt(Math.max(1.0E-12, tx * tx + ty * ty + tz * tz));
                tx *= inverse; ty *= inverse; tz *= inverse;
                double angle = strikeRoll * smooth(span / .22);
                double cosine = Math.cos(angle), sine = Math.sin(angle);
                double along = (tx * dx + ty * dy + tz * dz) * (1 - cosine);
                double rolledX = dx * cosine + (ty * dz - tz * dy) * sine + tx * along;
                double rolledY = dy * cosine + (tz * dx - tx * dz) * sine + ty * along;
                dz = dz * cosine + (tx * dy - ty * dx) * sine + tz * along;
                dx = rolledX; dy = rolledY;
            }
            // Enlarge around the attachment, then apply body/arm clearance at the final size.
            // This keeps the roots close to the back in both first- and third-person views.
            Point enlarged = new Point(root.x + (x + dx - root.x) * WING_SCALE,
                root.y + (y + dy - root.y) * WING_SCALE, root.z + (z + dz - root.z) * WING_SCALE);
            return clearArms(clearBody(enlarged), span);
        }
    }

    private AngelWingMotion() {}

    public static double birthProgress(double elapsedTicks, int pair) {
        return Math.clamp((elapsedTicks - Math.max(0, pair) * 3) / 36, 0, 1);
    }

    public static double featherGrowth(double span, double growth) {
        if (growth >= 1) return 1;
        return smooth((growth - .04 - Math.clamp(span, 0, 1.2) * .50) / .32);
    }

    /** Bones lengthen from the back first; wrists unfold as the last primaries take shape. */
    public static Joints growing(Joints target, double progress) {
        double p = Math.clamp(progress, 0, 1);
        if (p >= 1) return target;
        double folded = 1 - smooth((p - .15) / .85);
        Point upper = target.elbow.add(target.root.scale(-1));
        Point fore = target.wrist.add(target.elbow.scale(-1));
        Point outer = target.tip.add(target.wrist.scale(-1));
        Point elbow = target.root.add(rotate(upper, -65 * folded, 20 * folded, 0).scale(smooth(p / .58)));
        Point wrist = elbow.add(rotate(fore, -72 * folded, 24 * folded, 0).scale(smooth((p - .10) / .70)));
        Point tip = wrist.add(rotate(outer, -80 * folded, 28 * folded, 0).scale(smooth((p - .25) / .75)));
        return new Joints(target.root, elbow, wrist, tip, target.recovery, target.guard, target.strikeRoll, p,
            target.streamline, target.resting);
    }

    public static double diveAmount(double pitch, double verticalSpeed) {
        return smooth((pitch - 10) / 55) * smooth((-verticalSpeed - .02) / .40);
    }

    /** Fold along the spine for a dive, or curl around the body for a held spinning charge. */
    public static Joints aerial(Joints target, double dive, double dash, int pair, int side) {
        dive = smooth(dive);
        dash = smooth(dash);
        Point root = target.root;
        Point elbow = target.elbow.blend(new Point(.53, root.y + .14, .29 + pair * .02), dive);
        Point wrist = target.wrist.blend(new Point(.46, .72 + pair * .06, .34 + pair * .02), dive);
        Point tip = target.tip.blend(new Point(.33, 1.02 + pair * .04, .38 + pair * .025), dive);
        elbow = elbow.blend(new Point(.57, root.y + .05, .16), dash);
        wrist = wrist.blend(new Point(.47, .18 + pair * .12, -.38 - side * .02), dash);
        tip = tip.blend(new Point(-.25, .26 + pair * .12 + side * .05, -.50 - side * .035), dash);
        return new Joints(root, elbow, wrist, tip, target.recovery * (1 - Math.max(dive, dash)),
            target.guard, target.strikeRoll, target.growth, Math.max(dive, dash), target.resting);
    }

    /** Fold the elbow, wrist and fan behind the back; root and feather/body clearance remain shared. */
    public static Joints resting(Joints target, double amount, double phase, int pair) {
        double combat = Math.max(target.guard, Math.min(1, target.strikeRoll / Math.toRadians(80)));
        double fold = smooth(amount) * (1 - combat);
        if (fold <= 0) return target;
        double breathe = Math.sin(phase * .65 - pair * .3) * .012;
        Point root = target.root;
        Point elbow = new Point(.62 + pair * .04, root.y - .05 + breathe, .40 + pair * .06);
        Point wrist = new Point(.45 + pair * .07, .40 + pair * .09 + breathe, .56 + pair * .06);
        Point tip = new Point(.31 + pair * .10, .72 + pair * .03 + breathe, .57 + pair * .06);
        return new Joints(root, target.elbow.blend(elbow, smooth(fold / .80)),
            target.wrist.blend(wrist, smooth((fold - .07) / .85)), target.tip.blend(tip, smooth((fold - .14) / .86)),
            target.recovery * (1 - fold), target.guard, target.strikeRoll,
            target.growth, Math.max(target.streamline, fold), fold);
    }

    /** Hover cadence is 44% below the original rate (30% reduction, then another 20%); normal ascent/descent varies it by up to 40%. */
    public static double hoverRate(double verticalSpeed) {
        return (.29 + .88 * .13) * .56 * (1 + Math.clamp(verticalSpeed / .15, -1, 1) * .40);
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
        return sample(phase, flight, fast, effort, guard, attack, pair, 1);
    }

    public static Joints sample(double phase, double flight, double fast, double effort,
            double guard, double attack, int pair, int side) {
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
        Point root = new Point(.22, .17 + pair * .22, .16);
        Point elbow = root.add(rotate(new Point(.68 * size, -.16 * size, 0), flap, sweep, twist));
        Point wrist = elbow.add(rotate(new Point(.67 * size, -.07 * size, .015),
            flap + delayed * amplitude * 27 + recovery * amplitude * 23,
            sweep + recovery * amplitude * 23, twist + delayed * amplitude * 10));
        Point tip = wrist.add(rotate(new Point(.62 * size, .025, .015),
            flap + tipLag * amplitude * 38 + recovery * amplitude * 32,
            sweep + recovery * amplitude * 32, twist + tipLag * amplitude * 16));

        double strikeRoll = 0;
        if (attack >= 0 && attack < 1 && guard < .1) {
            // Wind up outside the shoulder, sweep around the torso, then unfold back to rest.
            double strength = attack < .48 ? smooth(attack / .18) : 1 - smooth((attack - .48) / .52);
            // Fully twist before the four-tick damage frame, then unwind with the recovery.
            strikeRoll = Math.toRadians(80) * strength;
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
        // Curl around the shoulders, then cross the centerline at opposing angles. A small depth
        // separation layers the two wings instead of letting their luminous surfaces intersect.
        // The elbow first swings behind and outside the entire arm, not through the shoulder.
        // Only the outer forewing turns forward to cross in front of the chest.
        elbow = elbow.blend(new Point(.88, root.y - .10, .22), guard);
        wrist = wrist.blend(new Point(.72, root.y - .15 - side * .11, -.78), guard);
        tip = tip.blend(new Point(-.34, root.y + .02 - side * .28, -.92 - pair * .035 - side * .04), guard);
        return new Joints(root, clearBody(elbow), clearBody(wrist), clearBody(tip), recovery * amplitude, guard,
            strikeRoll, 1, 0);
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
        // A close attachment behind the actual torso is allowed; forward-sweeping feathers still
        // clear its sides. The back margin also allows for the feather's raised luminous shaft.
        if (p.y > -.3 && p.y < 1.45 && Math.abs(p.x) < .32 && p.z > -.25 && p.z < .15) {
            return new Point(.32, p.y, p.z);
        }
        return p;
    }

    private static Point clearArms(Point p, double span) {
        // Include arms and sleeves, plus clearance for the feather's raised luminous shaft.
        // Keep root feathers behind the arm, the elbow outside it, and crossed primaries in front.
        // Applying the same clearance at rest avoids a jump as blocking starts or finishes.
        double x = Math.abs(p.x);
        if (p.y > -.12 && p.y < 1.02 && x > .23 && x < .64 && p.z > -.32 && p.z < .30) {
            if (span <= 1.0 / 3) return new Point(p.x, p.y, .34);
            if (span < .56) return new Point(Math.copySign(.70, p.x), p.y, p.z);
            return new Point(p.x, p.y, -.38);
        }
        return p;
    }
}
