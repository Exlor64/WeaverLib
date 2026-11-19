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
        if (burst.kind() == AngelBurst.AWAKEN || burst.kind() == AngelBurst.IMPACT || burst.kind() == AngelBurst.DIM) {
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
                Vec3 forward = end.subtract(center).normalize();
                Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
                if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
                Vec3 head = center.lerp(end, Math.min(1, progress * 3));
                Vec3 tail = head.subtract(forward.scale(.85));
                Vec3 shoulder = tail.lerp(head, .35);
                AngelMesh.quad(buffer, pose, 0xFFF3BF, 0, 1-progress, 1-progress, 1-progress,
                    tail, shoulder.add(side.scale(.15)), head, shoulder.subtract(side.scale(.15)));
                continue;
            }
            float dim = effect.burst().kind() == AngelBurst.DIM ? .22F : 1;
            ring(buffer, pose, center.add(0, .09, 0), .4 + 3.4 * progress, (1 - progress) * dim);
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
            if (view.defending() && Angels.flying(player)) {
                float radius = view.fast() ? .75F : 2.1F;
                for (int i = 0; i < 3; i++) {
                    double phase = (time * .075 + i / 3.0) % 1;
                    ring(buffer, pose, center.add(0, .15 + phase, 0),
                        radius * (.4 + phase), (float) ((1-phase) * .52));
                }
            }
            if (view.fast() && (view.defending() || view.spin() > tick)) {
                for (int i = 0; i < 10; i++) {
                    double angle = time * .27 + i * Math.TAU / 10;
                    Vec3 at = center.add(Math.cos(angle) * .53, .3 + (i % 4) * .35, Math.sin(angle) * .53);
                    flame(buffer, pose, at, .14, .45 + Mth.sin(time * .3F + i) * .12, .74F);
                }
            }
        }
        client.renderBuffers().bufferSource().endBatch(AngelMesh.layer());
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
