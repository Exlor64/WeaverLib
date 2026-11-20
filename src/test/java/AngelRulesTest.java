import dev.weaverlib.angel.AngelEnergy;
import dev.weaverlib.angel.AngelDash;
import dev.weaverlib.angel.AngelForms;
import dev.weaverlib.angel.AngelLevels;
import dev.weaverlib.angel.AngelPayment;
import java.util.List;

/** Run with Java 21; no game bootstrap required. */
public final class AngelRulesTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Check " + checks);
    }
    private static boolean equal(double a, double b) { return Math.abs(a - b) < 1e-8; }
    private static final class Battery implements AngelPayment.Store {
        double stored, staged;
        int commits;
        boolean refuses;
        Battery(double stored) { this.stored = stored; }
        public double available() { return stored; }
        public boolean stage(double amount) {
            staged = amount;
            return !refuses;
        }
        public void commit() { stored -= staged; commits++; }
    }
    public static void main(String[] args) {
        // Every written combination is checked at every rank, including locked and unwritten inputs.
        for (int rank = 0; rank <= 3; rank++) {
            for (int sequence = -1; sequence <= 27; sequence++) {
                int expected = rank >= 1 && sequence == 0 ? AngelForms.DIVINITY
                    : rank >= 2 && sequence == 13 ? AngelForms.INFERNO : -1;
                check(AngelForms.invocation(sequence, rank) == expected);
            }
        }
        check(AngelForms.invocation(13, 4) == -1);
        check(AngelLevels.ENTRY == 25);
        check(AngelLevels.rankAt(74) == 1);
        check(AngelLevels.rankAt(75) == 2);
        check(AngelLevels.rankAt(149) == 2);
        check(AngelLevels.rankAt(150) == 3);
        check(AngelLevels.points(73, 2) == 75);
        check(AngelLevels.points(Integer.MAX_VALUE, 4) == Integer.MAX_VALUE);
        check(AngelLevels.points(-5, -3) == 0);
        check(AngelLevels.mayBless(75, 74));
        check(!AngelLevels.mayBless(75, 75));
        check(!AngelLevels.mayBless(75, 76));
        check(AngelLevels.dayReady(0, false, 0));
        check(!AngelLevels.dayReady(12, true, 12));
        check(AngelLevels.dayReady(13, true, 12));
        check(!AngelLevels.dayReady(11, true, 12));
        check(equal(AngelEnergy.capacity(3), 350));

        // A partial reserve plus several cells can pay, consuming only the shortfall.
        var a = new Battery(125); var b = new Battery(300);
        check(equal(AngelPayment.spend(2, 5, 100, List.of(a, b), true), 0));
        check(equal(a.stored, 0) && equal(b.stored, 125));
        check(a.commits == 1 && b.commits == 1);
        // Insufficient total energy must not partially drain either cell.
        a = new Battery(50); b = new Battery(100);
        check(AngelPayment.spend(1, 3, 100, List.of(a, b), true) < 0);
        check(equal(a.stored, 50) && equal(b.stored, 100));
        check(a.commits == 0 && b.commits == 0);
        // Availability checks never debit inventory. Personal reserve needs no cells.
        check(equal(AngelPayment.spend(2, 3, 100, List.of(a, b), false), 0));
        check(equal(a.stored, 50) && equal(b.stored, 100));
        check(equal(AngelPayment.spend(5, 3, 100, List.of(a, b), true), 2));
        check(equal(a.stored, 50) && equal(b.stored, 100));
        // Fractional per-tick upkeep must not round up to whole Grace.
        a = new Battery(2);
        check(equal(AngelPayment.spend(0, AngelEnergy.upkeep(false, false), 100, List.of(a), true), 0));
        check(equal(a.stored, .75));
        // A device refusing withdrawal invalidates the entire staged payment.
        a = new Battery(50); b = new Battery(100); b.refuses = true;
        check(AngelPayment.spend(0, 1, 100, List.of(a, b), true) < 0);
        check(a.commits == 0 && b.commits == 0 && equal(a.stored, 50));
        check(AngelPayment.spend(0, Double.NaN, 100, List.of(a), true) < 0);
        check(AngelPayment.spend(0, 1, 0, List.of(a), true) < 0);
        check(AngelPayment.spend(0, 1, 100, List.of(), true) < 0);
        check(!AngelDash.NONE.active(0));
        var dash = AngelDash.begin(0);
        check(dash.active(0) && dash.active(9) && !dash.active(10));
        for (int tick = 4; tick <= 80; tick += 4) {
            dash = dash.renew(tick);
            check(dash.started() == 0 && dash.active(tick));
        }
        check(!dash.active(90));
        check(!dash.renew(90).active(90));
        check(equal(AngelDash.COST_PER_TICK * 20, 10));
        check(equal(AngelDash.START_COST, 10));
        System.out.println("Passed " + checks + " angel progression and payment checks.");
        AngelVisualRulesTest.run();
        PyraRulesTest.run();
        AngelFeatherRulesTest.run();
        PyraFlamePhysicsTest.run();
    }
}
