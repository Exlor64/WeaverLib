package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.AngelBurst;
import net.minecraft.world.phys.Vec3;

/** Mesh-only ignition, impact and flight effects sharing the fire-wing material. */
final class PyraEffects {
    private PyraEffects() {}

    static void burst(VertexConsumer buffer, PoseStack.Pose pose, AngelBurst burst, Vec3 camera, float p) {
        Vec3 center = burst.from().subtract(camera);
        if (burst.effect() == AngelBurst.FEATHER) {
            Vec3 end = burst.to().subtract(camera);
            Vec3 direction = end.subtract(center).normalize();
            if (direction.lengthSqr() < .001) return;
            Vec3 head = center.lerp(end, Math.min(1, p * 3));
            Vec3 side = direction.cross(head.scale(-1).normalize());
            if (side.lengthSqr() < .001) side = direction.cross(new Vec3(0,1,0));
            if (side.lengthSqr() < .001) side = direction.cross(new Vec3(1,0,0));
            side = side.normalize().scale(.16);
            Vec3 tail = head.subtract(direction.scale(1.75));
            PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                head.subtract(side.scale(.22)),head.add(side.scale(.22)),tail.add(side),tail.subtract(side),
                0,0,1,1,1-p*.25,.1);
            return;
        }
        boolean dim = burst.effect() == AngelBurst.DIM;
        boolean impact = burst.effect() == AngelBurst.IMPACT || burst.effect() == AngelBurst.FEATHER_IMPACT;
        double radius = impact ? .12 + p * .42 : .30 + p * .12;
        double height = impact ? .3 + .8 * p : 1.9;
        double alpha = (1 - p) * (dim ? .23 : .70);
        // Narrow, twisting sheets climb around the body. No golden shockwave, billboards or particles.
        for (int strand = 0; strand < 7; strand++) {
            for (int segment = 0; segment < 10; segment++) {
                double a = segment / 10.0, b = (segment + 1) / 10.0;
                double aa = strand * Math.TAU / 7 + a * 1.6 - p * 2;
                double ab = strand * Math.TAU / 7 + b * 1.6 - p * 2;
                double width = .18 * Math.sin(a * Math.PI);
                PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                    point(center,radius,aa-width,a*height),point(center,radius,aa+width,a*height),
                    point(center,radius,ab+width,b*height),point(center,radius,ab-width,b*height),
                    0,a,1,b,alpha,alpha);
            }
        }
    }

    private static Vec3 point(Vec3 center, double radius, double angle, double height) {
        return center.add(Math.cos(angle)*radius,height,Math.sin(angle)*radius);
    }

    static void spiral(VertexConsumer buffer, PoseStack.Pose pose, AngelFlightFrame frame,
            Vec3 center, double time, boolean dash, float scale) {
        double length = dash ? 2.7 : 1.9;
        for (int strand = 0; strand < 4; strand++) {
            double phase = strand * Math.TAU / 4 - (dash ? 0 : time * .16);
            for (int segment = 0; segment < 28; segment++) {
                double a = segment / 28.0, b = (segment + 1) / 28.0;
                double axialA = (.85 - a * length) * scale, axialB = (.85 - b * length) * scale;
                double angleA = phase + a * 8, angleB = phase + b * 8;
                double radiusA = (.37 + .20 * Math.sin(a * Math.PI)) * scale;
                double radiusB = (.37 + .20 * Math.sin(b * Math.PI)) * scale;
                double widthA = .16 * Math.sin(a * Math.PI), widthB = .16 * Math.sin(b * Math.PI);
                PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                    frame.point(center,axialA,radiusA,angleA-widthA),frame.point(center,axialA,radiusA,angleA+widthA),
                    frame.point(center,axialB,radiusB,angleB+widthB),frame.point(center,axialB,radiusB,angleB-widthB),
                    0,a,1,b,.85,.85);
            }
        }
    }
}
