import dev.weaverlib.client.PyraFlamePhysics;
import dev.weaverlib.client.PyraFlamePhysics.Anchor;
import dev.weaverlib.client.PyraFlamePhysics.Vector;

/** World-up buoyancy, input-rate independence, cooling, motion drag and collision response. */
public final class PyraFlamePhysicsTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if(!value)throw new AssertionError("Flame physics check "+checks);
    }
    private static Anchor anchor(Vector position,Vector normal,Vector motion) {
        return new Anchor(position,normal,motion,1,.038,1,false,713);
    }
    public static void run() {
        for(var normal:new Vector[]{new Vector(1,0,0),new Vector(-1,0,0),new Vector(0,1,0),
                new Vector(0,-1,0),new Vector(0,0,1),new Vector(0,0,-1)}) {
            var plume=new PyraFlamePhysics.Plume();
            var input=anchor(Vector.ZERO,normal,Vector.ZERO);
            for(int tick=0;tick<30;tick++) {
                plume.capture(input,tick,PyraFlamePhysics.OPEN_AIR);
                var samples=plume.samples(1);
                check(samples.getFirst().position().equals(Vector.ZERO));
                check(samples.size()<=PyraFlamePhysics.LIFE+1);
                for(var sample:samples) {
                    check(sample.position().y()>=0); // even a downward-facing feather emits rising hot gas
                    check(Double.isFinite(sample.position().lengthSqr()));
                    check(sample.width()>0 && sample.alpha()>=0);
                }
            }
            check(plume.samples(1).getLast().position().y()>.35);
            check(plume.samples(1).getLast().alpha()<plume.samples(1).getFirst().alpha());
        }
        var once=new PyraFlamePhysics.Plume();var often=new PyraFlamePhysics.Plume();
        var input=anchor(Vector.ZERO,new Vector(0,0,1),Vector.ZERO);
        for(int tick=0;tick<40;tick++) {
            once.capture(input,tick,PyraFlamePhysics.OPEN_AIR);
            for(int frame=0;frame<12;frame++)often.capture(input,tick,PyraFlamePhysics.OPEN_AIR);
            check(once.samples(.5).equals(often.samples(.5)));
        }
        var fullRate=new PyraFlamePhysics.Plume();var lowRate=new PyraFlamePhysics.Plume();
        for(int tick=0;tick<=40;tick++) {
            fullRate.capture(input,tick,PyraFlamePhysics.OPEN_AIR);
            if(tick%4==0) {
                lowRate.capture(input,tick,PyraFlamePhysics.OPEN_AIR);
                check(fullRate.samples(1).equals(lowRate.samples(1)));
            }
        }
        var moving=new PyraFlamePhysics.Plume();
        for(int tick=0;tick<20;tick++)moving.capture(anchor(new Vector(tick*.6,0,0),new Vector(0,0,1),
            new Vector(.6,0,0)),tick,PyraFlamePhysics.OPEN_AIR);
        check(moving.samples(1).getLast().position().x()<moving.anchor().position().x());
        moving.capture(anchor(new Vector(1000,0,0),new Vector(0,0,1),Vector.ZERO),20,PyraFlamePhysics.OPEN_AIR);
        check(moving.samples(1).size()==1); // no flame bridge after teleporting
        var roof=new PyraFlamePhysics.Plume();
        PyraFlamePhysics.Collision ceiling=(from,to,velocity)->to.y()>.28
            ?new PyraFlamePhysics.Movement(new Vector(to.x(),.28,to.z()),new Vector(velocity.x(),0,velocity.z()))
            :new PyraFlamePhysics.Movement(to,velocity);
        for(int tick=0;tick<30;tick++) {
            roof.capture(input,tick,ceiling);
            for(var sample:roof.samples(1))check(sample.position().y()<=.28);
        }
        var at=new Vector(.3,.7,-.4);double e=1e-5;
        double divergence=(PyraFlamePhysics.turbulence(at.add(new Vector(e,0,0)),12,1).x()
            -PyraFlamePhysics.turbulence(at.add(new Vector(-e,0,0)),12,1).x()
            +PyraFlamePhysics.turbulence(at.add(new Vector(0,e,0)),12,1).y()
            -PyraFlamePhysics.turbulence(at.add(new Vector(0,-e,0)),12,1).y()
            +PyraFlamePhysics.turbulence(at.add(new Vector(0,0,e)),12,1).z()
            -PyraFlamePhysics.turbulence(at.add(new Vector(0,0,-e)),12,1).z())/(2*e);
        check(Math.abs(divergence)<1e-6);
        System.out.println("Passed "+checks+" buoyancy, drag, collision and frame-independence checks.");
    }
}
