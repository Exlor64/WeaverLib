import dev.weaverlib.angel.CelestialRules;
import dev.weaverlib.angel.OathOfferBook;
import dev.weaverlib.angel.RetaliationLedger;
import dev.weaverlib.angel.DamoclesContract;
import java.util.UUID;

/** Consent replay/forgery, expiry, per-attacker retaliation and single-winner judgments. */
public final class CelestialRulesTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Celestial contract check "+checks);}
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-7;}
    public static void run(){
        UUID angel=UUID.randomUUID(),paladin=UUID.randomUUID(),stranger=UUID.randomUUID();
        var offers=new OathOfferBook();
        UUID offer=offers.offer(angel,paladin,100);
        check(offers.claim(offer,stranger,200)==null);
        var accepted=offers.claim(offer,paladin,200);
        check(accepted!=null && accepted.lord().equals(angel) && accepted.paladin().equals(paladin));
        check(offers.claim(offer,paladin,200)==null);
        offer=offers.offer(angel,paladin,100);check(offers.claim(offer,paladin,701)==null);
        UUID old=offers.offer(angel,paladin,200),fresh=offers.offer(stranger,paladin,250);
        check(offers.claim(old,paladin,300)==null);
        check(offers.claim(fresh,paladin,300).lord().equals(stranger));
        offer=offers.offer(angel,paladin,400);offers.prune(1001);check(offers.claim(offer,paladin,500)==null);
        var retaliation=new RetaliationLedger();
        retaliation.record(angel,4);retaliation.record(angel,7);retaliation.record(stranger,3);
        check(near(retaliation.bonus(angel),11) && near(retaliation.bonus(stranger),3));
        check(retaliation.bonus(paladin)==0 && near(retaliation.total(),14));
        check(retaliation.consume(stranger)==3 && retaliation.total()==0 && retaliation.bonus(angel)==0);
        retaliation.record(angel,50);check(retaliation.consume(paladin)==0 && retaliation.total()==0);
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0})retaliation.record(angel,bad);
        check(retaliation.total()==0);
        for(UUID first:new UUID[]{angel,paladin}){
            var pact=new DamoclesContract(angel,paladin);
            check(!pact.violate(stranger));check(pact.violate(first));
            check(!pact.violate(angel) && !pact.violate(paladin));
        }
        double previousCost=0,previousSlow=0;
        for(int volume=0;volume<=100000;volume+=17){
            double scale=CelestialRules.scale(volume),cost=CelestialRules.sealCost(scale),slow=CelestialRules.sealSlow(scale);
            check(Double.isFinite(cost) && cost>=previousCost && slow>=previousSlow && slow<=.9);
            check(near(CelestialRules.sealUpkeep(scale)*20,3*scale));
            previousCost=cost;previousSlow=slow;
        }
        check(CelestialRules.daylight(0) && CelestialRules.daylight(11999) && !CelestialRules.daylight(12000));
        check(!CelestialRules.daylight(23999) && CelestialRules.daylight(24000) && !CelestialRules.daylight(-1));
        check(CelestialRules.SEAL_TICKS==300 && CelestialRules.GROUNDS_TICKS==200);
        check(CelestialRules.SKY_TICKS==600 && CelestialRules.DAMOCLES_TICKS==600 && CelestialRules.RETALIATION_TICKS==400);
        System.out.println("Passed "+checks+" oath consent, retaliation, verdict and seal-scaling checks.");
    }
    public static void main(String[] args){run();}
}
