package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.angel.PyraCombat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shader-driven flame volumes and crescents, using the same materials as the physical wing fire. */
final class PyraAerialVisual {
    private PyraAerialVisual() {}
    private static AngelFlightFrame frame(Vec3 delta) {
        Vec3 forward = delta.lengthSqr() < .00001 ? new Vec3(0, 0, 1) : delta.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < .001) right = forward.cross(new Vec3(1, 0, 0));
        right = right.normalize();
        return new AngelFlightFrame(forward, right, forward.cross(right).normalize());
    }

    static void charge(VertexConsumer out, PoseStack.Pose pose, Player player, Vec3 camera, float partial) {
        var charge = Angels.charge(player);
        if (!charge.active() || !charge.fireball()) return;
        double time = player.level().getGameTime() + partial;
        double merge = AngelWingMotion.smooth((time - charge.started() - 10) / 8);
        double radius = PyraCombat.fireballOrb(charge.energy()) * player.getScale();
        Vec3 center = Angels.pyraOrb(player, partial, charge.energy()).subtract(camera);
        ball(out, pose, center, radius, time, merge);
        // In first person the player's layer is hidden; tip sparks approach from the edge of view.
        var client = net.minecraft.client.Minecraft.getInstance();
        if (player == client.player && client.options.getCameraType().isFirstPerson() && merge < 1) {
            var f = frame(player.getViewVector(partial));
            for (int pair = 0; pair < Angels.view(player).rank(); pair++) {
                for (int side : new int[]{-1, 1}) {
                    double spread = (2.5 - merge * 2.45) * side * player.getScale();
                    Vec3 tip = center.add(f.right().scale(spread)).add(0, (.3 - pair * .32) * (1 - merge), 0);
                    ball(out, pose, tip, radius / Math.sqrt(Angels.view(player).rank() * 2), time + pair * 2,
                        1 - merge);
                }
            }
        }
    }

    static void render(VertexConsumer out, PoseStack.Pose pose, AngelBurst burst, Vec3 camera,
            double age, double life, double time) {
        int kind = burst.effect();
        if (kind == AngelBurst.PYRA_ARC || kind == AngelBurst.PYRA_ORB) {
            double t = Math.clamp(age, 0, 1);
            Vec3 center = burst.from().lerp(burst.to(), t).subtract(camera);
            var f = frame(burst.to().subtract(burst.from()));
            double fade = 1 - AngelWingMotion.smooth((age - 1) / 1.5);
            if (kind == AngelBurst.PYRA_ARC) {
                arc(out, pose, center, f, burst.power(), time, fade);
            } else {
                double radius = PyraCombat.fireballOrb(burst.power());
                ball(out, pose, center, radius, time, fade);
                for (int strand = 0; strand < 9; strand++) {
                    double angle = strand * Math.TAU / 9 + time * .045;
                    for (int step = 0; step < 16; step++) {
                        double a = step / 16.0, b = (step + 1) / 16.0;
                        Vec3 p = f.point(center, -a * (1.8 + radius * 2), radius * (1 - a * .6), angle + a * .6);
                        Vec3 q = f.point(center, -b * (1.8 + radius * 2), radius * (1 - b * .6), angle + b * .6);
                        Vec3 across = f.right().scale(.15 * radius * (1 - a));
                        PyraFire.quad(out, pose, PyraFire.TRAIL, p.subtract(across), p.add(across),
                            q.add(across), q.subtract(across), 0, a, 1, b, fade * .6 * (1 - a), fade * .6 * (1 - b));
                    }
                }
            }
            return;
        }
        double progress = Math.clamp(age / life, 0, 1);
        double reach = kind == AngelBurst.PYRA_DASH_BLAST ? 6 : PyraCombat.fireballRadius(burst.power());
        Vec3 center = burst.from().subtract(camera);
        double alpha = (1 - progress) * (1 - progress);
        double radius = reach * Math.sqrt(progress);
        PyraBeamVisual.sphere(out, pose, center, radius * .64 + .15, time, PyraFire.CORE, alpha * .75);
        PyraBeamVisual.sphere(out, pose, center, radius + .15, time, PyraFire.WING, alpha * .65);
        // Expanding, curling tongues peel upward rather than forming a misleading flat shockwave.
        for (int i = 0; i < 24; i++) {
            double az = i * 2.399963, elevation = -1 + 2 * (i + .5) / 24;
            Vec3 direction = new Vec3(Math.cos(az) * Math.sqrt(1 - elevation * elevation), elevation,
                Math.sin(az) * Math.sqrt(1 - elevation * elevation));
            Vec3 across = direction.cross(new Vec3(0, 1, .01)).normalize();
            for (int step = 0; step < 9; step++) {
                double a = step / 9.0, b = (step + 1) / 9.0;
                Vec3 p = center.add(direction.scale(radius * (.45 + a * .8)))
                    .add(0, progress * progress * reach * .45 + a * a * progress, 0)
                    .add(across.scale(Math.sin(a * 6 + time * .2 + i) * .16 * radius));
                Vec3 q = center.add(direction.scale(radius * (.45 + b * .8)))
                    .add(0, progress * progress * reach * .45 + b * b * progress, 0)
                    .add(across.scale(Math.sin(b * 6 + time * .2 + i) * .16 * radius));
                double wa = reach * .10 * (1 - a), wb = reach * .10 * (1 - b);
                PyraFire.quad(out, pose, PyraFire.WING, p.subtract(across.scale(wa)), p.add(across.scale(wa)),
                    q.add(across.scale(wb)), q.subtract(across.scale(wb)), 0, a, 1, b, alpha * .75, alpha * .65);
            }
        }
    }

    private static void ball(VertexConsumer out, PoseStack.Pose pose, Vec3 center, double radius, double time, double alpha) {
        PyraBeamVisual.sphere(out, pose, center, radius * .55, time, PyraFire.CORE, alpha * .92);
        PyraBeamVisual.sphere(out, pose, center, radius, time, PyraFire.WING, alpha * .76);
        PyraBeamVisual.sphere(out, pose, center, radius * 1.17, time + 5, PyraFire.TRAIL, alpha * .35);
    }

    private static void arc(VertexConsumer out, PoseStack.Pose pose, Vec3 center, AngelFlightFrame frame,
            double wing, double time, double alpha) {
        double sign = ((int)wing & 1) == 0 ? 1 : -1;
        for (int shell = 0; shell < 3; shell++) {
            int role = shell == 0 ? PyraFire.CORE : PyraFire.WING;
            double width = shell == 0 ? .055 : shell == 1 ? .23 : .45;
            for (int i = 0; i < 40; i++) {
                double a = i / 20.0 - 1, b = (i + 1) / 20.0 - 1;
                Vec3 p = crescent(center, frame, a, sign), q = crescent(center, frame, b, sign);
                double wa = width * Math.sqrt(Math.max(0, 1 - a * a));
                double wb = width * Math.sqrt(Math.max(0, 1 - b * b));
                for (Vec3 across : new Vec3[]{frame.up(), frame.forward()}) {
                    PyraFire.quad(out, pose, role, p.subtract(across.scale(wa)), p.add(across.scale(wa)),
                        q.add(across.scale(wb)), q.subtract(across.scale(wb)), 0, (a + 1) / 2, 1, (b + 1) / 2,
                        alpha * (shell == 0 ? .95 : .65), alpha * (shell == 0 ? .95 : .65));
                }
            }
        }
        for (int tongue = 0; tongue < 13; tongue++) {
            double t = tongue / 6.0 - 1;
            Vec3 start = crescent(center, frame, t, sign);
            for (int i = 0; i < 8; i++) {
                double a = i / 8.0, b = (i + 1) / 8.0;
                Vec3 p = start.subtract(frame.forward().scale(a * 1.6)).add(0, a * a * .45, 0)
                    .add(frame.right().scale(.10 * Math.sin(a * 8 - time * .4 + tongue)));
                Vec3 q = start.subtract(frame.forward().scale(b * 1.6)).add(0, b * b * .45, 0)
                    .add(frame.right().scale(.10 * Math.sin(b * 8 - time * .4 + tongue)));
                Vec3 across = frame.up().scale(.12 * Math.sqrt(Math.max(0, 1 - t * t)));
                PyraFire.quad(out, pose, PyraFire.TRAIL, p.subtract(across.scale(1 - a)), p.add(across.scale(1 - a)),
                    q.add(across.scale(1 - b)), q.subtract(across.scale(1 - b)), 0, a, 1, b,
                    alpha * .7 * (1 - a), alpha * .7 * (1 - b));
            }
        }
    }
    private static Vec3 crescent(Vec3 center, AngelFlightFrame frame, double t, double sign) {
        return center.add(frame.right().scale(2.4 * t)).add(frame.up().scale(sign * .75 * (1 - t * t)));
    }
}
