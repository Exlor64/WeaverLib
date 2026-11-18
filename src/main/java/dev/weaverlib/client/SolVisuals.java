package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.sol.SolCombat;
import dev.weaverlib.sol.SolFlash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class SolVisuals {
    private static final int SIDES = 16;
    private static final int AMBER = 0xF48A22;
    private static final int HONEY = 0xFFD36C;
    private static final int HOT = 0xFFFBE9;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private static final Frame HORIZONTAL = Frame.around(UP);
    private final List<Effect> effects = new ArrayList<>();

    void receive(SolFlash flash) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (effects.size() >= 192) {
            effects.removeFirst();
        }
        effects.add(new Effect(level, level.getGameTime(), flash, switch (flash.kind()) {
            case SKY -> 27;
            case HAND -> 17;
            case LANCE -> 7;
            case IMPACT -> 14;
        }));
    }

    void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            clear();
            return;
        }
        effects.removeIf(effect -> effect.level() != level
            || level.getGameTime() - effect.started() >= effect.lifetime());

        Vec3 camera = event.getCamera().getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var type = SolMesh.layer();
        var pose = event.getPoseStack().last();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isInvisible() || player.distanceToSqr(camera) > 48 * 48
                    || !SolCombat.view(player).handsTogether()) {
                continue;
            }
            Vec3 axis = player.getViewVector(partial);
            Vec3 hands = player.getEyePosition(partial).add(0, -.30, 0).add(axis.scale(.60)).subtract(camera);
            chargeOrb(buffers.getBuffer(type), pose, hands, Frame.around(axis), player.tickCount + partial);
        }
        if (!effects.isEmpty()) {
            var buffer = buffers.getBuffer(type);
            for (Effect effect : effects) {
                float age = Math.max(0, level.getGameTime() - effect.started() + partial);
                draw(buffer, pose, effect.flash(), camera, Mth.clamp(age / effect.lifetime(), 0, 1), age);
            }
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
        if (flash.kind() == SolFlash.Kind.IMPACT) {
            impact(buffer, pose, to, HORIZONTAL, progress, fade, .92);
        } else if (axis.lengthSqr() >= .01) {
            Frame frame = Frame.around(axis);
            switch (flash.kind()) {
                case SKY -> skyBeam(buffer, pose, from, to, frame, progress, age);
                case HAND -> handBeam(buffer, pose, from, to, frame, progress, age);
                case LANCE -> lance(buffer, pose, from, to, frame, fade, age);
                default -> throw new IllegalStateException("Unexpected Sol effect " + flash.kind());
            }
        }
    }

    private static void skyBeam(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, Frame frame,
            float progress, float age) {
        float strike = 1 - Mth.clamp(age / 13F, 0, 1);
        if (strike > 0) {
            double width = .23 + .63 * strike;
            tube(buffer, pose, from, to, frame, width * 3.05, width * 2.40, AMBER, strike * .17F, strike * .24F);
            tube(buffer, pose, from, to, frame, width * 1.50, width * 1.12, HONEY, strike * .52F, strike * .71F);
            tube(buffer, pose, from, to, frame, width * .74, width * .51, HOT, strike * .84F, strike);
            for (int i = 0; i < 3; i++) {
                helix(buffer, pose, to, to.lerp(from, .52), frame, width * (2.35 + i * .24),
                    12.6, age * -.34 + i * Math.TAU / 3, .085, i == 1 ? HOT : AMBER, strike * .76F);
            }
            Vec3 crown = to.add(0, .6 + 4.4 * progress, 0);
            band(buffer, pose, crown, HORIZONTAL, 2.40 - 1.25 * progress, .12, HOT, strike * .73F, true, age * .055);
        }
        impact(buffer, pose, to.add(0, .06, 0), HORIZONTAL, progress, 1 - progress, 2.20);
    }

    private static void handBeam(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, Frame frame,
            float progress, float age) {
        float fade = 1 - progress;
        double width = .22 + .74 * fade;
        tube(buffer, pose, from, to, frame, width * 1.67, width * 2.1, AMBER, fade * .30F, fade * .09F);
        tube(buffer, pose, from, to, frame, width, width * 1.32, HONEY, fade * .69F, fade * .44F);
        tube(buffer, pose, from, to, frame, width * .52, width * .70, HOT, fade, fade * .88F);
        for (int i = 0; i < 2; i++) {
            helix(buffer, pose, from, to, frame, width * (1.14 + i * .16),
                (i == 0 ? 1 : -1) * 19.0, age * .59 + i * Math.PI, .10, i == 0 ? HOT : AMBER, fade * .84F);
        }
        for (int i = 0; i < 3; i++) {
            double distance = .06 + i * .18 + progress * .59;
            if (distance < .98) {
                band(buffer, pose, from.lerp(to, distance), frame, width * (1.45 + i * .18),
                    .11, HONEY, fade * .69F, true, age * .26 + i * .4);
            }
        }
        band(buffer, pose, from.add(frame.axis().scale(.23)), frame, .32 + 1.55 * progress, .11,
            HOT, fade * .80F, false, 0);
        impact(buffer, pose, to, frame, progress, fade * .83F, .78);
    }

    private static void lance(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, Frame frame,
            float fade, float age) {
        Vec3 head = from.lerp(to, Math.min(1, age));
        if (age < 1) {
            Vec3 shaft = head.subtract(frame.axis().scale(1.85));
            Vec3 guard = head.subtract(frame.axis().scale(.65));
            tube(buffer, pose, shaft, guard, frame, .035, .12, HOT, 0, .96F);
            tube(buffer, pose, guard, head, frame, .25, 0, HONEY, .94F, .92F);
            tube(buffer, pose, guard, head, frame, .15, 0, HOT, .98F, .98F);
            band(buffer, pose, guard, frame, .36, .075, HOT, .85F, true, age * 1.8);
        }
        tube(buffer, pose, from, head, frame, .036, .095, AMBER, fade * .20F, fade * .44F);
        tube(buffer, pose, from, head, frame, .016, .041, HOT, fade * .26F, fade * .70F);
        for (int i = 0; i < 3; i++) {
            Vec3 glint = from.lerp(head, .18 + .29 * i)
                .add(frame.radial(i * 2.40 + age * .22).scale((.07 + .085 * age) * (i + 1) / 2));
            spark(buffer, pose, glint, frame.radial(i * 2.40), frame.axis(), .064, .18, HONEY, fade * .66F);
        }
    }

    private static void chargeOrb(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Frame frame, float time) {
        double pulse = .14 + Mth.sin(time * .38F) * .025;
        spark(buffer, pose, center, frame.across(), frame.vertical(), pulse, pulse, HOT, .86F);
        spark(buffer, pose, center, frame.across(), frame.vertical(), pulse * 2, pulse * 1.76, HONEY, .31F);
        band(buffer, pose, center, frame, pulse * 2.2, .04, HONEY, .76F, true, time * .23);
        band(buffer, pose, center, Frame.around(frame.across()), pulse * 1.65, .035, HOT, .67F, true, -time * .19);
        for (int i = 0; i < 4; i++) {
            Vec3 radial = frame.radial(time * .19 + i * Math.PI / 2);
            Vec3 glint = center.add(radial.scale(pulse * (2.0 + .46 * Math.sin(time * .31 + i))));
            spark(buffer, pose, glint, radial, frame.axis(), .039, .073, HOT, .78F);
        }
    }

    private static void impact(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Frame frame,
            float progress, float fade, double scale) {
        double radius = (.32 + 1.90 * (1 - fade * fade)) * scale;
        band(buffer, pose, center, frame, radius, (.045 + fade * .065) * scale,
            AMBER, fade * .70F, false, 0);
        band(buffer, pose, center.add(frame.axis().scale(.035)), frame, radius * .73, .065 * scale,
            HOT, fade * .83F, true, progress * .48);
        for (int i = 0; i < 12; i++) {
            double direction = i * Math.TAU / 12 + .12;
            Vec3 ray = frame.radial(direction);
            double speed = .7 + (i % 4) * .13;
            Vec3 glint = center.add(ray.scale(radius * speed))
                .add(frame.axis().scale(Math.sin(progress * Math.PI) * speed * scale * .62));
            spark(buffer, pose, glint, frame.radial(direction + Math.PI / 2), frame.axis(),
                .041 * scale, (.23 - .13 * progress) * scale, i % 3 == 0 ? HOT : HONEY, fade * .92F);
        }
    }

    private static void spark(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Vec3 across, Vec3 up,
            double width, double height, int color, float alpha) {
        var w = across.scale(width);
        var h = up.scale(height);
        SolMesh.quad(buffer, pose, color, alpha * .35F, alpha * .68F, alpha * .23F, alpha * .68F,
            center.add(h), center.add(w), center.subtract(h), center.subtract(w));
        SolMesh.quad(buffer, pose, color, alpha * .58F, alpha, alpha * .44F, alpha,
            center.add(h.scale(.63)), center.add(w.scale(.52)),
            center.subtract(h.scale(.63)), center.subtract(w.scale(.52)));
    }

    private static void helix(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to,
            Frame frame, double radius, double twist, double phase, double width, int color, float alpha) {
        int segments = 24;
        Vec3 previous = from.add(frame.radial(phase).scale(radius));
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 next = from.lerp(to, t).add(frame.radial(phase + twist * t).scale(radius * (1 + .25 * t)));
            Vec3 edge = frame.radial(phase + twist * t).scale(width);
            Vec3 across = frame.axis().scale(width * 1.5);
            SolMesh.quad(buffer, pose, color, 0, alpha, alpha, 0,
                previous.subtract(edge), previous, next, next.subtract(edge));
            SolMesh.quad(buffer, pose, color, alpha, 0, 0, alpha,
                previous, previous.add(across), next.add(across), next);
            previous = next;
        }
    }

    private static void band(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Frame frame,
            double radius, double width, int color, float alpha, boolean segmented, double phase) {
        for (int i = 0; i < SIDES; i++) {
            if (segmented && i % 4 == 3) {
                continue;
            }
            Vec3 a = frame.radial(phase + i * Math.TAU / SIDES);
            Vec3 b = frame.radial(phase + (i + 1) * Math.TAU / SIDES);
            SolMesh.quad(buffer, pose, color, alpha * .24F, alpha * .24F, alpha, alpha,
                center.add(a.scale(Math.max(0, radius - width))), center.add(b.scale(Math.max(0, radius - width))),
                center.add(b.scale(radius)), center.add(a.scale(radius)));
        }
    }

    private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, Frame frame,
            double startRadius, double endRadius, int color, float nearAlpha, float farAlpha) {
        Vec3 a = frame.across();
        for (int i = 1; i <= SIDES; i++) {
            Vec3 b = frame.radial(i * Math.TAU / SIDES);
            SolMesh.quad(buffer, pose, color, nearAlpha, nearAlpha, farAlpha, farAlpha,
                from.add(a.scale(startRadius)), from.add(b.scale(startRadius)),
                to.add(b.scale(endRadius)), to.add(a.scale(endRadius)));
            a = b;
        }
    }

    private record Frame(Vec3 axis, Vec3 across, Vec3 vertical) {
        static Frame around(Vec3 axis) {
            Vec3 across = axis.cross(Math.abs(axis.y) > .9 ? RIGHT : UP).normalize();
            return new Frame(axis, across, across.cross(axis));
        }

        Vec3 radial(double radians) {
            return across.scale(Math.cos(radians)).add(vertical.scale(Math.sin(radians)));
        }
    }

    private record Effect(ClientLevel level, long started, SolFlash flash, int lifetime) {}
}
