package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.angel.PyraCharge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Animated nested flame volumes: a cradled charge, a hot laser core and a rolling flamethrower envelope. */
final class PyraBeamVisual {
    private PyraBeamVisual() {}

    static void charge(VertexConsumer buffer, PoseStack.Pose pose, Player player, Vec3 camera, float partial) {
        var charge = Angels.charge(player);
        if (!charge.active()) return;
        double time = player.level().getGameTime() + partial;
        double power = PyraCharge.power(charge.energy());
        double radius = PyraCharge.orbRadius(charge.energy()) * player.getScale();
        Vec3 center = Angels.pyraHands(player, partial).subtract(camera)
            .add(player.getViewVector(partial).scale(Math.max(0, radius - .15 * player.getScale())));
        var frame = AngelFlightFrame.of(player.getViewYRot(partial),player.getViewXRot(partial),0);
        sphere(buffer,pose,center,radius*.64,time,PyraFire.CORE,.78);
        sphere(buffer,pose,center,radius,time,PyraFire.WING,.68);
        sphere(buffer,pose,center,radius*1.16,time+4,PyraFire.WING,.24);
        for (int strand=0;strand<4;strand++) {
            for (int segment=0;segment<24;segment++) {
                double a=segment/24.0,b=(segment+1)/24.0;
                double angleA=strand*Math.TAU/4 + a*5 - time*.13;
                double angleB=strand*Math.TAU/4 + b*5 - time*.13;
                double axialA=(a-.5)*radius*2.8,axialB=(b-.5)*radius*2.8;
                double rA=radius*(.45+.8*Math.sin(a*Math.PI)),rB=radius*(.45+.8*Math.sin(b*Math.PI));
                double width=.13+.07*power/(1+power);
                PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                    frame.point(center,axialA,rA,angleA-width),frame.point(center,axialA,rA,angleA+width),
                    frame.point(center,axialB,rB,angleB+width),frame.point(center,axialB,rB,angleB-width),
                    0,a,1,b,.7*Math.sin(a*Math.PI),.7*Math.sin(b*Math.PI));
            }
        }
    }

    static void beam(VertexConsumer buffer, PoseStack.Pose pose, AngelBurst burst, Vec3 camera,
            double age, double duration, double time) {
        Vec3 start=burst.from().subtract(camera),end=burst.to().subtract(camera);
        Vec3 delta=end.subtract(start);
        double length=delta.length(),power=PyraCharge.power(burst.power());
        double fade=AngelWingMotion.smooth((duration-age)/5);
        if (length<.06) {
            sphere(buffer,pose,start,.18+power*.25,time,PyraFire.WING,fade*.8);
            return;
        }
        Vec3 forward=delta.normalize();
        Vec3 right=forward.cross(new Vec3(0,1,0));
        if (right.lengthSqr()<.001) right=forward.cross(new Vec3(1,0,0));
        right=right.normalize();
        var frame=new AngelFlightFrame(forward,right,forward.cross(right).normalize());
        double radius=PyraCharge.radius(burst.power());
        int sections=Math.clamp((int)Math.ceil(length*2),8,72);
        for (int shell=0;shell<3;shell++) {
            double size=shell==0?.23:shell==1?.72:1;
            int role=shell==0?PyraFire.CORE:PyraFire.JET;
            double opacity=fade*(shell==0?.68:shell==1?.54:.28);
            for (int segment=0;segment<sections;segment++) {
                double a=segment/(double)sections,b=(segment+1)/(double)sections;
                for (int side=0;side<18;side++) {
                    double u=side/18.0,v=(side+1)/18.0;
                    PyraFire.quad(buffer,pose,role,
                        edge(frame,start,a*length,u,radius*size,a,time,shell),
                        edge(frame,start,a*length,v,radius*size,a,time,shell),
                        edge(frame,start,b*length,v,radius*size,b,time,shell),
                        edge(frame,start,b*length,u,radius*size,b,time,shell),
                        u,a,v,b,opacity,opacity);
                }
            }
        }
        // Turbulent tongues roll along and peel from the envelope, emphasizing the flamethrower character.
        for (int strand=0;strand<7;strand++) {
            for (int segment=0;segment<36;segment++) {
                double a=segment/36.0,b=(segment+1)/36.0;
                double angleA=strand*Math.TAU/7 + .20*Math.sin(a*8-time*.16+strand);
                double angleB=strand*Math.TAU/7 + .20*Math.sin(b*8-time*.16+strand);
                double ra=radius*(.55+.50*a+.12*Math.sin(a*22-time*.32+strand));
                double rb=radius*(.55+.50*b+.12*Math.sin(b*22-time*.32+strand));
                double width=.18*Math.sin(a*Math.PI)*(.72+.28*Math.sin(a*11-time*.24+strand));
                PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                    frame.point(start,a*length,ra,angleA-width),frame.point(start,a*length,ra,angleA+width),
                    frame.point(start,b*length,rb,angleB+width),frame.point(start,b*length,rb,angleB-width),
                    0,a,1,b,fade*.28,fade*.28);
            }
        }
        double muzzle=radius*(.7+.08*Math.sin(time*.8));
        sphere(buffer,pose,start,muzzle,time,PyraFire.CORE,fade*.7*Math.max(0,1-age/6));
        sphere(buffer,pose,end,radius*(.7+.18*Math.sin(time*.45)),time,PyraFire.WING,fade*.45);
    }

    private static Vec3 edge(AngelFlightFrame frame,Vec3 start,double axial,double around,
            double radius,double progress,double time,int shell) {
        double angle=around*Math.TAU+time*.025;
        double wave=shell==0?.08*Math.sin(axial*1.8-time*.7)+.035*Math.sin(angle*3+axial*2.6-time*.9)
            :.10*Math.sin(angle*5-axial*1.4+time*.38)
            +.06*Math.sin(angle*9+axial*2-time*.54);
        double r=radius*(.62+.38*Math.sqrt(progress))*(1+wave);
        return frame.point(start,axial,r,angle);
    }

    static void sphere(VertexConsumer buffer,PoseStack.Pose pose,Vec3 center,double radius,
            double time,int role,double alpha) {
        if (alpha<.001 || radius<.001) return;
        for (int latitude=0;latitude<12;latitude++) {
            double a=latitude/12.0,b=(latitude+1)/12.0;
            for (int longitude=0;longitude<24;longitude++) {
                double u=longitude/24.0,v=(longitude+1)/24.0;
                PyraFire.quad(buffer,pose,role,
                    globe(center,radius,u,a,time),globe(center,radius,v,a,time),
                    globe(center,radius,v,b,time),globe(center,radius,u,b,time),
                    u,1-a,v,1-b,alpha,alpha);
            }
        }
    }

    private static Vec3 globe(Vec3 center,double radius,double around,double up,double time) {
        double angle=around*Math.TAU+time*.04,polar=up*Math.PI;
        double wave=1+.055*Math.sin(angle*5+polar*7-time*.37)
            +.035*Math.sin(angle*9-polar*5+time*.52);
        double r=radius*wave;
        return center.add(Math.cos(angle)*Math.sin(polar)*r,Math.cos(polar)*r,
            Math.sin(angle)*Math.sin(polar)*r);
    }
}
