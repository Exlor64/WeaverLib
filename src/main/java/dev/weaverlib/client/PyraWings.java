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
                    PyraFire.wingQuad(buffer, pose.last(),
                        point(joints, a, v * .83, time, shell), point(joints, b, v * .83, time, shell),
                        point(joints, b, w * .83, time, shell), point(joints, a, w * .83, time, shell),
                        a, v * .83, b, w * .83, alpha, alpha);
                }
            }
        }
        // Long, narrow streams run from the roots along the articulated wing and curl beyond the outer tips.
        for (int lane = 0; lane < 9; lane++) {
            double trail = .045 + lane * .085;
            for (int segment = 0; segment < 20; segment++) {
                double a = segment / 20.0, b = (segment + 1) / 20.0;
                double ua = .08 + a * 1.02, ub = .08 + b * 1.02;
                double wa = .018 * Math.sin(a * Math.PI), wb = .018 * Math.sin(b * Math.PI);
                double ca = trail + .035 * Math.sin(a * 8 - time * .14 + lane) * a;
                double cb = trail + .035 * Math.sin(b * 8 - time * .14 + lane) * b;
                double born = AngelWingMotion.featherGrowth((ua + ub) * .5, joints.growth());
                if (born < .001) continue;
                // quad's V follows the span, so both geometry and material flow shoulder-to-tip.
                PyraFire.quad(buffer,pose.last(),PyraFire.TRAIL,
                    point(joints,ua,ca-wa,time,0),point(joints,ua,ca+wa,time,0),
                    point(joints,ub,cb+wb,time,0),point(joints,ub,cb-wb,time,0),
                    0,a,1,b,born*.65,born*.65);
            }
        }
    }

    static Vec3 point(AngelWingMotion.Joints joints, double u, double v, double time, int shell) {
        double forming = AngelWingMotion.featherGrowth(u, joints.growth());
        double wave = Math.sin(u * 29 - time * .16) * Math.sin(u * 13 + time * .11);
        double strike = Math.clamp(joints.strikeRoll() / Math.toRadians(80), 0, 1);
        double stretch = strike * AngelWingMotion.smooth((u - .45) / .55);
        var p = joints.surface(u + wave * .009 * v * v + .055 * stretch,
            v * (1 + .18 * strike) * forming * (.94 + .06 * Math.sin(u * 37 + time * .12)));
        return new Vec3(p.x(), p.y(), p.z() + (shell == 0 ? -.012 : .018) * forming);
    }
}
