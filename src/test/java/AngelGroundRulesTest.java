import dev.weaverlib.client.AngelGroundMotion;
import dev.weaverlib.client.AngelWingMotion;
import dev.weaverlib.client.AngelFeathers;

/** Rest timing, input priority, predicted/server jump reconciliation and folded feather clearance. */
public final class AngelGroundRulesTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Ground wing check " + checks);
    }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1e-8; }
    private static AngelGroundMotion.Pose ground(AngelGroundMotion state,double time,long action,boolean combat) {
        return state.sample(time,action,-1,true,false,false,combat,0);
    }
    public static void run() {
        var state=new AngelGroundMotion();
        ground(state,0,0,false);
        for(int i=1;i<100;i++) check(ground(state,i,0,false).folded()==0);
        var first=ground(state,100,0,false);
        check(first.folded()>0 && first.folded()<.2);
        var folded=first;
        for(int i=101;i<=140;i++) folded=ground(state,i,0,false);
        check(folded.folded()>.99);
        var ready=ground(state,141,141,true);
        check(ready.folded()>0 && ready.folded()<folded.folded());
        for(int i=142;i<=148;i++) ready=ground(state,i,148,true);
        check(ready.folded()<.01);
        for(int i=149;i<248;i++) check(ground(state,i,148,false).folded()<.01);
        check(ground(state,249,148,false).folded()>.1);
        // Prediction and the server acknowledgment must produce one launch beat, not two.
        state=new AngelGroundMotion();ground(state,200,0,false);
        var jump=state.sample(201,0,-1,false,true,false,false,.42);
        check(jump.takeoff() && jump.airborne()>0 && jump.folded()<1);
        check(!state.sample(202,0,201,false,true,false,false,.33).takeoff());
        check(!state.sample(203,0,195,false,true,false,false,.25).takeoff());
        // A future-dated packet waits for the local clock instead of being silently discarded.
        state=new AngelGroundMotion();state.sample(200,0,202,false,true,false,false,0);
        check(state.sample(202,0,202,false,true,false,false,.3).takeoff());
        double lastRate=0,lastEffort=0;
        for(double speed:new double[]{-.05,-.3,-.8,-1.5,-3}) {
            state=new AngelGroundMotion();
            var falling=state.sample(0,0,-1,false,true,false,false,speed);
            for(int i=1;i<=50;i++) falling=state.sample(i,0,-1,false,true,false,false,speed);
            check(falling.rate()>=lastRate && falling.effort()>=lastEffort);
            check(falling.airborne()>.99 && falling.folded()==0);
            lastRate=falling.rate();lastEffort=falling.effort();
        }
        state=new AngelGroundMotion();
        var flight=state.sample(200,0,-1,false,false,true,false,-2);
        check(flight.folded()==0 && flight.airborne()==0);
        var wet=state.sample(201,0,-1,false,false,false,false,0);
        check(wet.airborne()==0); // No jump/fall beats in water, on ladders or while riding.
        double reference=-1;
        for(int fps:new int[]{20,60,144}) {
            state=new AngelGroundMotion();ground(state,99,0,false);
            double value=0;
            for(int i=0;i<=fps*2;i++) value=ground(state,100+i*20.0/fps,0,false).folded();
            if(reference>=0) check(near(value,reference));
            reference=value;
        }
        for(int pair=0;pair<3;pair++) for(int side:new int[]{-1,1}) {
            var open=AngelWingMotion.sample(1.7,0,0,.7,0,-1,pair,side);
            var rest=AngelWingMotion.resting(open,1,1.7,pair);
            check(rest.root().equals(open.root()));
            check(rest.tip().x()<open.tip().x() && rest.tip().z()>rest.root().z());
            for(int frame=0;frame<=20;frame++) {
                var pose=AngelWingMotion.resting(open,frame/20.0,1.7,pair);
                check(pose.root().equals(open.root()));
                for(var feather:AngelFeathers.ALL) for(int segment=0;segment<=10;segment++) {
                    double t=segment/10.0,width=AngelFeathers.width(pose,feather,t);
                    for(int edge:new int[]{-1,0,1}) {
                        var p=AngelFeathers.point(pose,feather,t,width*edge);
                        check(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
                        boolean torso=p.y()>-.25&&p.y()<1.4&&Math.abs(p.x())<.26&&Math.abs(p.z())<.13;
                        boolean arm=p.y()>-.06&&p.y()<.95&&Math.abs(p.x())>.23&&Math.abs(p.x())<.58
                            &&p.z()-.025>-.22&&p.z()-.025<.22;
                        check(!torso&&!arm);
                        if(frame==20) check(p.z()>.14);
                    }
                }
            }
            var guard=AngelWingMotion.sample(0,0,0,0,1,-1,pair,side);
            check(AngelWingMotion.resting(guard,1,0,pair).equals(guard));
            var strike=AngelWingMotion.sample(0,0,0,0,0,1.0/3,pair,side);
            check(AngelWingMotion.resting(strike,1,0,pair).equals(strike));
        }
        System.out.println("Passed " + checks + " ground-rest, jump, fall and folded-clearance checks.");
    }
}
