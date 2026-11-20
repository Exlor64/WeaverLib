import dev.weaverlib.client.AngelFlightMath;
import dev.weaverlib.client.AngelFlightMath.Vector;
import dev.weaverlib.client.AngelWingMotion;

/** Geometry regressions for body-relative effects and wing paths; no client or graphics context. */
public final class AngelVisualRulesTest {
    private static int frames;
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-5; }
    private static boolean matches(Vector a, Vector b) {
        return near(a.x(), b.x()) && near(a.y(), b.y()) && near(a.z(), b.z());
    }
    // Independently apply the renderer's local-Y roll, X pitch, then world-Y yaw to a model vector.
    private static Vector modelDirection(Vector v, double yaw, double pitch, double roll) {
        double x = v.x() * Math.cos(roll) + v.z() * Math.sin(roll);
        double y = v.y();
        double z = -v.x() * Math.sin(roll) + v.z() * Math.cos(roll);
        double p = Math.toRadians(-90 - pitch);
        double pitchedY = y * Math.cos(p) - z * Math.sin(p);
        z = y * Math.sin(p) + z * Math.cos(p);
        double heading = Math.toRadians(180 - yaw);
        return new Vector(x * Math.cos(heading) + z * Math.sin(heading), pitchedY,
            -x * Math.sin(heading) + z * Math.cos(heading));
    }
    public static void run() {
        require(AngelWingMotion.birthProgress(-1, 0) == 0, "Growth must not start before transformation");
        require(AngelWingMotion.birthProgress(36, 0) == 1, "Base wings finish growing after 36 ticks");
        require(AngelWingMotion.birthProgress(3, 1) == 0, "Additional pairs should be staggered");
        require(AngelWingMotion.featherGrowth(.05, .35) > AngelWingMotion.featherGrowth(.95, .35),
            "Inner feathers must emerge before the primaries");
        require(AngelWingMotion.featherGrowth(1.1, 0) == 0, "No full feathers should appear at the start");
        require(AngelWingMotion.featherGrowth(1.1, 1) == 1, "Completed wings must have their full feather length");
        for (int pair = 0; pair < 3; pair++) {
            var guard = AngelWingMotion.sample(0, 0, 0, 0, 1, -1, pair);
            require(guard.tip().x() < -.25 && guard.tip().z() < -.8, "Shield tip must cross the chest centerline");
            var opposite = AngelWingMotion.sample(0, 0, 0, 0, 1, -1, pair, -1);
            require(opposite.tip().x() < -.25, "Both shield wings must cross beyond the centerline");
            require(opposite.tip().y() - guard.tip().y() > .4, "Guard wings need opposing crossing angles");
            require(Math.abs(opposite.tip().z() - guard.tip().z()) > .05, "Crossed wings need depth separation");
            require(guard.root().x() < .25 && guard.root().z() <= .17, "Wing roots must sit close to the back");
            require(guard.elbow().x() > .8 && guard.elbow().z() > .18, "Guard elbow must pass behind and outside the arm");
            for (int side : new int[]{-1, 1}) {
                var full = AngelWingMotion.sample(0, 0, 0, 0, 0, -1, pair, side);
                require(AngelWingMotion.growing(full, 1) == full, "Growth must finish in the existing full pose");
                var seed = AngelWingMotion.growing(full, 0);
                var seedTip = seed.surface(1, 0);
                require(near(seedTip.x(), full.root().x()) && near(seedTip.y(), full.root().y())
                    && near(seedTip.z(), full.root().z()), "Growth must begin at the back attachment");
                for (int step = 0; step <= 36; step++) {
                    double progress = step / 36.0;
                    var forming = AngelWingMotion.growing(full, progress);
                    require(forming.root().equals(full.root()), "Growth cannot move the wing attachment");
                    for (int u = 0; u <= 24; u++) {
                        double span = u / 22.0;
                        double feather = AngelWingMotion.featherGrowth(span, progress);
                        for (int v = 0; v <= 6; v++) {
                            var p = forming.surface(span, v / 6.0 * feather);
                            require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()),
                                "Growing wing geometry must stay finite");
                            boolean torso = p.y() > -.25 && p.y() < 1.40
                                && Math.abs(p.x()) < .26 && Math.abs(p.z()) < .13;
                            double shaftZ = p.z() - .025;
                            boolean arm = p.y() > -.06 && p.y() < .95 && Math.abs(p.x()) > .23
                                && Math.abs(p.x()) < .58 && shaftZ > -.22 && shaftZ < .22;
                            require(!torso && !arm, "Growing wings must clear the torso and arms");
                        }
                    }
                    frames++;
                }
            }
            // At the server's damage frame, the upper edge must lead the feather fan into the sweep.
            double impact = 4.0 / 12;
            var strike = AngelWingMotion.sample(0, 0, 0, 0, 0, impact, pair);
            var next = AngelWingMotion.sample(0, 0, 0, 0, 0, impact + .0001, pair);
            var edge = strike.surface(.75, 0);
            var trailing = strike.surface(.75, .8);
            var after = next.surface(.75, 0);
            double vx = after.x() - edge.x(), vy = after.y() - edge.y(), vz = after.z() - edge.z();
            double velocity = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double edgeLead = ((edge.x() - trailing.x()) * vx + (edge.y() - trailing.y()) * vy
                + (edge.z() - trailing.z()) * vz) / velocity;
            require(edgeLead > .25, "The upper wing edge must hit before the trailing feathers");
            for (int frame = 0; frame <= 60; frame++) {
                for (int mode = 0; mode < 4; mode++) {
                    double phase = frame * Math.PI / 15;
                    for (int side : new int[]{-1, 1}) {
                        var joints = AngelWingMotion.sample(phase, mode >= 2 ? 1 : 0, mode == 3 ? 1 : 0,
                            .9, mode == 0 ? frame / 60.0 : 0, mode == 1 ? frame / 60.0 : -1, pair, side);
                        for (int u = 0; u <= 48; u++) {
                            for (int v = 0; v <= 16; v++) {
                                var p = joints.surface(u / 40.0, v / 16.0 - .04);
                                require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()),
                                    "Wing vertex must be finite");
                                boolean torso = p.y() > -.25 && p.y() < 1.40
                                    && Math.abs(p.x()) < .26 && Math.abs(p.z()) < .13;
                                require(!torso, "Wing crossed torso during mode " + mode + " frame " + frame);
                                // Include sleeves and the feather shaft's .025-block surface offset.
                                double shaftZ = p.z() - .025;
                                boolean arm = p.y() > -.06 && p.y() < .95 && Math.abs(p.x()) > .23
                                    && Math.abs(p.x()) < .58 && shaftZ > -.22 && shaftZ < .22;
                                require(!arm, "Wing crossed an arm during mode " + mode + " frame " + frame);
                            }
                        }
                        frames++;
                    }
                }
            }
        }
        require(near(AngelWingMotion.stroke(-.000001), AngelWingMotion.stroke(.000001)), "Flap cycle seam");
        double minZ = 100, maxZ = -100;
        for (int frame = 0; frame < 100; frame++) {
            var joints = AngelWingMotion.sample(frame * Math.TAU / 100, 1, 1, 1, 0, -1, 0);
            minZ = Math.min(minZ, joints.tip().z()); maxZ = Math.max(maxZ, joints.tip().z());
        }
        require(maxZ - minZ > 1.5, "Fast-flight wings need a full flap, not a small rigid oscillation");
        for (double yaw : new double[]{0, 45, 90, 180, 270}) {
            for (double pitch : new double[]{-90, -60, 0, 35, 90}) {
                for (double remaining : new double[]{0, 1.5, 8, 18}) {
                    double roll = AngelFlightMath.roll(remaining);
                    var frame = AngelFlightMath.of(yaw, pitch, roll);
                    require(near(frame.forward().dot(frame.right()), 0), "Forward/right must be orthogonal");
                    require(near(frame.up().lengthSqr(), 1), "Vertical dives need a stable circular frame");
                    require(matches(modelDirection(new Vector(0, 1, 0), yaw, pitch, roll), frame.forward()),
                        "Effect axis must match the renderer's diving body axis");
                    require(matches(modelDirection(new Vector(-1, 0, 0), yaw, pitch, roll), frame.right()),
                        "Flame roll must match the player roll");
                    for (int i = 0; i < 16; i++) {
                        var point = frame.point(Vector.ZERO, .4, .55, i * Math.TAU / 16);
                        require(near(point.dot(frame.forward()), .4), "Spiral escaped its axial plane");
                        require(near(point.subtract(frame.forward().scale(.4)).lengthSqr(), .55 * .55),
                            "Spiral must encircle the diving body at a constant radius");
                    }
                    frames++;
                }
            }
        }
        require(AngelWingMotion.diveAmount(0, -1) == 0, "Level flight should keep the wings open");
        require(AngelWingMotion.diveAmount(75, .3) == 0, "Climbing should not use the dive tuck");
        require(AngelWingMotion.diveAmount(75, -1) == 1, "A steep descent should fully tuck the wings");
        for (int pair = 0; pair < 3; pair++) {
            for (int side : new int[]{-1, 1}) {
                var full = AngelWingMotion.sample(0, 1, 1, .4, 0, -1, pair, side);
                var tucked = AngelWingMotion.aerial(full, 1, 0, pair, side);
                var wrapped = AngelWingMotion.aerial(full, 0, 1, pair, side);
                require(tucked.tip().x() < .5 && tucked.tip().y() > .95,
                    "Dive tips should trail along the body, not stick out sideways");
                require(wrapped.tip().x() < 0 && wrapped.tip().z() < -.4,
                    "Dash wings must wrap across the front of the body");
                require(AngelWingMotion.aerial(full, 1, 1, pair, side).tip().equals(wrapped.tip()),
                    "Dash wrap must take priority over the dive tuck");
                for (int dive = 0; dive <= 4; dive++) {
                    for (int dash = 0; dash <= 4; dash++) {
                        var pose = AngelWingMotion.aerial(full, dive / 4.0, dash / 4.0, pair, side);
                        require(pose.root().equals(full.root()), "Aerial folding must keep roots attached");
                        for (int u = 0; u <= 24; u++) {
                            for (int v = 0; v <= 12; v++) {
                                var p = pose.surface(u / 22.0, v / 12.0);
                                require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()),
                                    "Aerial wing geometry must stay finite");
                                boolean torso = p.y() > -.25 && p.y() < 1.40
                                    && Math.abs(p.x()) < .26 && Math.abs(p.z()) < .13;
                                double shaftZ = p.z() - .025;
                                boolean arm = p.y() > -.06 && p.y() < .95 && Math.abs(p.x()) > .23
                                    && Math.abs(p.x()) < .58 && shaftZ > -.22 && shaftZ < .22;
                                require(!torso && !arm, "Aerial wing folds must clear the player");
                            }
                        }
                        frames++;
                    }
                }
            }
        }
        require(near(AngelFlightMath.channelRoll(1), AngelFlightMath.channelRoll(7)),
            "Channeled rotation must remain periodic without depending on lease expiry");
        require(Math.abs(AngelFlightMath.settleRoll(-6, 1)) < .3,
            "Dash release must unwind by the short path instead of snapping through a full turn");
        require(Math.abs(AngelFlightMath.settleRoll(1.5, 4)) < .25,
            "Dash roll should settle smoothly after release");
        require(AngelFlightMath.roll(-1) == 0, "An expired attack must not keep rolling");
        System.out.println("Passed angel visual geometry checks across " + frames + " animation and flight frames.");
    }
    public static void main(String[] args) { run(); }
}
