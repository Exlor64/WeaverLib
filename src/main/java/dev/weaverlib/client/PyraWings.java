package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Continuous flame membranes with independently curling, tapered tongues at their trailing edges. */
final class PyraWings {
    private PyraWings() {}

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints, double time) {
        for (int shell = 0; shell < 2; shell++) {
            for (int span = 0; span < 26; span++) {
                double a = span / 26.0, b = (span + 1) / 26.0;
                double born = AngelWingMotion.featherGrowth((a + b) * .5, joints.growth());
                if (born < .001) continue;
                for (int band = 0; band < 8; band++) {
                    double v = band / 8.0, w = (band + 1) / 8.0;
                    double alpha = born * (shell == 0 ? .88 : .32)
                        * AngelWingMotion.smooth(a / .08) * AngelWingMotion.smooth((1 - b) / .08);
                    PyraFire.quad(buffer, pose.last(), PyraFire.WING,
                        point(joints, a, v * .83, time, shell), point(joints, b, v * .83, time, shell),
                        point(joints, b, w * .83, time, shell), point(joints, a, w * .83, time, shell),
                        a * 3, v * .83, b * 3, w * .83, alpha, alpha);
                }
            }
        }
        // The flame tips have genuine curved silhouettes, not square cards or a recolored feather texture.
        for (int i = 0; i < 19; i++) {
            double u = .045 + i * .049, seed = i * 2.399;
            double born = AngelWingMotion.featherGrowth(u, joints.growth());
            if (born < .001) continue;
            for (int band = 0; band < 7; band++) {
                double a = band / 7.0, b = (band + 1) / 7.0;
                double ca = u + Math.sin(seed + time * .085 + a * 3) * .024 * a;
                double cb = u + Math.sin(seed + time * .085 + b * 3) * .024 * b;
                double wa = .035 * (1 - a) * (1 - a * .4), wb = .035 * (1 - b) * (1 - b * .4);
                double length = .37 + .14 * Math.sin(seed + time * .13);
                double va = .57 + a * length, vb = .57 + b * length;
                PyraFire.quad(buffer, pose.last(), PyraFire.WING,
                    point(joints, ca - wa, va, time, 0), point(joints, ca + wa, va, time, 0),
                    point(joints, cb + wb, vb, time, 0), point(joints, cb - wb, vb, time, 0),
                    0, a * .85, 1, b * .85, born * .8 * (1 - a), born * .8 * (1 - b));
            }
        }
    }

    static Vec3 point(AngelWingMotion.Joints joints, double u, double v, double time, int shell) {
        double forming = AngelWingMotion.featherGrowth(u, joints.growth());
        double wave = Math.sin(u * 29 - time * .16) * Math.sin(u * 13 + time * .11);
        var p = joints.surface(u + wave * .009 * v * v,
            v * forming * (.94 + .06 * Math.sin(u * 37 + time * .12)));
        return new Vec3(p.x(), p.y(), p.z() + (shell == 0 ? -.012 : .018) * forming);
    }
}
