import dev.weaverlib.angel.AngelForms;
import dev.weaverlib.angel.AngelSpellRules;

/** Invocation order, exact heal timing, wave lifetime and partial-stack charging contracts. */
public final class AngelSpellRulesTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Angel spell check " + checks);
    }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1e-9; }
    public static void run() {
        int[] codes={1,4,3,9,12,10,2,24,18,5,19,17,15,7}, costs={15,25,20,15,35,8,40,100,75,40,200,60,150,60};
        for(int rank=0;rank<=4;rank++) {
            int known=0;
            for(int sequence=-1;sequence<=27;sequence++) {
                int expected=-1;
                for(int i=0;i<codes.length;i++) if(sequence==codes[i] && (i<6 ? rank>=2 && rank<=3 : rank==3)) expected=i;
                check(AngelSpellRules.spell(sequence,rank)==expected);
                if(sequence>=0 && sequence<27 &&
                        (AngelSpellRules.spell(sequence,rank)>=0 || AngelForms.invocation(sequence,rank)>=0)) known++;
            }
            check(known==(rank==1?1:rank==2?8:rank==3?17:0));
        }
        for(int i=0;i<costs.length;i++) check(AngelSpellRules.cost(i)==costs[i]);
        check(AngelSpellRules.smiteDamage(1)==10);
        check(AngelSpellRules.smiteDamage(2)==20);
        check(AngelSpellRules.smiteDamage(3)==30);
        int pulses=0;
        for(int age=-20;age<=120;age++) {
            boolean wanted=age==20||age==40||age==60||age==80;
            check(AngelSpellRules.healingPulse(age)==wanted);
            if(AngelSpellRules.healingPulse(age)) pulses++;
        }
        check(pulses==4 && near(pulses*AngelSpellRules.HEAL_AMOUNT,8));
        check(near(AngelSpellRules.CIRCLE_DAMAGE,8));
        double previous=0;
        for(int age=0;age<20;age++) {
            check(AngelSpellRules.circleActive(age));
            double radius=AngelSpellRules.circleRadius(age);
            check(radius>=previous && radius<=6);previous=radius;
        }
        check(!AngelSpellRules.circleActive(-1) && !AngelSpellRules.circleActive(20));
        check(near(AngelSpellRules.circleRadius(10),6));
        check(near(AngelSpellRules.waveRadius(0),0) && near(AngelSpellRules.waveRadius(12),6));
        check(AngelSpellRules.reducedWarp(0,3)==0);
        check(AngelSpellRules.reducedWarp(2,3)==0);
        check(AngelSpellRules.reducedWarp(8,3)==5);
        check(AngelSpellRules.reducedWarp(1,1)==0);
        check(AngelSpellRules.forgeCount(0,cost->{throw new AssertionError("Empty stack must not inspect fuel");})==0);
        for(int items=1;items<=64;items++) for(int grace=0;grace<=520;grace++) {
            final int budget=grace;
            int count=AngelSpellRules.forgeCount(items,cost->cost<=budget);
            check(count==Math.min(items,grace/8));
        }
        check(AngelSpellRules.forgeCount(Integer.MAX_VALUE,cost->cost<=8)==1);
        System.out.println("Passed " + checks + " ordered invocation, healing, area and forge-cost checks.");
    }
}
