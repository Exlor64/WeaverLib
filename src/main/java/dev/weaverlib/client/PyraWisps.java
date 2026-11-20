package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/** Sparse procedural ribbons distributed from the body surface to one block away; no particle emitter. */
final class PyraWisps {
    private PyraWisps() {}

    static void render(VertexConsumer buffer, PoseStack.Pose pose, AbstractClientPlayer player,
            Vec3 camera, float partial) {
        double time=player.level().getGameTime()+partial;
        var body=AngelFlightPose.sample(player,partial);
        var frame=body.frame();
        Vec3 base=player.getPosition(partial).subtract(camera);
        double scale=player.getScale();
        long seed=player.getUUID().getLeastSignificantBits();
        for (int lane=0;lane<6;lane++) {
            double clock=time/52+lane/6.0;
            long cycle=(long)Math.floor(clock);
            double phase=clock-cycle;
            double id=(seed & 65535)+lane*79.7+cycle*117.3;
            double angle=hash(id)*Math.TAU;
            double offset=hash(id+7); // distance from the player, 0..1 block
            double height=(.35+hash(id+11)*1.05)*scale;
            double rise=body.blend()>.5?-.30:.30;
            double alpha=.34*Math.pow(Math.sin(phase*Math.PI),2);
            for (int segment=0;segment<9;segment++) {
                double a=segment/9.0,b=(segment+1)/9.0;
                double angleA=angle+.16*Math.sin(a*4+phase*3)+a*.18;
                double angleB=angle+.16*Math.sin(b*4+phase*3)+b*.18;
                double width=.028*Math.sin(a*Math.PI),widthB=.028*Math.sin(b*Math.PI);
                double distanceA=Math.min(1,offset+a*.10),distanceB=Math.min(1,offset+b*.10);
                PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                    point(frame,base,height+rise*a,angleA-width,distanceA,scale),
                    point(frame,base,height+rise*a,angleA+width,distanceA,scale),
                    point(frame,base,height+rise*b,angleB+widthB,distanceB,scale),
                    point(frame,base,height+rise*b,angleB-widthB,distanceB,scale),
                    0,a,1,b,alpha,alpha);
            }
        }
    }

    private static Vec3 point(AngelFlightFrame frame,Vec3 base,double height,double angle,double distance,double scale) {
        double surface=.30*scale/Math.max(Math.abs(Math.cos(angle)),Math.abs(Math.sin(angle)));
        return frame.point(base,height,surface+distance,angle);
    }
    private static double hash(double n) {
        double p=Math.sin(n*12.9898)*43758.5453;
        return p-Math.floor(p);
    }
}
