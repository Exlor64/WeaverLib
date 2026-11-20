package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Flame roots and vanes sample the same individual feathers as the physical wing. */
final class PyraWings {
    private PyraWings() {}

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints, double time) {
        geometry(buffer, pose, joints, time, 0);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints,
            double time, long pattern) {
        long accents = AngelFeathers.goldMask(pattern);
        double strike = Math.clamp(joints.strikeRoll() / Math.toRadians(80), 0, 1);
        for (var feather : AngelFeathers.ALL) {
            // Small coverts carry only occasional wisps; the feather layers stay readable.
            if (feather.covert() && feather.index() % 3 != 0) continue;
            double born = AngelFeathers.growth(joints, feather);
            if (born <= .001) continue;
            boolean gold = AngelFeathers.golden(accents, feather);
            var n = AngelFeathers.normal(joints, feather, .58);
            Vec3 normal = new Vec3(n.x(), n.y(), n.z());
            double phase = feather.index() * 2.399 + (pattern & 1023) * .013;
            double alpha = born * (feather.covert() ? .17 : .38);
            int material = gold ? PyraFire.GILDED_WING : PyraFire.WING;

            // A faint wash lies inside each vane's exact taper instead of bridging the gaps between feathers.
            for (int band = 0; band < 5; band++) {
                double a = .06 + band * .17, b = a + .17;
                double wa = AngelFeathers.width(joints, feather, a) * .60;
                double wb = AngelFeathers.width(joints, feather, b) * .60;
                PyraFire.quad(buffer,pose.last(),material,
                    surface(joints,feather,a,-wa,normal,.019),surface(joints,feather,a,wa,normal,.019),
                    surface(joints,feather,b,wb,normal,.019),surface(joints,feather,b,-wb,normal,.019),
                    0,a,1,b,alpha*.30,alpha*.30);
            }
            // The fire follows the quill, then peels into a small curl only near the real feather tip.
            double extra = feather.covert() ? .02 : .12 + .10 * strike;
            for (int segment = 0; segment < 10; segment++) {
                double a = segment / 10.0, b = (segment + 1) / 10.0;
                double ta = .12 + a * (1 + extra), tb = .12 + b * (1 + extra);
                double wa = feather.width() * .24 * Math.sin(a * Math.PI) * born;
                double wb = feather.width() * .24 * Math.sin(b * Math.PI) * born;
                double ca = AngelFeathers.width(joints,feather,ta) * .35 * Math.sin(phase);
                double cb = AngelFeathers.width(joints,feather,tb) * .35 * Math.sin(phase);
                double liftA = lift(feather,ta,phase,time), liftB = lift(feather,tb,phase,time);
                PyraFire.quad(buffer,pose.last(),gold ? PyraFire.GILDED_WING : PyraFire.TRAIL,
                    surface(joints,feather,ta,ca-wa,normal,liftA),surface(joints,feather,ta,ca+wa,normal,liftA),
                    surface(joints,feather,tb,cb+wb,normal,liftB),surface(joints,feather,tb,cb-wb,normal,liftB),
                    0,a,1,b,alpha,alpha);
            }
        }
    }

    private static double lift(AngelFeathers.Feather feather,double along,double phase,double time) {
        double peel = AngelWingMotion.smooth((along - .68) / .50);
        return .024 + peel * (feather.covert() ? .016 : .065)
            * (.65 + .35 * Math.sin(phase + along * 7 - time * .19));
    }

    private static Vec3 surface(AngelWingMotion.Joints joints,AngelFeathers.Feather feather,
            double along,double across,Vec3 normal,double lift) {
        var p = AngelFeathers.point(joints,feather,along,across);
        return new Vec3(p.x(),p.y(),p.z()).add(normal.scale(lift));
    }

    static Vec3 featherEdge(AngelWingMotion.Joints joints,int index,double along,double across) {
        var feather = AngelFeathers.ALL.get(index);
        var n = AngelFeathers.normal(joints,feather,along);
        return surface(joints,feather,along,AngelFeathers.width(joints,feather,along)*across,
            new Vec3(n.x(),n.y(),n.z()),.020);
    }

    /** Melee slashes follow the physical leading edge, without the old sheet's independent wobble. */
    static Vec3 point(AngelWingMotion.Joints joints,double span,double trail) {
        var p = joints.surface(span,trail);
        return new Vec3(p.x(),p.y(),p.z()-.012);
    }
}
