package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.Angels;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;

final class AngelWings extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Map<UUID, Motion> motions = new HashMap<>();
    private ClientLevel world;

    private static final class Motion {
        double lastTime, phase, flight, fast, effort = .7, guard, previousSpeed, vertical;
        Motion(double time) { lastTime = time; }
    }

    AngelWings(PlayerRenderer renderer) { super(renderer); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float speed, float partial, float age, float yaw, float pitch) {
        var view = Angels.view(player);
        if (!view.active() || !player.isAlive() || player.isInvisible()) {
            motions.remove(player.getUUID());
            return;
        }
        if (world != player.level()) {
            motions.clear();
            world = (ClientLevel) player.level();
        }
        double time = player.level().getGameTime() + partial;
        if (motions.size() > 128) motions.clear();
        Motion m = motions.computeIfAbsent(player.getUUID(), id -> new Motion(time));
        double elapsed = Math.clamp(time - m.lastTime, 0, 3);
        m.lastTime = time;
        double velocity = player.getDeltaMovement().length();
        double acceleration = elapsed > .001 ? (velocity - m.previousSpeed) / elapsed : 0;
        m.previousSpeed = velocity;
        boolean flying = Angels.flying(player);
        double effort = view.fast() ? Math.clamp(.18 + Math.max(0, -player.getXRot()) / 65
            + Math.max(0, 1.15 - velocity) * .65 + Math.max(0, acceleration) * 5, .08, 1) : .88;
        double blend = 1 - Math.exp(-elapsed / 4);
        m.effort += (effort - m.effort) * blend;
        m.vertical += (player.getDeltaMovement().y - m.vertical) * blend;
        m.flight += ((flying ? 1 : 0) - m.flight) * blend;
        m.fast += ((view.fast() ? 1 : 0) - m.fast) * blend;
        m.guard += ((view.defending() && !flying ? 1 : 0) - m.guard) * (1 - Math.exp(-elapsed / 2.2));
        // Integrating phase prevents animation jumps when speed or flight mode changes.
        double hoverRate = AngelWingMotion.hoverRate(m.vertical);
        double flightRate = hoverRate + m.fast * (.29 + m.effort * .13 - hoverRate);
        m.phase = (m.phase + elapsed * (.075 + m.flight * (flightRate - .075))) % Math.TAU;
        double attack = (time - view.swing()) / 12;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        var buffer = buffers.getBuffer(AngelMesh.layer());
        for (int pair = 0; pair < view.rank(); pair++) {
            for (int side = -1; side <= 1; side += 2) {
                double strike = view.wing() == pair * 2 + (side > 0 ? 1 : 0) ? attack : -1;
                var joints = AngelWingMotion.sample(m.phase, m.flight, m.fast, m.effort, m.guard, strike, pair);
                pose.pushPose();
                pose.scale(side, 1, 1);
                geometry(buffer, pose, joints);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints) {
        // Long primaries fan from the wrist; shorter overlapping rows fill the arm and cover the roots.
        for (int i = 0; i < 12; i++) {
            double u = .40 + i * .056;
            double length = .93 - i * .026;
            feather(buffer, pose.last(), joints, u, .04, .055, length, .055 + i * .004,
                i % 2 == 0 ? 0xFFF4C6 : 0xFFE3A2);
        }
        for (int i = 0; i < 11; i++) {
            double u = .025 + i * .052;
            feather(buffer, pose.last(), joints, u, .015, .048, .84 + u * .22, .025, 0xFFEAB0);
        }
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 13; i++) {
                double u = .018 + i * .075;
                feather(buffer, pose.last(), joints, u, -.035 + row * .11, .045,
                    .38 + row * .10, .012, row == 0 ? 0xFFF9DE : 0xF9D58D);
            }
        }
    }

    private static Vec3 point(AngelWingMotion.Joints joints, double u, double v, double ridge) {
        var p = joints.surface(u, v);
        return new Vec3(p.x(), p.y(), p.z() - ridge);
    }

    private static void feather(VertexConsumer buffer, PoseStack.Pose pose, AngelWingMotion.Joints joints,
            double u, double start, double width, double length, double lean, int color) {
        // Five tapered sections make each feather bend with the wing and give it a curved luminous edge.
        for (int band = 0; band < 5; band++) {
            double a = band / 5.0, b = (band + 1) / 5.0;
            double centerA = u + lean * a * a, centerB = u + lean * b * b;
            double wA = width * Math.sin(Math.PI * Math.pow(a, .65));
            double wB = width * Math.sin(Math.PI * Math.pow(b, .65));
            double vA = start + length * a, vB = start + length * b;
            Vec3 leftA = point(joints, centerA - wA, vA, .012);
            Vec3 rightA = point(joints, centerA + wA, vA, .012);
            Vec3 leftB = point(joints, centerB - wB, vB, .012);
            Vec3 rightB = point(joints, centerB + wB, vB, .012);
            AngelMesh.quad(buffer, pose, color, .92F, .92F, .84F, .84F, leftA, rightA, rightB, leftB);
            AngelMesh.quad(buffer, pose, 0xFFFBEA, .72F, .72F, .48F, .48F,
                point(joints, centerA - .004, vA, .025), point(joints, centerA + .004, vA, .025),
                point(joints, centerB + .003, vB, .025), point(joints, centerB - .003, vB, .025));
            // A narrow, fading gold fringe avoids the old solid diamond-shaped glow cards.
            AngelMesh.quad(buffer, pose, 0xFFB63E, .20F, 0, 0, .16F,
                leftA, point(joints, centerA - wA - .016, vA, .006),
                point(joints, centerB - wB - .012, vB, .006), leftB);
            AngelMesh.quad(buffer, pose, 0xFFB63E, .20F, 0, 0, .16F,
                rightA, point(joints, centerA + wA + .016, vA, .006),
                point(joints, centerB + wB + .012, vB, .006), rightB);
        }
    }
}
