import dev.weaverlib.angel.PyraCharge;

/** Paid charging, input leases and unbounded combat scaling, without Minecraft dependencies. */
public final class PyraRulesTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Pyra check " + checks);
    }
    private static boolean equal(double a, double b) { return Math.abs(a-b)<1e-9; }

    public static void run() {
        check(equal(PyraCharge.MELEE_DAMAGE,6.75));
        check(equal(PyraCharge.PER_TICK*20,25));
        check(!PyraCharge.NONE.held(0));
        var charge=PyraCharge.begin(100);
        check(charge.held(100) && charge.held(109) && !charge.held(110));
        check(equal(charge.invested(),5));
        check(equal(charge.nextCost(100),0));
        check(charge.paid(100).equals(charge));
        check(equal(charge.invested(),5)); // a failed payment leaves the immutable charge untouched
        check(charge.renew(110).equals(PyraCharge.NONE));
        double lastDamage=PyraCharge.damage(5),lastRadius=PyraCharge.radius(5),lastRange=PyraCharge.range(5);
        for (long now=101;now<=4100;now++) {
            if (now%4==0) charge=charge.renew(now);
            check(charge.held(now));
            check(equal(charge.nextCost(now),1.25));
            double before=charge.invested();
            charge=charge.paid(now);
            check(equal(charge.invested(),before+1.25));
            check(charge.paid(now).equals(charge));
            check(PyraCharge.damage(charge.invested())>lastDamage);
            check(PyraCharge.radius(charge.invested())>lastRadius);
            check(PyraCharge.range(charge.invested())>lastRange);
            lastDamage=PyraCharge.damage(charge.invested());
            lastRadius=PyraCharge.radius(charge.invested());
            lastRange=PyraCharge.range(charge.invested());
        }
        check(equal(charge.invested(),5005));
        check(charge.started()==100);
        check(PyraCharge.damage(200)>PyraCharge.damage(100));
        check(PyraCharge.orbRadius(1000)>PyraCharge.orbRadius(100));
        check(equal(PyraCharge.power(Double.NaN),0));
        check(equal(PyraCharge.power(-1),0));
        check(!charge.held(charge.until()));
        check(charge.renew(charge.until()).equals(PyraCharge.NONE));
        check(equal(charge.nextCost(charge.until()),0));
        check(charge.paid(charge.until()).equals(charge));
        check(!PyraCharge.begin(100).held(99));
        System.out.println("Passed " + checks + " Pyra charging and uncapped scaling checks.");
    }
}
