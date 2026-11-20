package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.Angels;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
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
        double lastTime, phase, flight, fast, effort = .7, guard, previousSpeed, vertical, dive, dash;
        long lastBirthParticle = Long.MIN_VALUE;
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
        m.fast = AngelFlightPose.sample(player, partial).blend();
        double diving = view.fast() ? AngelWingMotion.diveAmount(player.getViewXRot(partial), player.getDeltaMovement().y) : 0;
        m.dive += (diving - m.dive) * blend;
        m.dash += ((view.fast() && view.spin() > time ? 1 : 0) - m.dash) * (1 - Math.exp(-elapsed / 2.5));
        m.guard += ((view.defending() && !flying && !Angels.inferno(player) ? 1 : 0) - m.guard) * (1 - Math.exp(-elapsed / 2.2));
        // Integrating phase prevents animation jumps when speed or flight mode changes.
        double hoverRate = AngelWingMotion.hoverRate(m.vertical);
        double flightRate = hoverRate + m.fast * (.29 + m.effort * .13 - hoverRate);
        m.phase = (m.phase + elapsed * (.075 + m.flight * (flightRate - .075))) % Math.TAU;
        double attack = (time - view.swing()) / 12;
        var formation = Angels.formation(player);
        long tick = player.level().getGameTime();
        boolean emitBirth = m.lastBirthParticle != tick && tick % (formation.dim() ? 5 : 2) == 0;
        boolean fire = Angels.inferno(player);
        // Complete the feather pass before the fire pass. Both use the same pose and skeleton;
        // reacquiring a shared buffer mid-wing would flush it and break translucent ordering.
        for (int pass = 0; pass < (fire ? 2 : 1); pass++) {
            var buffer = buffers.getBuffer(pass == 0 ? AngelMesh.layer() : PyraFire.layer());
            if (pass == 1) {
                double strength = formation.started() < 0 ? 1
                    : AngelWingMotion.smooth((time - formation.started()) / 18);
                PyraAura.render(buffer, pose, getParentModel(), player, strength * (formation.dim() ? .65 : 1));
            }
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            for (int pair = 0; pair < view.rank(); pair++) {
                for (int side = -1; side <= 1; side += 2) {
                    double strike = view.wing() == pair * 2 + (side > 0 ? 1 : 0) ? attack : -1;
                    var normal = AngelWingMotion.sample(m.phase, m.flight, m.fast, m.effort, m.guard, strike, pair, side);
                    normal = AngelWingMotion.aerial(normal, m.dive, m.dash, pair, side);
                    double growth = formation.started() < 0 ? 1
                        : AngelWingMotion.birthProgress(time - formation.started(), pair);
                    var joints = AngelWingMotion.growing(normal, growth);
                    pose.pushPose();
                    pose.scale(side, 1, 1);
                    long pattern = AngelFeathers.seed(player.getUUID().getMostSignificantBits(),
                        player.getUUID().getLeastSignificantBits(), pair, side);
                    if (pass == 0) geometry(buffer, pose, joints, fire, pattern);
                    else {
                        PyraWings.geometry(buffer, pose, joints, time + pair * 11, pattern);
                        if (PyraWingFire.inWorldPass()) {
                            PyraWingFire.capture(player, pose, joints, pattern, pair, side, false);
                            PyraTrails.sample(player, pose, joints, time, pair, side);
                            if (!flying && strike >= 0) PyraSlashes.sample(player, pose, joints, time, strike);
                        }
                    }
                    if (!fire && growth < 1) {
                        birthGlow(buffer, pose, joints, growth, formation.dim());
                        if (emitBirth && growth > .02) birthParticles(pose, player, joints, growth);
                    }
                    pose.popPose();
                }
            }
            pose.popPose();
        }
        if (emitBirth) m.lastBirthParticle = tick;
    }

    private static void birthGlow(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints,
            double growth, boolean dim) {
        var p = joints.root();
        Vec3 root = new Vec3(p.x(), p.y(), p.z() + .035);
        float alpha = (float) ((dim ? .24 : .88) * (1 - AngelWingMotion.smooth((growth - .72) / .28)));
        double radius = .065 + Math.sin(growth * Math.PI) * .075;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.TAU / 8, b = (i + 1) * Math.TAU / 8;
            Vec3 edgeA = root.add(Math.cos(a) * radius, Math.sin(a) * radius, 0);
            Vec3 edgeB = root.add(Math.cos(b) * radius, Math.sin(b) * radius, 0);
            AngelMesh.quad(buffer, pose.last(), 0xFFCB65, alpha, 0, 0, alpha,
                root, edgeA, edgeB, root);
        }
        AngelMesh.quad(buffer, pose.last(), 0xFFF8D4, 0, alpha, 0, alpha,
            root.add(-radius * .36, 0, .002), root.add(0, -radius * .65, .002),
            root.add(radius * .36, 0, .002), root.add(0, radius * .65, .002));
    }

    private static void birthParticles(PoseStack pose, AbstractClientPlayer player,
            AngelWingMotion.Joints joints, double growth) {
        var client = Minecraft.getInstance();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        if (player.distanceToSqr(camera) > 64 * 64) return;
        var local = joints.surface(Math.clamp(growth, .04, .98), .08);
        var transformed = pose.last().pose().transformPosition((float) local.x(), (float) local.y(),
            (float) local.z(), new org.joml.Vector3f());
        Vec3 at = new Vec3(transformed.x, transformed.y, transformed.z).add(camera);
        var random = player.getRandom();
        player.level().addParticle(ParticleTypes.FLAME,
            at.x + (random.nextDouble() - .5) * .06, at.y, at.z + (random.nextDouble() - .5) * .06,
            (random.nextDouble() - .5) * .016, .012, (random.nextDouble() - .5) * .016);
        player.level().addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, .006, 0);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints) {
        geometry(buffer, pose, joints, false);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints, boolean pyra) {
        geometry(buffer, pose, joints, pyra, 0);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints,
            boolean pyra, long pattern) {
        long accents = pyra ? AngelFeathers.goldMask(pattern) : 0;
        for (var feather : AngelFeathers.ALL) {
            boolean gold = pyra && AngelFeathers.golden(accents, feather);
            feather(buffer, pose.last(), joints, feather,
                pyra ? AngelFeathers.pyraColor(feather, gold) : feather.normalColor(), pyra, gold);
        }
    }

    private static Vec3 featherPoint(AngelWingMotion.Joints joints, AngelFeathers.Feather feather,
            double along, double offset, double ridge) {
        var p = AngelFeathers.point(joints, feather, along, offset);
        return new Vec3(p.x(), p.y(), p.z() - ridge);
    }

    private static void feather(VertexConsumer buffer, PoseStack.Pose pose, AngelWingMotion.Joints joints,
            AngelFeathers.Feather feather, int color, boolean pyra, boolean gold) {
        double forming = AngelFeathers.growth(joints, feather);
        if (forming <= .001) return;
        float alpha = (float) Math.min(1, forming * 2);
        int quill = !pyra ? 0xFFFBEA : gold ? 0xFFF0B8 : 0xEC643F;
        int fringe = !pyra || gold ? 0xFFB63E : 0xDE4A2C;
        if (pyra) {
            color = AngelMesh.mix(0, color, .70F);
            quill = AngelMesh.mix(0, quill, .68F);
            fringe = AngelMesh.mix(0, fringe, .65F);
        }
        float fringeRoot = pyra ? .08F : .20F, fringeTip = pyra ? .06F : .16F;
        for (int band = 0; band < 5; band++) {
            double a = band / 5.0, b = (band + 1) / 5.0;
            double wA = AngelFeathers.width(joints, feather, a), wB = AngelFeathers.width(joints, feather, b);
            Vec3 leftA = featherPoint(joints, feather, a, -wA, .012);
            Vec3 rightA = featherPoint(joints, feather, a, wA, .012);
            Vec3 leftB = featherPoint(joints, feather, b, -wB, .012);
            Vec3 rightB = featherPoint(joints, feather, b, wB, .012);
            float body = (pyra ? .97F : .92F) * alpha, tip = (pyra ? .91F : .84F) * alpha;
            AngelMesh.quad(buffer, pose, color, body, body, tip, tip, leftA, rightA, rightB, leftB);
            AngelMesh.quad(buffer, pose, quill, .72F * alpha, .72F * alpha, .48F * alpha, .48F * alpha,
                featherPoint(joints, feather, a, -.004, .025), featherPoint(joints, feather, a, .004, .025),
                featherPoint(joints, feather, b, .003, .025), featherPoint(joints, feather, b, -.003, .025));
            AngelMesh.quad(buffer, pose, fringe, fringeRoot * alpha, 0, 0, fringeTip * alpha,
                leftA, featherPoint(joints, feather, a, -wA - .016, .006),
                featherPoint(joints, feather, b, -wB - .012, .006), leftB);
            AngelMesh.quad(buffer, pose, fringe, fringeRoot * alpha, 0, 0, fringeTip * alpha,
                rightA, featherPoint(joints, feather, a, wA + .016, .006),
                featherPoint(joints, feather, b, wB + .012, .006), rightB);
        }
    }
}
