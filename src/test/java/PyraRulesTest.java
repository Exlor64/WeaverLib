import dev.weaverlib.angel.PyraCharge;
import dev.weaverlib.angel.PyraCombat;
import dev.weaverlib.client.PyraWingMotion;
import dev.weaverlib.client.AngelWingMotion;

/** Paid charging, input leases and unbounded combat scaling, without Minecraft dependencies. */
public final class PyraRulesTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Pyra check " + checks);
    }
    private static boolean equal(double a, double b) { return Math.abs(a-b)<1e-9; }

    public static void run() {
        check(equal(PyraCombat.MELEE_DAMAGE,15));
        check(equal(PyraCharge.PER_TICK*20,50));
        check(!PyraCharge.NONE.held(0));
        var charge=PyraCharge.begin(100);
        check(charge.held(100) && charge.held(109) && !charge.held(110));
        check(equal(charge.invested(),20));
        check(equal(charge.nextCost(100),0));
        check(charge.paid(100).equals(charge));
        check(equal(charge.invested(),20)); // a failed payment leaves the immutable charge untouched
        check(charge.renew(110).equals(PyraCharge.NONE));
        double lastDamage=PyraCharge.damage(5),lastRadius=PyraCharge.radius(5),lastRange=PyraCharge.range(5);
        for (long now=101;now<=4100;now++) {
            if (now%4==0) charge=charge.renew(now);
            check(charge.held(now));
            check(equal(charge.nextCost(now),2.5));
            double before=charge.invested();
            charge=charge.paid(now);
            check(equal(charge.invested(),before+2.5));
            check(charge.paid(now).equals(charge));
            check(PyraCharge.damage(charge.invested())>lastDamage);
            check(PyraCharge.radius(charge.invested())>lastRadius);
            check(PyraCharge.range(charge.invested())>lastRange);
            lastDamage=PyraCharge.damage(charge.invested());
            lastRadius=PyraCharge.radius(charge.invested());
            lastRange=PyraCharge.range(charge.invested());
        }
        check(equal(charge.invested(),10020));
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
        var fireball = PyraCharge.begin(100, PyraCombat.FIREBALL_START);
        check(equal(fireball.invested(),30));
        for (long tick=101;tick<=2100;tick++) {
            if (tick%4==0) fireball=fireball.renew(tick);
            double old=fireball.invested();
            fireball=fireball.paid(tick,PyraCombat.FIREBALL_PER_TICK);
            check(equal(fireball.invested(),old+3));
            check(fireball.paid(tick,3).equals(fireball));
        }
        check(equal(fireball.invested(),6030));
        check(fireball.paid(2101,Double.NaN).equals(fireball));
        check(fireball.paid(2101,-3).equals(fireball));
        check(PyraCombat.fireballDamage(1000)>PyraCombat.fireballDamage(350));
        check(PyraCombat.fireballRadius(1000)>PyraCombat.fireballRadius(350));
        check(PyraCombat.fireballOrb(1000)>PyraCombat.fireballOrb(350));
        check(equal(PyraCombat.upkeep(true,true)*20,7));
        check(equal(PyraWingMotion.slashScale(.3),1.5));
        check(equal(PyraWingMotion.slashScale(1),1));
        for (int pair=0;pair<3;pair++) {
            var open=AngelWingMotion.sample(1.2,1,0,.7,0,-1,pair);
            var strike=PyraWingMotion.slash(open,.3);
            check(strike.root().equals(open.root()));
            check(equal(strike.tip().x()-strike.root().x(),(open.tip().x()-open.root().x())*1.5));
            for (double pitch : new double[]{-85,-45,0,45,85}) {
                for (int frame=0;frame<=18;frame++) {
                    var gathered=PyraWingMotion.gather(open,frame,pair,1,0,pitch);
                    check(gathered.root().equals(open.root()));
                    for (int span=0;span<=24;span++) {
                        var point=gathered.surface(span/24.0,.8);
                        check(Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z()));
                    }
                }
                var tip=PyraWingMotion.gather(open,18,pair,1,0,pitch).surface(1,0);
                check(equal(tip.x(),.035+pair*.025));
                check(equal(tip.z(),-Math.cos(Math.toRadians(pitch))*1.8));
            }
        }
        for (int side : new int[]{-1,1}) {
            for (int yaw : new int[]{-60,-30,0,30,60}) {
                var open=AngelWingMotion.sample(1.2,1,0,.7,0,-1,0,side);
                var tip=PyraWingMotion.gather(open,18,0,side,yaw,0).surface(1,0);
                check(equal(tip.x(),side*Math.sin(Math.toRadians(yaw))*1.8+.035));
                check(equal(tip.z(),-Math.cos(Math.toRadians(yaw))*1.8));
            }
        }
        System.out.println("Passed " + checks + " Pyra charging and uncapped scaling checks.");
    }
}
