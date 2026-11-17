package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.sol.SolFlash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class SolVisuals {
    private static final int SIDES = 12;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private final List<Effect> effects = new ArrayList<>();

    void receive(SolFlash flash) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (effects.size() >= 256) {
                effects.removeFirst();
            }
            effects.add(new Effect(level, level.getGameTime(), flash, switch (flash.kind()) {
                case SKY -> 16;
                case HAND -> 10;
                case LANCE -> 3;
                case IMPACT -> 8;
            }));
        }
    }

    void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        effects.removeIf(effect -> effect.level() != level
            || level.getGameTime() - effect.started() >= effect.lifetime());
        if (effects.isEmpty()) {
            return;
        }

        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var type = SolMesh.layer();
        var buffer = buffers.getBuffer(type);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Effect effect : effects) {
            float age = Math.max(0, level.getGameTime() - effect.started() + partial);
            draw(buffer, poses.last(), effect.flash(), camera, age / effect.lifetime(), age);
        }
        buffers.endBatch(type);
    }

    void clear() {
        effects.clear();
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose pose, SolFlash flash,
            Vec3 camera, float progress, float age) {
        Vec3 from = flash.start().subtract(camera);
        Vec3 to = flash.end().subtract(camera);
        Vec3 axis = to.subtract(from).normalize();
        float fade = Mth.clamp(1 - progress, 0, 1);
        switch (flash.kind()) {
            case SKY -> {
                double width = 0.32 + fade * 0.48;
                surface(buffer, pose, from, to, axis, width * 2.3, width * 3.1, SolMesh.GOLD, fade * 0.28F);
                surface(buffer, pose, from, to, axis, width, width * 1.38, SolMesh.WHITE, fade * 0.86F);
                for (int i = 0; i < 3; i++) {
                    Vec3 ringCenter = to.add(0, 0.13 + i * 0.29 + progress * 2.1, 0);
                    ring(buffer, pose, ringCenter, UP, (0.65 + progress * 2.3) * (1 + i * .18),
                        0.08, SolMesh.GOLD, fade * (0.62F - i * 0.11F));
                }
            }
            case HAND -> {
                double width = 0.25 + fade * 0.54;
                surface(buffer, pose, from, to, axis, width * 1.4, width * 1.75, SolMesh.GOLD, fade * 0.40F);
                surface(buffer, pose, from, to, axis, width * 0.48, width * 0.72, SolMesh.WHITE, fade);
                for (int i = 0; i < 3; i++) {
                    ring(buffer, pose, from.lerp(to, Math.min(0.95, 0.08 + progress * 0.65 + i * 0.13)),
                        axis, width * (1.1 + 0.36 * i), 0.09, SolMesh.GOLD, fade * 0.65F);
                }
            }
            case LANCE -> {
                Vec3 head = from.lerp(to, Mth.clamp(age, 0, 1));
                Vec3 shaftStart = head.subtract(axis.scale(1.70));
                Vec3 shoulders = head.subtract(axis.scale(0.60));
                surface(buffer, pose, shaftStart, shoulders, axis, 0.018, 0.18, SolMesh.GOLD, fade * 0.65F);
                surface(buffer, pose, shoulders, head, axis, 0.18, 0.0, SolMesh.WHITE, fade * 0.94F);
            }
            case IMPACT -> {
                ring(buffer, pose, to, UP, 0.20 + progress * 1.20, 0.085, SolMesh.GOLD, fade * 0.84F);
                ring(buffer, pose, to, RIGHT, 0.10 + progress * 0.78, 0.065, SolMesh.WHITE, fade * 0.72F);
            }
        }
    }

    private static void ring(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Vec3 axis,
            double radius, double width, int color, float alpha) {
        surface(buffer, pose, center, center, axis, Math.max(0, radius - width), radius, color, alpha);
    }

    private static void surface(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, Vec3 axis,
            double startRadius, double endRadius, int color, float alpha) {
        if (axis.lengthSqr() < 0.01) {
            return;
        }
        Vec3 across = axis.cross(Math.abs(axis.y) > 0.9 ? RIGHT : UP).normalize();
        Vec3 vertical = across.cross(axis);
        for (int i = 0; i < SIDES; i++) {
            Vec3 a = radial(across, vertical, i);
            Vec3 b = radial(across, vertical, i + 1);
            SolMesh.quad(buffer, pose, color, alpha, alpha, alpha, alpha,
                from.add(a.scale(startRadius)), from.add(b.scale(startRadius)),
                to.add(b.scale(endRadius)), to.add(a.scale(endRadius)));
        }
    }

    private static Vec3 radial(Vec3 across, Vec3 vertical, int i) {
        double angle = i * Math.TAU / SIDES;
        return across.scale(Math.cos(angle)).add(vertical.scale(Math.sin(angle)));
    }

    private record Effect(ClientLevel level, long started, SolFlash flash, int lifetime) {}
}
