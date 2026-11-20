package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.Angels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class AngelVisuals {
    private final List<Flash> effects = new ArrayList<>();
    private ClientLevel world;

    void receive(AngelBurst burst) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (world != level) {
            effects.clear();
            world = level;
        }
        if (effects.size() >= 128) effects.removeFirst();
        effects.add(new Flash(burst, level.getGameTime(), burst.kind() == AngelBurst.FEATHER ? 3 : 22));
        if (burst.kind() == AngelBurst.AWAKEN || burst.kind() == AngelBurst.IMPACT
                || burst.kind() == AngelBurst.FEATHER_IMPACT || burst.kind() == AngelBurst.DIM) {
            int count = burst.kind() == AngelBurst.DIM ? 5 : 28;
            for (int i = 0; i < count; i++) {
                double angle = i * Math.TAU / count;
                level.addParticle(ParticleTypes.FLAME, burst.from().x, burst.from().y + .8, burst.from().z,
                    Math.cos(angle) * .12, .045 + level.random.nextDouble() * .08, Math.sin(angle) * .12);
            }
        }
    }

    void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        if (client.level == null) {
            effects.clear();
            world = null;
            return;
        }
        if (world != client.level) {
            effects.clear();
            world = client.level;
        }
        long tick = client.level.getGameTime();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        effects.removeIf(f -> tick - f.started() >= f.life());
        var pose = e.getPoseStack().last();
        var camera = e.getCamera().getPosition();
        var buffer = client.renderBuffers().bufferSource().getBuffer(AngelMesh.layer());
        for (var effect : effects) {
            float progress = Mth.clamp((tick - effect.started() + partial) / effect.life(), 0, 1);
            Vec3 center = effect.burst().from().subtract(camera);
            if (effect.burst().kind() == AngelBurst.FEATHER) {
                Vec3 end = effect.burst().to().subtract(camera);
                feather(buffer, pose, center, end, progress);
                continue;
            }
            float dim = effect.burst().kind() == AngelBurst.DIM ? .22F : 1;
            if (effect.burst().kind() != AngelBurst.FEATHER_IMPACT) {
                ring(buffer, pose, center.add(0, .09, 0), .4 + 3.4 * progress, (1 - progress) * dim);
            }
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.TAU / 8 + progress * 2;
                Vec3 at = center.add(Math.cos(angle) * (.35 + progress), progress * 2.4, Math.sin(angle) * (.35 + progress));
                flame(buffer, pose, at, .23, .7 * (1 - progress) + .1, (1-progress) * dim);
            }
        }
        for (var player : client.level.players()) {
            var view = Angels.view(player);
            if (!view.active() || player.isInvisible() || player.distanceToSqr(camera) > 64 * 64) continue;
            Vec3 center = player.getPosition(partial).subtract(camera);
            float time = player.tickCount + partial;
            if (view.defending() && Angels.flying(player) && !view.fast()) {
                for (int i = 0; i < 3; i++) {
                    double phase = (time * .075 + i / 3.0) % 1;
                    ring(buffer, pose, center.add(0, .15 + phase, 0),
                        2.1 * (.4 + phase), (float) ((1-phase) * .52));
                }
            }
            if (view.fast() && (view.defending() || view.spin() > tick)) {
                double remaining = view.spin() - tick - partial;
                long started = Angels.dashStarted(player);
                double roll = remaining > 0 && started >= 0
                    ? AngelFlightFrame.channelRoll(tick + partial - started) : 0;
                var frame = AngelFlightFrame.of(player.getViewYRot(partial), player.getViewXRot(partial), roll);
                // The renderer lays the body forward from its feet; its center is along that axis.
                Vec3 body = center.add(frame.forward().scale(.82 * player.getScale()));
                spiral(buffer, pose, frame, body, time, remaining > 0, player.getScale());
            }
        }
        client.renderBuffers().bufferSource().endBatch(AngelMesh.layer());
    }

    private static void feather(VertexConsumer buffer, PoseStack.Pose pose, Vec3 start, Vec3 end,
            float progress) {
        Vec3 forward = end.subtract(start).normalize();
        if (forward.lengthSqr() < .001) return;
        Vec3 head = start.lerp(end, Math.min(1, progress * 3));
        Vec3 towardCamera = head.scale(-1).normalize();
        // The primary feather face follows the camera; a crossed face preserves its volume in motion.
        Vec3 side = forward.cross(towardCamera);
        if (side.lengthSqr() < .001) side = forward.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < .001) side = forward.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        Vec3 other = forward.cross(side).normalize();
        float opacity = 1 - progress * .65F;
        Vec3 tail = head.subtract(forward.scale(1.18));
        Vec3 trail = head.subtract(forward.scale(2.3));
        Vec3 middle = tail.lerp(head, .44);
        AngelMesh.quad(buffer, pose, 0xFFB43D, 0, opacity * .20F, opacity * .20F, 0,
            trail.subtract(side.scale(.10)), middle.subtract(side.scale(.28)),
            middle.add(side.scale(.28)), trail.add(side.scale(.10)));
        AngelMesh.quad(buffer, pose, 0xFFF2B0, 0, opacity * .75F, opacity * .75F, 0,
            trail, head.subtract(side.scale(.035)), head.add(side.scale(.035)), trail);
        for (Vec3 across : new Vec3[]{side, other}) {
            Vec3 broad = across.scale(.22);
            // Soft gold silhouette, opaque white-gold vanes, and a white central shaft.
            AngelMesh.quad(buffer, pose, 0xFFB531, 0, opacity * .26F, 0, opacity * .26F,
                tail, middle.add(broad.scale(1.65)), head, middle.subtract(broad.scale(1.65)));
            for (int i = 0; i < 6; i++) {
                double a = i / 6.0, b = (i + 1) / 6.0;
                Vec3 p = tail.lerp(head, a), q = tail.lerp(head, b);
                double widthA = Math.sin(Math.PI * a), widthB = Math.sin(Math.PI * b);
                AngelMesh.quad(buffer, pose, 0xFFF1BC, opacity, opacity, opacity * .92F, opacity * .92F,
                    p.subtract(broad.scale(widthA)), p.add(broad.scale(widthA)),
                    q.add(broad.scale(widthB)), q.subtract(broad.scale(widthB)));
            }
            AngelMesh.quad(buffer, pose, 0xFFFFFF, opacity, opacity, opacity, opacity,
                tail.subtract(across.scale(.018)), tail.add(across.scale(.018)),
                head.add(across.scale(.012)), head.subtract(across.scale(.012)));
            for (int i = 1; i < 6; i++) {
                double t = i / 7.0;
                Vec3 root = tail.lerp(head, t);
                Vec3 edge = root.add(forward.scale(.10)).add(broad.scale(Math.sin(Math.PI * t)));
                AngelMesh.quad(buffer, pose, 0xFFD16A, opacity * .85F, opacity * .85F, 0, 0,
                    root, root.add(forward.scale(.016)), edge.add(forward.scale(.016)), edge);
            }
        }
        // A camera-facing head glint remains visible when a feather travels directly toward/away from us.
        Vec3 up = towardCamera.cross(side).normalize();
        Vec3 glint = side.scale(.095);
        AngelMesh.quad(buffer, pose, 0xFFFFFF, opacity * .30F, opacity, opacity * .30F, opacity,
            head.subtract(glint), head.add(up.scale(.14)), head.add(glint), head.subtract(up.scale(.14)));
    }

    private static void spiral(VertexConsumer buffer, PoseStack.Pose pose, AngelFlightFrame frame,
            Vec3 center, float time, boolean spinning, float scale) {
        double length = spinning ? 2.65 : 1.9;
        double phase = spinning ? 0 : -time * .20;
        int strands = spinning ? 3 : 4;
        for (int strand = 0; strand < strands; strand++) {
            double offset = strand * Math.TAU / strands + phase;
            for (int segment = 0; segment < 26; segment++) {
                double a = segment / 26.0, b = (segment + 1) / 26.0;
                double axialA = (.83 - a * length) * scale, axialB = (.83 - b * length) * scale;
                double angleA = offset + a * 7.7, angleB = offset + b * 7.7;
                double radiusA = (.35 + .19 * Math.sin(a * Math.PI) + .12 * a) * scale;
                double radiusB = (.35 + .19 * Math.sin(b * Math.PI) + .12 * b) * scale;
                double flicker = .86 + .14 * Math.sin(time * .61 + strand * 2 + a * 19);
                double widthA = Math.sin(a * Math.PI) * (.13 + .035 * flicker);
                double widthB = Math.sin(b * Math.PI) * (.13 + .035 * flicker);
                float alpha = (float) (Math.sin(a * Math.PI) * (spinning ? .88 : .67));
                // Soft ember edge, gold flame body and thin white-hot filament, all in the flight frame.
                ribbon(buffer, pose, frame, center, axialA, axialB, radiusA, radiusB,
                    angleA, angleB, widthA * 2.3, widthB * 2.3, 0xED6A16, alpha * .17F);
                ribbon(buffer, pose, frame, center, axialA, axialB, radiusA, radiusB,
                    angleA, angleB, widthA, widthB, 0xFFB83B, alpha);
                ribbon(buffer, pose, frame, center, axialA, axialB, radiusA - .012, radiusB - .012,
                    angleA, angleB, widthA * .26, widthB * .26, 0xFFF7D2, alpha * .94F);
            }
            // Curved flame tongues peel off behind the wings instead of rising vertically.
            for (int tongue = 0; tongue < 3; tongue++) {
                double progress = (time * .045 + tongue / 3.0 + strand * .13) % 1;
                double axial = (.35 - progress * 1.65) * scale;
                double angle = offset + progress * 7.7;
                Vec3 root = frame.point(center, axial, .51 * scale, angle);
                Vec3 tip = frame.point(center, axial - (.35 + progress * .3) * scale,
                    (.53 + progress * .25) * scale, angle - .25);
                Vec3 across = frame.right().scale(-Math.sin(angle) * .075 * scale)
                    .add(frame.up().scale(Math.cos(angle) * .075 * scale));
                Vec3 shoulder = root.lerp(tip, .30);
                float alpha = (float) ((1 - progress) * .78);
                AngelMesh.quad(buffer, pose, 0xFFB348, 0, alpha, 0, alpha,
                    root, shoulder.add(across), tip, shoulder.subtract(across));
            }
        }
    }

    private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, AngelFlightFrame frame, Vec3 center,
            double axialA, double axialB, double radiusA, double radiusB, double angleA, double angleB,
            double widthA, double widthB, int color, float alpha) {
        AngelMesh.quad(buffer, pose, color, alpha, alpha, alpha * .94F, alpha * .94F,
            frame.point(center, axialA, radiusA, angleA - widthA),
            frame.point(center, axialA, radiusA, angleA + widthA),
            frame.point(center, axialB, radiusB, angleB + widthB),
            frame.point(center, axialB, radiusB, angleB - widthB));
    }

    private static void ring(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, double radius, float alpha) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.TAU / 32;
            double b = (i + 1) * Math.TAU / 32;
            var p = new Vec3(Math.cos(a), 0, Math.sin(a));
            var q = new Vec3(Math.cos(b), 0, Math.sin(b));
            AngelMesh.quad(buffer, pose, 0xFFCF68, alpha * .12F, alpha * .12F, alpha, alpha,
                center.add(p.scale(radius - .10)), center.add(q.scale(radius - .10)),
                center.add(q.scale(radius)), center.add(p.scale(radius)));
        }
    }

    private static void flame(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, double width, double height, float alpha) {
        for (int plane = 0; plane < 2; plane++) {
            Vec3 side = plane == 0 ? new Vec3(width,0,0) : new Vec3(0,0,width);
            AngelMesh.quad(buffer, pose, 0xFF942A, alpha*.6F, alpha*.85F, 0, alpha*.85F,
                at.subtract(side), at.add(0,height*.35,0).add(side), at.add(0,height,0), at.add(0,height*.35,0).subtract(side));
            AngelMesh.quad(buffer, pose, 0xFFF1B4, alpha, alpha*.8F, 0, alpha*.8F,
                at, at.add(side.scale(.36)).add(0,height*.12,0), at.add(0,height*.65,0),
                at.subtract(side.scale(.36)).add(0,height*.12,0));
        }
    }

    private record Flash(AngelBurst burst, long started, int life) {}
}
