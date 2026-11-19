import dev.weaverlib.client.AngelFlightFrame;
import dev.weaverlib.client.AngelWingMotion;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Geometry regressions for body-relative effects and wing paths; no client or graphics context. */
public final class AngelVisualRulesTest {
    private static int frames;
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-5; }
    private static boolean matches(Vector3f a, Vec3 b) {
        return near(a.x, b.x) && near(a.y, b.y) && near(a.z, b.z);
    }
    public static void run() {
        for (int pair = 0; pair < 3; pair++) {
            var guard = AngelWingMotion.sample(0, 0, 0, 0, 1, -1, pair);
            require(guard.tip().x() < .1 && guard.tip().z() < -.8, "Shield tip must wrap across the chest");
            require(guard.elbow().x() > .7 && guard.elbow().z() < -.2, "Elbow must clear the shoulder");
            for (int frame = 0; frame <= 60; frame++) {
                for (int mode = 0; mode < 4; mode++) {
                    double phase = frame * Math.PI / 15;
                    var joints = AngelWingMotion.sample(phase, mode >= 2 ? 1 : 0, mode == 3 ? 1 : 0,
                        .9, mode == 0 ? frame / 60.0 : 0, mode == 1 ? frame / 60.0 : -1, pair);
                    for (int u = 0; u <= 48; u++) {
                        for (int v = 0; v <= 16; v++) {
                            var p = joints.surface(u / 40.0, v / 16.0 - .04);
                            require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()),
                                "Wing vertex must be finite");
                            boolean torso = p.y() > -.25 && p.y() < 1.40
                                && Math.abs(p.x()) < .26 && Math.abs(p.z()) < .17;
                            require(!torso, "Wing crossed torso during mode " + mode + " frame " + frame);
                        }
                    }
                    frames++;
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
                    double roll = AngelFlightFrame.roll(remaining);
                    var frame = AngelFlightFrame.of(yaw, pitch, roll);
                    require(near(frame.forward().dot(frame.right()), 0), "Forward/right must be orthogonal");
                    require(near(frame.up().lengthSqr(), 1), "Vertical dives need a stable circular frame");
                    Matrix4f model = new Matrix4f().rotateY((float) Math.toRadians(180 - yaw))
                        .rotateX((float) Math.toRadians(-90 - pitch)).rotateY((float) roll);
                    require(matches(model.transformDirection(new Vector3f(0, 1, 0)), frame.forward()),
                        "Effect axis must match the renderer's diving body axis");
                    require(matches(model.transformDirection(new Vector3f(-1, 0, 0)), frame.right()),
                        "Flame roll must match the player roll");
                    for (int i = 0; i < 16; i++) {
                        var point = frame.point(Vec3.ZERO, .4, .55, i * Math.TAU / 16);
                        require(near(point.dot(frame.forward()), .4), "Spiral escaped its axial plane");
                        require(near(point.subtract(frame.forward().scale(.4)).lengthSqr(), .55 * .55),
                            "Spiral must encircle the diving body at a constant radius");
                    }
                    frames++;
                }
            }
        }
        require(AngelFlightFrame.roll(-1) == 0, "An expired attack must not keep rolling");
        System.out.println("Passed angel visual geometry checks across " + frames + " animation and flight frames.");
    }
    public static void main(String[] args) { run(); }
}
