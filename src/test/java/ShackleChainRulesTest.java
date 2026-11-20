import dev.weaverlib.client.ShackleChainPhysics;
import dev.weaverlib.client.ShackleChainPhysics.Point;

/** Gravity, momentum, collision and discontinuity regressions without Minecraft bootstrapping. */
public final class ShackleChainRulesTest {
    private static int checks;
    private static final Point ZERO = new Point(0, 0, 0);
    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void links(ShackleChainPhysics chain, Point anchor, double error) {
        require(chain.point(0).subtract(anchor).length() < 1e-7, "Cuff anchor detached");
        for (int i = 1; i <= ShackleChainPhysics.LINKS; i++) {
            require(chain.point(i).finite(), "Invalid chain node");
            require(Math.abs(chain.point(i).subtract(chain.point(i - 1)).length() - chain.linkLength()) < error,
                "Chain stretched: " + chain.point(i).subtract(chain.point(i - 1)).length());
        }
    }
    public static void run() {
        for (int fps : new int[]{30, 60, 144}) {
            ShackleChainPhysics chain = new ShackleChainPhysics(.165);
            Point pin = new Point(0, 2, 0);
            chain.advance(0, pin, ZERO, ShackleChainPhysics.EMPTY);
            for (int frame = 1; frame <= fps * 12; frame++) {
                double t = frame * 20.0 / fps;
                // Moving and rotating cuff path, then stop abruptly and allow it to settle.
                pin = t < 100 ? new Point(Math.sin(t * .08) * .4, 2 + Math.cos(t * .06) * .12,
                    Math.cos(t * .08) * .4) : new Point(Math.sin(8) * .4, 2 + Math.cos(6) * .12, Math.cos(8) * .4);
                chain.advance(t, pin, ZERO, ShackleChainPhysics.EMPTY);
                links(chain, pin, .022);
            }
            require(Math.abs(chain.point(6).x() - pin.x()) < .035, "Swing did not damp toward rest");
            require(chain.point(6).y() < pin.y() - .94, "Stationary chain must hang under world gravity");
            Point before = chain.point(6);
            chain.advance(240, pin, ZERO, ShackleChainPhysics.EMPTY);
            require(chain.point(6).equals(before), "Duplicate render pass advanced physics");
            // A teleport starts a local chain, without dragging it across the world.
            pin = new Point(20000, 180, -30000);
            chain.advance(241, pin, ZERO, ShackleChainPhysics.EMPTY);
            links(chain, pin, 1e-7);
            chain.advance(260, pin, ZERO, ShackleChainPhysics.EMPTY);
            links(chain, pin, 1e-7);
        }
        ShackleChainPhysics floor = new ShackleChainPhysics(.135);
        Point pin = new Point(0, .3, 0);
        ShackleChainPhysics.Collision ground = (p, r) -> new Point(p.x(), Math.max(r, p.y()), p.z());
        floor.advance(0, pin, ZERO, ground);
        for (int frame = 1; frame <= 600; frame++) {
            // Slight horizontal movement makes a chain with excess length settle along the floor.
            Point moving = pin.add(new Point(Math.sin(frame * .015) * .12, 0, 0));
            floor.advance(frame / 3.0, moving, ZERO, ground);
            for (int i = 1; i <= 6; i++) require(floor.point(i).y() >= .135 * .15 - 1e-8, "Floor penetration");
            links(floor, moving, .06);
        }
        Point a = new Point(0, 0, 0), b = new Point(0, 1, 0);
        for (int i = 0; i <= 100; i++) {
            Point p = new Point(.03, i / 100.0, .01);
            Point out = ShackleChainPhysics.outsideCapsule(p, a, b, .2);
            require(Math.abs(Math.hypot(out.x(), out.z()) - .2) < 1e-7, "Limb collision failed");
        }
        require(ShackleChainPhysics.outsideCapsule(new Point(1, .5, 0), a, b, .2).equals(new Point(1, .5, 0)),
            "Collision displaced an outside node");
        System.out.println("Passed " + checks + " shackle chain physics checks.");
    }
    public static void main(String[] args) { run(); }
}
