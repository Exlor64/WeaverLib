package dev.weaverlib.client;

import java.util.ArrayList;
import java.util.List;

/** Fixed-tick hot-gas advection for continuous flame meshes. Coordinates are always world-space. */
public final class PyraFlamePhysics {
    public static final int LIFE = 12;
    private PyraFlamePhysics() {}

    public record Vector(double x,double y,double z) {
        public static final Vector ZERO = new Vector(0,0,0);
        public Vector add(Vector v) { return new Vector(x+v.x,y+v.y,z+v.z); }
        public Vector scale(double s) { return new Vector(x*s,y*s,z*s); }
        public Vector subtract(Vector v) { return add(v.scale(-1)); }
        public double lengthSqr() { return x*x+y*y+z*z; }
        public double dot(Vector v) { return x*v.x+y*v.y+z*v.z; }
        public Vector lerp(Vector v,double t) { return scale(1-t).add(v.scale(t)); }
        public Vector limited(double maximum) {
            double n=Math.sqrt(lengthSqr());
            return n>maximum?scale(maximum/n):this;
        }
    }
    public record Anchor(Vector position,Vector normal,Vector motion,double scale,double width,
                         double heat,boolean gold,long seed) {}
    public record Movement(Vector position,Vector velocity) {}
    public interface Collision { Movement move(Vector from,Vector to,Vector velocity); }
    public static final Collision OPEN_AIR = (from,to,velocity) -> new Movement(to,velocity);
    public record Sample(Vector position,double width,double alpha,double along) {}
    /** Smooth the rendered hot-gas centerline without changing the fixed-tick simulation. */
    public static Sample interpolate(Sample a,Sample b,Sample c,Sample d,double t) {
        double t2=t*t,t3=t2*t;
        Vector at=b.position.scale(2*t3-3*t2+1).add(c.position.scale(-2*t3+3*t2))
            .add(c.position.subtract(a.position).scale(.5*t3-t2+.5*t))
            .add(d.position.subtract(b.position).scale(.5*t3-.5*t2));
        return new Sample(at,b.width+(c.width-b.width)*t,b.alpha+(c.alpha-b.alpha)*t,
            b.along+(c.along-b.along)*t);
    }
    private record Parcel(Vector previous,Vector position,Vector velocity,int age) {}

    /** A curl field creates coherent eddies instead of unrelated per-frame random jitter. */
    public static Vector turbulence(Vector at,double time,long seed) {
        double k=2.7, phase=(seed&65535)*.0003;
        double x=at.x*k+phase,y=at.y*k+phase*.71,z=at.z*k-phase*.43;
        return new Vector(
            -Math.sin(x-time*.10)*Math.sin(y+time*.15)-Math.cos(z+time*.11)*Math.cos(x+time*.07),
            -Math.sin(y+time*.13)*Math.sin(z-time*.09)-Math.cos(x-time*.10)*Math.cos(y+time*.15),
            -Math.sin(z+time*.11)*Math.sin(x+time*.07)-Math.cos(y+time*.13)*Math.cos(z-time*.09));
    }

    public static Vector wind(Vector at,double time) {
        return new Vector(.006*Math.sin(time*.013+at.z*.19),0,.005*Math.cos(time*.011+at.x*.17));
    }

    public static final class Plume {
        private final List<Parcel> gas=new ArrayList<>(LIFE+1);
        private Anchor anchor;
        private Vector tickRoot;
        private long tick=Long.MIN_VALUE;
        public long lastSeen() { return tick; }
        public Anchor anchor() { return anchor; }

        public void capture(Anchor next,long now,Collision collision) {
            if (anchor==null || now<tick || now-tick>10 || next.position.subtract(tickRoot).lengthSqr()>64) {
                gas.clear();anchor=next;tick=now;tickRoot=next.position;
                emit(Vector.ZERO,now);
                return;
            }
            if (now>tick) {
                Vector wingMotion=next.position.subtract(tickRoot).scale(1.0/(now-tick)).limited(2.5);
                for(long step=tick+1;step<=now;step++) {
                    Vector root=tickRoot.lerp(next.position,(step-tick)/(double)(now-tick));
                    anchor=new Anchor(root,next.normal,next.motion,next.scale,next.width,next.heat,next.gold,next.seed);
                    for(int i=gas.size()-1;i>=0;i--) {
                        var parcel=gas.get(i);
                        int age=parcel.age+1;
                        double heat=Math.exp(-age/8.0);
                        Vector air=wind(parcel.position,step);
                        Vector eddy=turbulence(parcel.position,step,anchor.seed);
                        Vector velocity=air.add(parcel.velocity.subtract(air).scale(.84))
                            .add(new Vector(0,.016*heat,0))
                            .add(new Vector(eddy.x*.008,eddy.y*.003,eddy.z*.008).scale(heat));
                        Movement moved=collision.move(parcel.position,parcel.position.add(velocity),velocity);
                        if(age>LIFE || moved==null || moved.position.subtract(anchor.position).lengthSqr()
                                > 4.84*anchor.scale*anchor.scale) gas.remove(i);
                        else gas.set(i,new Parcel(parcel.position,moved.position,moved.velocity,age));
                    }
                    emit(wingMotion,step);
                }
                tick=now;tickRoot=next.position;anchor=next;
            } else anchor=next;
            // The youngest root stays welded to the moving feather between simulation ticks.
            if(!gas.isEmpty() && gas.getFirst().age==0) {
                var head=gas.getFirst();
                gas.set(0,new Parcel(anchor.position,anchor.position,head.velocity,0));
            }
        }

        private void emit(Vector wingMotion,long now) {
            Vector eddy=turbulence(anchor.position,now,anchor.seed);
            Vector velocity=new Vector(0,.065,0).add(anchor.normal.scale(.040))
                .add(anchor.motion.limited(3).scale(.22)).add(wingMotion.scale(.18))
                .add(new Vector(eddy.x*.014,eddy.y*.004,eddy.z*.014));
            gas.addFirst(new Parcel(anchor.position,anchor.position,velocity,0));
            while(gas.size()>LIFE+1)gas.removeLast();
        }

        public List<Sample> samples(double partial) {
            if(anchor==null)return List.of();
            var result=new ArrayList<Sample>(gas.size()+1);
            result.add(new Sample(anchor.position,anchor.width*.48,anchor.heat*.60,0));
            for(var parcel:gas) {
                if(parcel.age==0)continue;
                double along=parcel.age/(double)(LIFE+1);
                double width=anchor.width*(.8+.8*Math.sin(along*Math.PI))*(1-along);
                double alpha=anchor.heat*.60*Math.exp(-parcel.age/9.0)*(1-along);
                result.add(new Sample(parcel.previous.lerp(parcel.position,Math.clamp(partial,0,1)),width,alpha,along));
            }
            return result;
        }
    }
}
